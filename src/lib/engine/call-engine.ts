/**
 * ORANGE SHIELD - ENGINE 01 · CALL GUARD
 *
 * SmartCall Engine: local, evidence-based call screening.
 * Flow (per product spec §12):
 *   1. Normalize number
 *   2. Priority protections (emergency / contacts / allowlist) -> ALLOW
 *   3. Explicit blocks (blacklist, confirmed fraud)         -> BLOCK
 *   4. Risk scoring (prefixes, reputation, complaints, ...)
 *   5. Decision by thresholds (allow / warn / silence / block)
 *   6. Record decision + evidences
 *
 * Scoring reference (spec §3):
 *   Personal blacklist            100
 *   Reported as scam              100
 *   Prefix 0303                   +90
 *   Telemarketing classification  +90
 *   Robocall association          +95
 *   Consistent complaints         +60
 *   Mass-burst detection          +50
 *   Verified business             -40
 *   Known contact                 priority release
 */

import { db } from "@/lib/db";
import { isInternational, normalizePhone } from "./normalize";
import { analyzeSpoofing } from "./spoof";
import {
  clampScore,
  riskBand,
  type CallAnalysis,
  type EngineEvidence,
  type EngineSettings,
  type EngineStep,
} from "./types";

/** Weight table (tunable parameters). */
export const CALL_WEIGHTS = {
  PREFIX_0303: 90,
  TELEMARKETING: 90,
  ROBOCALL: 95,
  COLLECTOR: 45,
  FRAUD: 100,
  COMPLAINT_BASE: 15,
  COMPLAINT_STEP: 15,
  COMPLAINT_MAX: 60,
  MASS_BURST: 50,
  VERIFIED_BUSINESS: -40,
  INTERNATIONAL: 100,
  UNKNOWN_NUMBER: 100,
  INSISTENCE_LIGHT: 30,
  INSISTENCE_HEAVY: 50,
  SHORTCODE: 25,
} as const;
/** Insistence thresholds: calls within the last 60 minutes. */
const INSISTENCE_LIGHT_COUNT = 3;
const INSISTENCE_HEAVY_COUNT = 6;

export async function analyzeIncomingCall(
  rawNumber: string,
  settings: EngineSettings
): Promise<CallAnalysis> {
  const e164 = normalizePhone(rawNumber);
  const steps: EngineStep[] = [];
  const evidences: EngineEvidence[] = [];
  let score = 0;
  let category = "unknown";
  let decision: CallAnalysis["decision"] = "ALLOW";

  steps.push({ step: "Normalizar número", result: e164 || "inválido" });

  // Protection master switch: engine idle -> everything passes.
  if (!settings.protectionActive) {
    steps.push({ step: "Motor de proteção", result: "Desativado — chamada liberada" });
    return {
      e164,
      decision: "ALLOW",
      riskScore: 0,
      category: "unknown",
      evidences: [],
      steps,
      reason: "Proteção desativada pelo usuário",
      ruleVersion: settings.ruleVersion,
    };
  }

  const international = isInternational(e164);

  // ------------------------------------------------------------------
  // 1. Priority protections — never blocked (spec §10.1)
  // ------------------------------------------------------------------
  const allowed = await db.allowedNumber.findUnique({ where: { e164 } });
  if (allowed) {
    steps.push({
      step: "Lista de autorizados",
      result: `Liberado${allowed.name ? ` (${allowed.name})` : ""} — prioridade máxima`,
    });
    return {
      e164,
      decision: "ALLOW",
      riskScore: 0,
      category: "authorized",
      evidences: [],
      steps,
      reason: "Número autorizado na lista de permissões",
      ruleVersion: settings.ruleVersion,
    };
  }
  steps.push({ step: "Lista de autorizados", result: "Não está autorizado" });

  // ------------------------------------------------------------------
  // 2. Explicit blocks
  // ------------------------------------------------------------------
  const blocked = await db.blockedNumber.findUnique({ where: { e164 } });
  if (blocked) {
    steps.push({ step: "Lista pessoal de bloqueio", result: "Presente na lista negra" });
    return {
      e164,
      decision: "BLOCK",
      riskScore: 100,
      category: "blacklisted",
      evidences: [{ label: "Número presente na lista pessoal de bloqueio", points: 100 }],
      steps,
      reason: blocked.label ?? "Número bloqueado manualmente",
      ruleVersion: settings.ruleVersion,
    };
  }
  steps.push({ step: "Lista pessoal de bloqueio", result: "Sem bloqueio manual" });

  // ------------------------------------------------------------------
  // 3. Local reputation base
  // ------------------------------------------------------------------
  const phoneRecord = await db.phoneNumber.findUnique({
    where: { e164 },
    include: { reputation: true },
  });
  const reputation = phoneRecord?.reputation;

  if (reputation?.isConfirmedFraud) {
    steps.push({ step: "Base de reputação", result: "Golpe confirmado — bloqueio direto" });
    return {
      e164,
      decision: "BLOCK",
      riskScore: 100,
      category: "fraud",
      evidences: [{ label: "Número denunciado como golpe (confirmado)", points: 100 }],
      steps,
      reason: "Golpe confirmado na base de reputação",
      ruleVersion: settings.ruleVersion,
    };
  }
  steps.push({
    step: "Base de reputação",
    result: reputation
      ? `Classificação: ${reputation.classification} (risco ${reputation.riskScore})`
      : "Número desconhecido",
  });

  // ------------------------------------------------------------------
  // 4. Risk scoring
  // ------------------------------------------------------------------
  const add = (label: string, points: number) => {
    if (!points) return;
    evidences.push({ label, points });
    score += points;
  };

  // ------------------------------------------------------------------
  // 4. Spoof Detector — caller ID manipulation (Engine 04)
  // ------------------------------------------------------------------
  const spoofFlags: { code: string; label: string; points: number }[] = [];
  if (settings.spoofProtection) {
    const spoof = await analyzeSpoofing(e164, settings.ownNumber);
    const flagged = spoof.checks.filter((c) => c.status === "flagged");
    spoofFlags.push(...flagged.map((f) => ({ code: f.code, label: f.label, points: f.points })));
    if (flagged.length > 0) {
      for (const f of flagged) add(f.label, f.points);
      if (category === "unknown" && spoof.spoofScore >= 25) category = "spoofing";
      steps.push({
        step: "Detector de spoofing",
        result: `${flagged.length} flag(s) — score de spoofing ${spoof.spoofScore}/100 (${spoof.verdictLabel})`,
      });
    } else {
      steps.push({
        step: "Detector de spoofing",
        result: spoof.checks.some((c) => c.status === "info")
          ? "Checagem parcial — caller ID sem indícios de manipulação"
          : "Caller ID íntegro — todas as checagens passaram",
      });
    }
  }

  // Anatel 0303 telemarketing prefix
  if (e164.startsWith("+550303")) {
    if (settings.blockTelemarketing) {
      add("Prefixo 0303 — telemarketing (Anatel)", CALL_WEIGHTS.PREFIX_0303);
      category = "telemarketing";
    } else {
      steps.push({ step: "Prefixo 0303", result: "Detectado, mas regra desativada" });
    }
  }

  // Reputation classification
  if (reputation) {
    switch (reputation.classification) {
      case "telemarketing":
        if (settings.blockTelemarketing) {
          add("Classificado como telemarketing", CALL_WEIGHTS.TELEMARKETING);
          category = "telemarketing";
        }
        break;
      case "robocall":
        if (settings.blockRobocalls) {
          add("Associado a robocall (disparo automático)", CALL_WEIGHTS.ROBOCALL);
          category = "robocall";
        }
        break;
      case "collector":
        if (settings.blockCollectors) {
          add("Cobrança automatizada", CALL_WEIGHTS.COLLECTOR);
          category = "collector";
        }
        break;
      case "verified_business":
        add("Número comercial verificado", CALL_WEIGHTS.VERIFIED_BUSINESS);
        category = "verified_business";
        break;
      default:
        break;
    }

    // Consistent complaints (scaled by confidence, capped at 60)
    if (reputation.reportCount > 0) {
      const complaint = Math.min(
        CALL_WEIGHTS.COMPLAINT_MAX,
        CALL_WEIGHTS.COMPLAINT_BASE +
          (reputation.reportCount - 1) * CALL_WEIGHTS.COMPLAINT_STEP
      );
      const weighted = Math.round(complaint * (reputation.confidence || 0.5));
      add(
        `${reputation.reportCount} denúncia(s) coletiva(s) (confiança ${Math.round(
          reputation.confidence * 100
        )}%)`,
        weighted
      );
      if (!category || category === "unknown") category = reputation.classification;
    }
  }

  // International
  if (international) {
    if (settings.blockInternational) {
      add("Chamada internacional bloqueável", CALL_WEIGHTS.INTERNATIONAL);
      category = "international";
    } else {
      steps.push({ step: "Chamada internacional", result: "Detectada (regra desativada)" });
    }
  }

  // Operator shortcode
  const shortcode = /^\+55\d{1,6}$/.test(e164);
  if (shortcode) {
    add("Remetente/curto comercial (centrais automáticas)", CALL_WEIGHTS.SHORTCODE);
  }

  // Insistence protection — repeated calls in the last hour
  if (settings.insistenceProtection) {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    const recent = await db.callEvent.count({
      where: { e164, occurredAt: { gte: oneHourAgo } },
    });
    if (recent >= INSISTENCE_HEAVY_COUNT) {
      add(`Insistência: ${recent} chamadas na última hora`, CALL_WEIGHTS.INSISTENCE_HEAVY);
    } else if (recent >= INSISTENCE_LIGHT_COUNT) {
      add(`Insistência: ${recent} chamadas na última hora`, CALL_WEIGHTS.INSISTENCE_LIGHT);
    }
  }

  // Unknown-number policy
  const unknown = !reputation && !phoneRecord;
  if (unknown && settings.blockUnknownNumbers) {
    add("Política: bloquear números desconhecidos", CALL_WEIGHTS.UNKNOWN_NUMBER);
    category = "unknown";
  }

  score = clampScore(score);
  steps.push({
    step: "Pontuação de risco",
    result: `${score}/100 — ${riskBand(score)}`,
  });

  // ------------------------------------------------------------------
  // 5. Decision
  // ------------------------------------------------------------------
  if (score >= settings.blockThreshold) {
    decision = "BLOCK";
  } else if (settings.silenceMode && score < settings.blockThreshold && unknown) {
    decision = "SILENCE";
  } else if (score >= settings.warnThreshold) {
    decision = "WARN";
  } else {
    decision = "ALLOW";
  }

  steps.push({
    step: "Decisão do SmartCall Engine",
    result:
      decision === "BLOCK"
        ? "BLOQUEAR"
        : decision === "SILENCE"
          ? "SILENCIAR"
          : decision === "WARN"
            ? "IDENTIFICAR / ALERTAR"
            : "PERMITIR",
  });

  const reason =
    decision === "BLOCK"
      ? `Risco alto (${score}/100): ${evidences.map((e) => e.label).slice(0, 2).join("; ") || "política do usuário"}`
      : decision === "WARN"
        ? `Suspeito (${score}/100): exibir identificação na tela`
        : decision === "SILENCE"
          ? "Modo silêncio total: apenas contatos autorizados tocam"
          : "Sem indícios de risco — chamada legítima";

  // ------------------------------------------------------------------
  // 6. Record (audit trail)
  // ------------------------------------------------------------------
  await recordCallEvent(e164, phoneRecord?.id ?? null, decision, score, category, evidences, reason, spoofFlags, settings);

  return { e164, decision, riskScore: score, category, evidences, steps, reason, ruleVersion: settings.ruleVersion };
}

async function recordCallEvent(
  e164: string,
  phoneNumberId: string | null,
  decision: CallAnalysis["decision"],
  riskScore: number,
  category: string,
  evidences: EngineEvidence[],
  reason: string,
  spoofFlags: { code: string; label: string; points: number }[],
  settings: EngineSettings
) {
  await db.callEvent.create({
    data: {
      e164,
      phoneNumberId,
      decision,
      riskScore,
      category: category || null,
      evidences: JSON.stringify(evidences),
      reason,
      spoofFlags: JSON.stringify(spoofFlags),
      ruleVersion: settings.ruleVersion,
    },
  });
}
