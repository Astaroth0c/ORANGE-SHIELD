/**
 * ORANGE SHIELD - ENGINE 02 · SMS GUARD
 *
 * SMS classification engine (per product spec §4):
 *   1. Sender analysis (number, shortcode, alphanumeric ID)
 *   2. Content analysis (keywords, patterns, links)
 *   3. Frequency analysis (mass bursts)
 *   4. Classification (advertising | billing | authentication | service | fraud)
 *   5. Preferences (operator rules, user toggles)
 *
 * Safety rule: authentication messages (OTP, bank codes) are NEVER discarded
 * for merely containing links or commercial words (spec §6.2).
 */

import { db } from "@/lib/db";
import { isShortcode, normalizePhone } from "./normalize";
import {
  clampScore,
  riskBand,
  type EngineEvidence,
  type EngineSettings,
  type EngineStep,
  type SmsAnalysis,
  type SmsDecision,
} from "./types";

export const SMS_WEIGHTS = {
  OPERATOR_AD: 60,
  PROMO_KEYWORD: 20,
  FRAUD_KEYWORD: 30,
  SUSPICIOUS_LINK: 25,
  SHORTCODE: 20,
  BURST: 25,
  AUTH_BONUS: -100,
} as const;

const PROMO_KEYWORDS = [
  "oferta", "ofertas", "aproveite", "promoção", "promocao", "desconto",
  "exclusivo", "contrate", "assinatura", "pacote", "franquia", "ilimitado",
  "responda sim", "responda ja", "cancele", "ganhe", "pontos", "cashback",
  "dobro", "grátis", "gratis", "assine", "plano novo", "migração",
];

const FRAUD_KEYWORDS = [
  "pix bloqueado", "pix suspenso", "conta bloqueada", "cartão bloqueado",
  "cartao bloqueado", "renove o cadastro", "atualize o cadastro",
  "empréstimo aprovado", "emprestimo aprovado", "prêmio", "premio",
  "você ganhou", "voce ganhou", " sorteio", "confirmar dados",
  "clicar no link", "urgente", "última chance", "sua conta será suspensa",
];

const AUTH_KEYWORDS = [
  "código", "codigo", "código de verificação", "codigo de verificacao",
  "token", "otp", "autenticação", "autenticacao", "2fa", "verificação em duas etapas",
  "seu código é", "seu codigo e", "não compartilhe", "nao compartilhe",
];

const SUSPICIOUS_LINK_RE = /(bit\.ly|tinyurl|cutt\.ly|shorturl|\.xyz|\.top|\.club|http:\/\/)/i;

function detectCategory(content: string): {
  category: SmsAnalysis["category"];
  matched: string[];
} {
  const lower = content.toLowerCase();
  const matched: string[] = [];

  const authHit = AUTH_KEYWORDS.some((k) => lower.includes(k));
  const fraudHits = FRAUD_KEYWORDS.filter((k) => lower.includes(k));
  const promoHits = PROMO_KEYWORDS.filter((k) => lower.includes(k));

  matched.push(...fraudHits, ...promoHits);

  let category: SmsAnalysis["category"] = "unknown";
  if (authHit) category = "authentication";
  else if (fraudHits.length >= 2 || (fraudHits.length >= 1 && SUSPICIOUS_LINK_RE.test(content)))
    category = "fraud";
  else if (promoHits.length >= 1) category = "advertising";
  else if (/(fatura|boleto|vencimento|cobrança|cobranca|pagamento)/.test(lower))
    category = "billing";
  else if (/(agendamento|confirmação|confirmacao|entrega|atendimento|lembrete)/.test(lower))
    category = "service";

  return { category, matched };
}

export async function analyzeIncomingSms(
  rawSender: string,
  content: string,
  settings: EngineSettings
): Promise<SmsAnalysis> {
  const sender = normalizePhone(rawSender);
  const steps: EngineStep[] = [];
  const evidences: EngineEvidence[] = [];
  let score = 0;
  let decision: SmsDecision = "ALLOW";
  let reason = "";

  steps.push({ step: "Normalizar remetente", result: sender || "inválido" });

  const { category, matched } = detectCategory(content);
  steps.push({
    step: "Classificação de conteúdo",
    result:
      category === "unknown"
        ? "Sem padrão comercial/fraudulento claro"
        : `Categoria: ${category}${matched.length ? ` (termos: ${matched.slice(0, 3).join(", ")})` : ""}`,
  });

  // ------------------------------------------------------------------
  // 1. Priority: authentication messages are protected (spec §6.2)
  // ------------------------------------------------------------------
  if (category === "authentication") {
    steps.push({ step: "Proteção prioritária", result: "Mensagem de autenticação — preservada" });
    await recordSms(sender, null, content, "authentication", "ALLOW", 0, [], "Autenticação/código — alta prioridade");
    return {
      sender,
      decision: "ALLOW",
      riskScore: 0,
      category: "authentication",
      evidences: [],
      steps,
      reason: "Mensagem de autenticação (código/token) — preservada",
    };
  }

  // ------------------------------------------------------------------
  // 2. Allowlist sender
  // ------------------------------------------------------------------
  const allowed = await db.allowedNumber.findUnique({ where: { e164: sender } });
  if (allowed) {
    steps.push({ step: "Lista de autorizados", result: "Remetente liberado" });
    await recordSms(sender, null, content, "personal", "ALLOW", 0, [], "Remetente autorizado");
    return {
      sender,
      decision: "ALLOW",
      riskScore: 0,
      category: "personal",
      evidences: [],
      steps,
      reason: `Remetente autorizado${allowed.name ? ` (${allowed.name})` : ""}`,
    };
  }

  // ------------------------------------------------------------------
  // 3. Operator sender rules
  // ------------------------------------------------------------------
  const phoneRecord = await db.phoneNumber.findUnique({
    where: { e164: sender },
    include: { reputation: true },
  });

  const operatorRule = await db.operatorRule.findFirst({
    where: { sender, active: true },
  });

  const add = (label: string, points: number) => {
    if (!points) return;
    evidences.push({ label, points });
    score += points;
  };

  if (operatorRule) {
    steps.push({
      step: "Regras de operadora",
      result: `${operatorRule.operator} · ${operatorRule.sender} → ação configurada: ${operatorRule.action}`,
    });
    if (operatorRule.action === "block") {
      add(`Remetente ${operatorRule.sender} (${operatorRule.operator}) marcado como bloqueio`, SMS_WEIGHTS.OPERATOR_AD);
    } else if (operatorRule.action === "quarantine") {
      add(`Remetente ${operatorRule.sender} (${operatorRule.operator}) → quarentena`, SMS_WEIGHTS.OPERATOR_AD - 20);
    } else {
      add(`Remetente ${operatorRule.sender} (${operatorRule.operator}) autorizado`, -60);
    }
  }

  // ------------------------------------------------------------------
  // 4. Content scoring
  // ------------------------------------------------------------------
  if (category === "fraud") {
    add("Indicadores de fraude/phishing no conteúdo", SMS_WEIGHTS.FRAUD_KEYWORD * 2);
  } else if (category === "advertising") {
    if (settings.blockOperatorAds) {
      add("Linguagem promocional/publicitária", SMS_WEIGHTS.PROMO_KEYWORD * Math.min(3, matched.length || 1));
    } else {
      steps.push({ step: "Filtro comercial", result: "Publicidade permitida pela configuração" });
    }
  }

  if (SUSPICIOUS_LINK_RE.test(content)) {
    add("Link suspeito/encurtado detectado", SMS_WEIGHTS.SUSPICIOUS_LINK);
  }

  if (isShortcode(sender)) {
    add("Remetente curto comercial (disparo em massa)", SMS_WEIGHTS.SHORTCODE);
  }

  // Frequency: same sender within the last hour
  const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
  const recent = await db.smsMessage.count({
    where: { sender, occurredAt: { gte: oneHourAgo } },
  });
  if (recent >= 3) {
    add(`Disparo repetido: ${recent} mensagens na última hora`, SMS_WEIGHTS.BURST);
  }

  // Reputation
  if (phoneRecord?.reputation?.isConfirmedFraud) {
    add("Remetente com golpe confirmado na base", 100);
  }

  score = clampScore(score);
  steps.push({ step: "Pontuação de risco", result: `${score}/100 — ${riskBand(score)}` });

  // ------------------------------------------------------------------
  // 5. Decision (fraud always quarantines/blocks; billing/service are careful)
  // ------------------------------------------------------------------
  if (category === "fraud" && score >= 50) {
    decision = "BLOCK";
  } else if (score >= settings.blockThreshold) {
    decision = "BLOCK";
  } else if (score >= settings.warnThreshold) {
    decision = "QUARANTINE";
  } else {
    decision = "ALLOW";
  }

  steps.push({
    step: "Decisão do SMS Guard",
    result:
      decision === "BLOCK" ? "BLOQUEAR" : decision === "QUARANTINE" ? "QUARENTENA" : "PERMITIR",
  });

  reason =
    decision === "BLOCK"
      ? `Risco alto (${score}/100): ${evidences[0]?.label ?? "conteúdo indesejado"}`
      : decision === "QUARANTINE"
        ? `Suspeita (${score}/100): movida para quarentena recuperável`
        : "Sem indícios de risco — mensagem entregue";

  await recordSms(sender, phoneRecord?.id ?? null, content, category, decision, score, evidences, reason);

  return { sender, decision, riskScore: score, category, evidences, steps, reason };
}

async function recordSms(
  sender: string,
  phoneNumberId: string | null,
  content: string,
  category: string,
  decision: SmsDecision,
  riskScore: number,
  evidences: EngineEvidence[],
  reason: string
) {
  await db.smsMessage.create({
    data: {
      sender,
      phoneNumberId,
      content,
      category,
      decision,
      riskScore,
      evidences: JSON.stringify(evidences),
      reason,
    },
  });
}
