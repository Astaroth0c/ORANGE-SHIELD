/**
 * ORANGE SHIELD - ENGINE 03 · WHATSAPP GUARD
 *
 * WhatsApp message blocking logic (per user request).
 * Real-world plug points on Android: NotificationListenerService (read
 * WhatsApp notifications) + Accessibility/Contact checks; the decision
 * engine below is transport-agnostic and mirrors the SMS Guard flow:
 *
 *   1. Normalize sender
 *   2. Priority protections (allowlist / contacts)          -> ALLOW
 *   3. Explicit blocks (blacklist, confirmed fraud)          -> BLOCK
 *   4. Caller-ID spoofing checks (Spoof Detector integration)
 *   5. Content analysis (WhatsApp-specific scam patterns)
 *   6. Frequency analysis (mass bursts)
 *   7. Decision by thresholds (allow / quarantine / block)
 *   8. Record decision + evidences
 *
 * WhatsApp scam catalog covered:
 *   - Phishing do código de 6 dígitos (account hijack)
 *   - Falso parente "troquei de número" + PIX
 *   - Falso delivery/motoboy com link de rastreamento
 *   - Empréstimo/renda extra/investimento (golpe financeiro)
 *   - Prêmio/sorteio/converta pontos
 *   - Disparos "encaminhada muitas vezes"
 *   - Convites de grupo de desconhecidos
 *   - Links suspeitos (encurtadores e TLDs de risco)
 */

import { db } from "@/lib/db";
import { isShortcode, normalizePhone } from "./normalize";
import { analyzeSpoofing, type SpoofCheck } from "./spoof";
import {
  clampScore,
  riskBand,
  type EngineEvidence,
  type EngineSettings,
  type EngineStep,
} from "./types";

export type WhatsAppDecision = "ALLOW" | "QUARANTINE" | "BLOCK";

export interface WhatsAppAnalysis {
  sender: string;
  decision: WhatsAppDecision;
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
  spoofChecks: SpoofCheck[];
}

export const WA_WEIGHTS = {
  CODE_PHISH: 80,
  SCAM_KEYWORD: 25,
  SCAM_KEYWORD_CAP: 50,
  SUSPICIOUS_LINK: 30,
  UNKNOWN_LINK: 15,
  FORWARD_MASS: 25,
  GROUP_INVITE: 35,
  BURST: 20,
  UNKNOWN_SENDER: 10,
  POLICY_UNKNOWN: 70,
  VERIFIED_BUSINESS: -30,
  SPOOF_TOTAL: 0.6, // fraction of the spoof score injected as evidence
} as const;

/** Financial/social-engineering scam phrases typical on WhatsApp (BR). */
const WA_SCAM_KEYWORDS = [
  "pix", "meu pix", "faça um pix", "faca um pix", "fazer um pix",
  "empréstimo aprovado", "emprestimo aprovado", "empréstimo pré", "consignado",
  "renda extra", "investimento", "cripto", "dobrar o dinheiro",
  "prêmio", "premio", "você ganhou", "voce ganhou", "você foi selecionado",
  "sorteio", "resgate",
  "troquei de número", "novo número", "meu número novo",
  "pedido retido", "taxa de entrega", "rastreamento", "motoboy", "correios",
  "boleto vencido", "multa", " urgent", "urgente",
  "cvv", "dados do cartão", "dados do cartao",
  "vaga de emprego", "currículo", "curriculo",
];

const WA_PROMO_KEYWORDS = [
  "oferta", "desconto", "promoção", "promocao", "cupom", "cashback",
  "aproveite", "black friday", "frete grátis", "frete gratis",
];

const WA_CODE_PHISH_RE =
  /(me\s+(envie|manda|mande|manda|passe|informe|digite)[^.;!?]{0,30}(c[óo]digo|code)|(c[óo]digo|code)[^.;!?]{0,40}(whatsapp|telegram|errado|engano)|a[íi] est[áa] o c[óo]digo|meu c[óo]digo [ée])/i;

const SUSPICIOUS_LINK_RE =
  /(bit\.ly|tinyurl|cutt\.ly|shorturl|is\.gd|rb\.gy|\.xyz|\.top|\.club|\.icu|\.buzz|\.info|\.click|\.website|http:\/\/)/i;
const ANY_LINK_RE = /(https?:\/\/|www\.)[^\s]+/i;

/** Extracts the first URL from the message (for evidence text). */
function extractLink(content: string): string | null {
  const m = content.match(/(https?:\/\/|www\.)[^\s]+/i);
  return m ? m[0].slice(0, 48) : null;
}

export async function analyzeIncomingWhatsApp(
  rawSender: string,
  content: string,
  settings: EngineSettings,
  opts: { forwardedMany?: boolean; isGroupInvite?: boolean } = {}
): Promise<WhatsAppAnalysis> {
  const sender = normalizePhone(rawSender);
  const steps: EngineStep[] = [];
  const evidences: EngineEvidence[] = [];
  const spoofChecks: SpoofCheck[] = [];
  let score = 0;
  let category = "unknown";
  let decision: WhatsAppDecision = "ALLOW";

  steps.push({ step: "Normalizar remetente", result: sender || "inválido" });

  const forwardedMany = !!opts.forwardedMany;
  const isGroupInvite = !!opts.isGroupInvite;
  const lower = content.toLowerCase();
  const add = (label: string, points: number) => {
    if (!points) return;
    evidences.push({ label, points });
    score += points;
  };

  // Master switch
  if (!settings.protectionActive || !settings.waProtect) {
    steps.push({
      step: "WhatsApp Guard",
      result: settings.protectionActive ? "WhatsApp Guard desativado — mensagem liberada" : "Motor desativado — mensagem liberada",
    });
    return {
      sender,
      decision: "ALLOW",
      riskScore: 0,
      category: "unknown",
      evidences: [],
      steps,
      reason: "Proteção WhatsApp desativada pelo usuário",
      spoofChecks,
    };
  }

  // ------------------------------------------------------------------
  // 1. Priority protections
  // ------------------------------------------------------------------
  const allowed = await db.allowedNumber.findUnique({ where: { e164: sender } });
  if (allowed) {
    steps.push({ step: "Contatos autorizados", result: `Remetente liberado${allowed.name ? ` (${allowed.name})` : ""}` });
    await recordWa(sender, null, content, "personal", "ALLOW", 0, [], forwardedMany, isGroupInvite, "Remetente autorizado (lista de contatos)");
    return {
      sender,
      decision: "ALLOW",
      riskScore: 0,
      category: "personal",
      evidences: [],
      steps,
      reason: `Remetente autorizado${allowed.name ? ` (${allowed.name})` : ""}`,
      spoofChecks,
    };
  }
  steps.push({ step: "Contatos autorizados", result: "Remetente fora da lista de autorizados" });

  // ------------------------------------------------------------------
  // 2. Explicit blocks
  // ------------------------------------------------------------------
  const blocked = await db.blockedNumber.findUnique({ where: { e164: sender } });
  if (blocked) {
    steps.push({ step: "Lista pessoal de bloqueio", result: "Remetente bloqueado manualmente" });
    await recordWa(
      sender, null, content, "blacklisted", "BLOCK", 100,
      [{ label: "Remetente presente na lista pessoal de bloqueio", points: 100 }],
      forwardedMany, isGroupInvite, blocked.label ?? "Remetente bloqueado manualmente"
    );
    return {
      sender,
      decision: "BLOCK",
      riskScore: 100,
      category: "blacklisted",
      evidences: [{ label: "Remetente presente na lista pessoal de bloqueio", points: 100 }],
      steps,
      reason: blocked.label ?? "Remetente bloqueado manualmente",
      spoofChecks,
    };
  }

  const phoneRecord = await db.phoneNumber.findUnique({
    where: { e164: sender },
    include: { reputation: true },
  });

  if (phoneRecord?.reputation?.isConfirmedFraud) {
    steps.push({ step: "Base de reputação", result: "Golpe confirmado — bloqueio direto" });
    await recordWa(
      sender, phoneRecord.id, content, "fraud", "BLOCK", 100,
      [{ label: "Remetente com golpe confirmado na base coletiva", points: 100 }],
      forwardedMany, isGroupInvite, "Golpe confirmado na base de reputação"
    );
    return {
      sender,
      decision: "BLOCK",
      riskScore: 100,
      category: "fraud",
      evidences: [{ label: "Remetente com golpe confirmado na base coletiva", points: 100 }],
      steps,
      reason: "Golpe confirmado na base de reputação",
      spoofChecks,
    };
  }
  steps.push({
    step: "Base de reputação",
    result: phoneRecord?.reputation
      ? `Classificação: ${phoneRecord.reputation.classification} (risco ${phoneRecord.reputation.riskScore})`
      : "Remetente desconhecido na base",
  });

  // ------------------------------------------------------------------
  // 3. Spoof Detector integration (caller ID manipulation)
  // ------------------------------------------------------------------
  if (settings.spoofProtection && !isShortcode(sender)) {
    const spoof = await analyzeSpoofing(sender, settings.ownNumber);
    spoofChecks.push(...spoof.checks);
    const flagged = spoof.checks.filter((c) => c.status === "flagged");
    if (flagged.length > 0) {
      const injected = Math.round(spoof.spoofScore * WA_WEIGHTS.SPOOF_TOTAL);
      add(`Spoofing no caller ID (${spoof.verdictLabel.toLowerCase()})`, injected);
      if ((category === "unknown" || category === "spoofing") && spoof.spoofScore >= 25) {
        category = "spoofing";
      } else if (category === "spoofing" && spoof.spoofScore < 25) {
        category = "unknown";
      }
      steps.push({
        step: "Detector de spoofing",
        result: `${flagged.length} flag(s): ${flagged.map((f) => f.label).join("; ")}`,
      });
    } else {
      steps.push({ step: "Detector de spoofing", result: "Caller ID sem indícios de manipulação" });
    }
  }

  // ------------------------------------------------------------------
  // 4. Content analysis (WhatsApp-specific)
  // ------------------------------------------------------------------
  const link = extractLink(content);
  const hasLink = !!link;
  const suspiciousLink = hasLink && SUSPICIOUS_LINK_RE.test(content);

  // 4a. Phishing do código de 6 dígitos (sequestro de conta)
  const codePhish = WA_CODE_PHISH_RE.test(content) && /\d{4,8}/.test(content);
  if (codePhish) {
    if (settings.waBlockCodePhish) {
      add("Phishing de código de verificação (sequestro de conta)", WA_WEIGHTS.CODE_PHISH);
      category = "phishing_code";
    } else {
      steps.push({ step: "Phishing de código", result: "Detectado, mas regra desativada" });
    }
  }

  // 4b. Scam keywords
  const scamHits = WA_SCAM_KEYWORDS.filter((k) => lower.includes(k));
  if (scamHits.length > 0 && category !== "phishing_code") {
    const pts = Math.min(WA_WEIGHTS.SCAM_KEYWORD_CAP, WA_WEIGHTS.SCAM_KEYWORD * scamHits.length);
    add(`Padrões de golpe no texto (${scamHits.slice(0, 3).join(", ")})`, pts);
    if (category === "unknown" || category === "spoofing") category = "fraud";
  }
  // 4c. Links
  if (hasLink) {
    if (suspiciousLink) {
      add(`Link suspeito detectado (${link})`, WA_WEIGHTS.SUSPICIOUS_LINK);
      if (category === "unknown" || category === "spoofing") category = "fraud";
    } else if (settings.waBlockLinks && !phoneRecord) {
      add(`Link de remetente desconhecido (${link})`, WA_WEIGHTS.UNKNOWN_LINK);
    }
  }

  // 4d. Forwarded-many-times mass messages
  if (forwardedMany) {
    if (settings.waBlockForwarded) {
      add("Encaminhada muitas vezes (disparo em cadeia)", WA_WEIGHTS.FORWARD_MASS);
      if (category === "unknown" || category === "spoofing") category = "advertising";
    } else {
      steps.push({ step: "Disparo encaminhado", result: "Detectado, mas regra desativada" });
    }
  }

  // 4e. Group invite from unknown
  if (isGroupInvite) {
    if (settings.waBlockGroupInvite) {
      add("Convite de grupo de remetente desconhecido", WA_WEIGHTS.GROUP_INVITE);
      if (category === "unknown" || category === "spoofing") category = "group_invite";
    } else {
      steps.push({ step: "Convite de grupo", result: "Detectado, mas regra desativada" });
    }
  }

  // 4f. Unknown-sender baseline
  if (!phoneRecord) {
    add("Primeira mensagem de remetente fora dos contatos", WA_WEIGHTS.UNKNOWN_SENDER);
    if (settings.waBlockUnknown) {
      add("Política: bloquear remetentes fora dos contatos", WA_WEIGHTS.POLICY_UNKNOWN);
    }
  }

  // 4g. Verified business discount
  if (phoneRecord?.verified) {
    add("Empresa verificada no WhatsApp Business", WA_WEIGHTS.VERIFIED_BUSINESS);
  }

  // ------------------------------------------------------------------
  // 5. Frequency analysis (mass bursts)
  // ------------------------------------------------------------------
  const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
  const recent = await db.whatsAppMessage.count({
    where: { sender, occurredAt: { gte: oneHourAgo } },
  });
  if (recent >= 3) {
    add(`Disparo repetido: ${recent} mensagens na última hora`, WA_WEIGHTS.BURST);
  }

  score = clampScore(score);
  steps.push({ step: "Pontuação de risco", result: `${score}/100 — ${riskBand(score)}` });

  // ------------------------------------------------------------------
  // 6. Decision
  // ------------------------------------------------------------------
  if ((category === "phishing_code" || category === "fraud") && score >= 50) {
    decision = "BLOCK";
  } else if (score >= settings.blockThreshold) {
    decision = "BLOCK";
  } else if (score >= settings.warnThreshold) {
    decision = "QUARANTINE";
  } else {
    decision = "ALLOW";
  }

  steps.push({
    step: "Decisão do WhatsApp Guard",
    result:
      decision === "BLOCK" ? "BLOQUEAR" : decision === "QUARANTINE" ? "QUARENTENA" : "PERMITIR",
  });

  const reason =
    decision === "BLOCK"
      ? `Risco alto (${score}/100): ${evidences[0]?.label ?? "conteúdo malicioso"}`
      : decision === "QUARANTINE"
        ? `Suspeita (${score}/100): movida para quarentena recuperável`
        : "Sem indícios de risco — mensagem entregue";

  await recordWa(sender, phoneRecord?.id ?? null, content, category, decision, score, evidences, forwardedMany, isGroupInvite, reason);

  return { sender, decision, riskScore: score, category, evidences, steps, reason, spoofChecks };
}

async function recordWa(
  sender: string,
  phoneNumberId: string | null,
  content: string,
  category: string,
  decision: WhatsAppDecision,
  riskScore: number,
  evidences: EngineEvidence[],
  forwardedMany: boolean,
  isGroupInvite: boolean,
  reason: string
) {
  await db.whatsAppMessage.create({
    data: {
      sender,
      phoneNumberId,
      content,
      category,
      decision,
      riskScore,
      forwardedMany,
      hasLink: /(https?:\/\/|www\.)[^\s]+/i.test(content),
      isGroupInvite,
      evidences: JSON.stringify(evidences),
      reason,
    },
  });
}
