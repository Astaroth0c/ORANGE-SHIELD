/**
 * ORANGE SHIELD - Seed script
 * Populates: settings, reputation DB, spam patterns, operator rules,
 * allow/block lists and demo history for the dashboard.
 *
 * Run: bun run prisma/seed.ts
 */

import { PrismaClient } from "@prisma/client";

const db = new PrismaClient();

async function main() {
  console.log("Seeding ORANGE SHIELD database...");

  // ----------------------------------------------------------------
  // Settings
  // ----------------------------------------------------------------
  await db.userSettings.upsert({
    where: { id: "default" },
    update: {},
    create: { id: "default" },
  });

  // ----------------------------------------------------------------
  // Allowlist (contacts + emergency + important services)
  // ----------------------------------------------------------------
  const allowlist = [
    { e164: "+5511987654321", name: "Mãe", kind: "contact" },
    { e164: "+5511976543210", name: "Trabalho — Carlos", kind: "contact" },
    { e164: "+5521965432109", name: "Escola das crianças", kind: "contact" },
    { e164: "+5511933337777", name: "Hospital Santa Casa", kind: "business" },
    { e164: "+55119222", name: "Emergência 192 (SAMU)", kind: "emergency" },
    { e164: "+5511930331234", name: "Banco Itaú — atendimento", kind: "business" },
  ];
  for (const item of allowlist) {
    await db.allowedNumber.upsert({ where: { e164: item.e164 }, update: {}, create: item });
  }

  // ----------------------------------------------------------------
  // Blacklist (manual)
  // ----------------------------------------------------------------
  const blacklist = [
    { e164: "+551140028922", label: "Call center insistente — cartões" },
    { e164: "+553130151234", label: "Telemarketing de consórcio" },
  ];
  for (const item of blacklist) {
    await db.blockedNumber.upsert({ where: { e164: item.e164 }, update: {}, create: item });
  }

  // ----------------------------------------------------------------
  // Phone numbers + reputation (collective intelligence base)
  // ----------------------------------------------------------------
  const numbers = [
    // Telemarketing 0303 (Anatel)
    { e164: "+55030311100111", type: "shared", operator: "Vivo", classification: "telemarketing", risk: 95, reports: 214, confidence: 0.97, verified: false },
    { e164: "+55030311100222", type: "shared", operator: "TIM", classification: "telemarketing", risk: 92, reports: 156, confidence: 0.95, verified: false },
    { e164: "+551130345678", type: "landline", operator: "Oi", classification: "telemarketing", risk: 85, reports: 88, confidence: 0.9, verified: false },
    // Robocalls
    { e164: "+5511999912345", type: "mobile", operator: "Claro", classification: "robocall", risk: 93, reports: 120, confidence: 0.93, verified: false },
    { e164: "+5521987650000", type: "mobile", operator: "Vivo", classification: "robocall", risk: 90, reports: 76, confidence: 0.9, verified: false },
    // Fraud (confirmed)
    { e164: "+5511912340001", type: "mobile", operator: "Desconhecida", classification: "fraud", risk: 100, reports: 340, confidence: 0.99, verified: false },
    { e164: "+5511912340002", type: "mobile", operator: "Desconhecida", classification: "fraud", risk: 100, reports: 289, confidence: 0.98, verified: false },
    { e164: "+5585991112233", type: "mobile", operator: "Oi", classification: "fraud", risk: 100, reports: 95, confidence: 0.94, verified: false },
    // Collectors
    { e164: "+5511944445555", type: "mobile", operator: "TIM", classification: "collector", risk: 55, reports: 41, confidence: 0.8, verified: false },
    // Verified businesses
    { e164: "+5511930331234", type: "landline", operator: "Vivo", classification: "verified_business", risk: 5, reports: 0, confidence: 1, verified: true },
    { e164: "+551138581111", type: "landline", operator: "Vivo", classification: "verified_business", risk: 5, reports: 1, confidence: 0.99, verified: true },
    { e164: "+551155551234", type: "landline", operator: "TIM", classification: "verified_business", risk: 10, reports: 2, confidence: 0.95, verified: true },
    // Unknown legit numbers (no reputation)
    { e164: "+5511991234567", type: "mobile", operator: "Claro", classification: "unknown", risk: 0, reports: 0, confidence: 0, verified: false },
  ];

  for (const n of numbers) {
    await db.phoneNumber.upsert({
      where: { e164: n.e164 },
      update: {
        numberType: n.type,
        operator: n.operator,
        verified: n.verified,
        reputation: {
          upsert: {
            update: {
              riskScore: n.risk,
              classification: n.classification,
              reportCount: n.reports,
              confidence: n.confidence,
              isConfirmedFraud: n.classification === "fraud",
              lastAnalyzedAt: new Date(),
            },
            create: {
              riskScore: n.risk,
              classification: n.classification,
              reportCount: n.reports,
              confidence: n.confidence,
              isConfirmedFraud: n.classification === "fraud",
              lastAnalyzedAt: new Date(),
            },
          },
        },
      },
      create: {
        e164: n.e164,
        numberType: n.type,
        operator: n.operator,
        verified: n.verified,
        reputation: {
          create: {
            riskScore: n.risk,
            classification: n.classification,
            reportCount: n.reports,
            confidence: n.confidence,
            isConfirmedFraud: n.classification === "fraud",
            lastAnalyzedAt: new Date(),
          },
        },
      },
    });
  }

  // ----------------------------------------------------------------
  // Spam patterns (prefixes / keywords / senders)
  // ----------------------------------------------------------------
  const patterns = [
    { kind: "prefix", value: "0303", weight: 90, label: "Prefixo Anatel para telemarketing", channel: "call" },
    { kind: "prefix", value: "0900", weight: 80, label: "Prefixo de serviço pago (cobrança por minuto)", channel: "call" },
    { kind: "keyword", value: "responda sim", weight: 25, label: "Chamada para ação promocional", channel: "sms" },
    { kind: "keyword", value: "aproveite", weight: 15, label: "Linguagem publicitária", channel: "sms" },
    { kind: "keyword", value: "oferta especial", weight: 25, label: "Linguagem publicitária", channel: "sms" },
    { kind: "keyword", value: "pacote de internet", weight: 30, label: "Oferta de pacote de operadora", channel: "sms" },
    { kind: "keyword", value: "seu pix será bloqueado", weight: 60, label: "Fraude clássica de PIX", channel: "sms" },
    { kind: "keyword", value: "empréstimo aprovado", weight: 45, label: "Golpe de empréstimo", channel: "sms" },
    { kind: "keyword", value: "clique no link", weight: 35, label: "Indício de phishing", channel: "sms" },
    { kind: "sender", value: "OPERADORA", weight: 60, label: "Remetente publicitário genérico", channel: "sms" },
  ];
  for (const p of patterns) {
    await db.spamPattern.upsert({
      where: { kind_value_channel: { kind: p.kind, value: p.value, channel: p.channel } },
      update: { weight: p.weight, label: p.label },
      create: p,
    });
  }

  // ----------------------------------------------------------------
  // Operator sender rules (Claro, Vivo, TIM, Oi)
  // ----------------------------------------------------------------
  const operatorRules = [
    { operator: "Claro", sender: "5050", category: "advertising", action: "block" },
    { operator: "Claro", sender: "CLARO", category: "advertising", action: "block" },
    { operator: "Claro", sender: "206", category: "authentication", action: "allow" },
    { operator: "Vivo", sender: "280", category: "advertising", action: "block" },
    { operator: "Vivo", sender: "VIVO", category: "advertising", action: "block" },
    { operator: "Vivo", sender: "331", category: "billing", action: "quarantine" },
    { operator: "TIM", sender: "380", category: "advertising", action: "block" },
    { operator: "TIM", sender: "TIM", category: "advertising", action: "block" },
    { operator: "TIM", sender: "244", category: "authentication", action: "allow" },
    { operator: "Oi", sender: "502", category: "advertising", action: "block" },
    { operator: "Oi", sender: "OI", category: "advertising", action: "block" },
  ];
  for (const r of operatorRules) {
    await db.operatorRule.upsert({
      where: { operator_sender: { operator: r.operator, sender: r.sender } },
      update: { category: r.category, action: r.action },
      create: r,
    });
  }

  // ----------------------------------------------------------------
  // Demo history (last 7 days) for dashboard + reports
  // ----------------------------------------------------------------
  const existingEvents = await db.callEvent.count();
  if (existingEvents === 0) {
    const now = Date.now();
    const day = 24 * 60 * 60 * 1000;

    const demoCalls = [
      { e164: "+55030311100111", decision: "BLOCK", score: 95, cat: "telemarketing", reason: "Prefixo 0303 + reputação de telemarketing", h: 2 },
      { e164: "+5511912340001", decision: "BLOCK", score: 100, cat: "fraud", reason: "Golpe confirmado na base de reputação", h: 3 },
      { e164: "+5511999912345", decision: "BLOCK", score: 93, cat: "robocall", reason: "Robocall conhecido", h: 5 },
      { e164: "+5511987654321", decision: "ALLOW", score: 0, cat: "authorized", reason: "Número autorizado na lista de permissões", h: 6 },
      { e164: "+551140028922", decision: "BLOCK", score: 100, cat: "blacklisted", reason: "Número bloqueado manualmente", h: 8 },
      { e164: "+551130345678", decision: "BLOCK", score: 90, cat: "telemarketing", reason: "Classificado como telemarketing", h: 26 },
      { e164: "+5511944445555", decision: "WARN", score: 55, cat: "collector", reason: "Suspeito: cobrança automatizada", h: 28 },
      { e164: "+5521987650000", decision: "BLOCK", score: 92, cat: "robocall", reason: "Robocall conhecido", h: 30 },
      { e164: "+551138581111", decision: "ALLOW", score: 0, cat: "verified_business", reason: "Número comercial verificado", h: 32 },
      { e164: "+5511912340002", decision: "BLOCK", score: 100, cat: "fraud", reason: "Golpe confirmado na base de reputação", h: 49 },
      { e164: "+553130151234", decision: "BLOCK", score: 100, cat: "blacklisted", reason: "Número bloqueado manualmente", h: 52 },
      { e164: "+5511991234567", decision: "ALLOW", score: 10, cat: "unknown", reason: "Sem indícios de risco — chamada legítima", h: 55 },
      { e164: "+55030311100222", decision: "BLOCK", score: 92, cat: "telemarketing", reason: "Prefixo 0303 + reputação de telemarketing", h: 74 },
      { e164: "+551155551234", decision: "ALLOW", score: 0, cat: "verified_business", reason: "Número comercial verificado", h: 80 },
      { e164: "+5511999912345", decision: "BLOCK", score: 95, cat: "robocall", reason: "Robocall conhecido + insistência", h: 96 },
      { e164: "+5585991112233", decision: "BLOCK", score: 100, cat: "fraud", reason: "Golpe confirmado na base de reputação", h: 120 },
      { e164: "+5511987654321", decision: "ALLOW", score: 0, cat: "authorized", reason: "Número autorizado na lista de permissões", h: 140 },
    ];

    for (const c of demoCalls) {
      await db.callEvent.create({
        data: {
          e164: c.e164,
          decision: c.decision,
          riskScore: c.score,
          category: c.cat,
          evidences: JSON.stringify([{ label: c.reason, points: c.score }]),
          reason: c.reason,
          occurredAt: new Date(now - c.h * 60 * 60 * 1000),
        },
      });
    }

    const demoSms = [
      { sender: "280", content: "Vivo: Aproveite! Pacote de internet ilimitado com 50% de desconto. Responda SIM para contratar agora.", decision: "BLOCK", score: 85, cat: "advertising", reason: "Remetente publicitário da operadora", h: 1 },
      { sender: "244", content: "TIM: Seu código de verificação é 843291. Não compartilhe com ninguém.", decision: "ALLOW", score: 0, cat: "authentication", reason: "Autenticação/código — alta prioridade", h: 2 },
      { sender: "+5511912340001", content: "Seu PIX será bloqueado! Clique no link urgentemente para renove o cadastro.", decision: "BLOCK", score: 100, cat: "fraud", reason: "Indicadores de fraude/phishing", h: 4 },
      { sender: "CLARO", content: "Claro: Oferta especial! Dobro de dados por menos. Aproveite, última chance!", decision: "BLOCK", score: 80, cat: "advertising", reason: "Remetente publicitário da operadora", h: 7 },
      { sender: "331", content: "Vivo: Sua fatura vence amanhã. Consulte o boleto no app.", decision: "QUARANTINE", score: 40, cat: "billing", reason: "Suspeita: cobrança em quarentena", h: 9 },
      { sender: "+5511976543210", content: "Carlos: reunião mudou para 15h, pode confirmar?", decision: "ALLOW", score: 0, cat: "personal", reason: "Remetente autorizado", h: 11 },
      { sender: "5050", content: "Claro: Parabéns você ganhou 10GB de bônus! Responda JA para ativar.", decision: "BLOCK", score: 85, cat: "advertising", reason: "Remetente publicitário da operadora", h: 27 },
      { sender: "+5585991112233", content: "Empréstimo aprovado! 50 mil sem consulta. Clique no link e confirme seus dados.", decision: "BLOCK", score: 100, cat: "fraud", reason: "Golpe de empréstimo + link suspeito", h: 33 },
      { sender: "OPERADORA", content: "Prezado cliente! Aproveite nossa oferta especial. Contrate agora seu novo pacote de internet. Responda SIM.", decision: "BLOCK", score: 90, cat: "advertising", reason: "Remetente comercial + linguagem promocional", h: 47 },
      { sender: "380", content: "TIM: Conheça o novo plano com streaming incluso. Assine já!", decision: "BLOCK", score: 80, cat: "advertising", reason: "Remetente publicitário da operadora", h: 70 },
      { sender: "206", content: "Claro: Código de segurança 771203 para login na Minha Claro.", decision: "ALLOW", score: 0, cat: "authentication", reason: "Autenticação/código — alta prioridade", h: 76 },
    ];

    for (const s of demoSms) {
      await db.smsMessage.create({
        data: {
          sender: s.sender,
          content: s.content,
          decision: s.decision,
          riskScore: s.score,
          category: s.cat,
          evidences: JSON.stringify([{ label: s.reason, points: s.score }]),
          reason: s.reason,
          occurredAt: new Date(now - s.h * 60 * 60 * 1000),
        },
      });
    }

    // Demo reports (denúncias)
    await db.report.createMany({
      data: [
        { e164: "+5511999912345", category: "robocall", description: "Liga todos os dias com gravação de vendas.", status: "approved" },
        { e164: "+5511912340001", category: "fraud", description: "Se passou pelo banco e pediu código do PIX.", status: "approved" },
        { e164: "+55030311100111", category: "telemarketing", description: "Oferta de plano de TV insistente.", status: "approved" },
        { e164: "+5511777", category: "operator_spam", description: "SMS diário da operadora.", status: "pending" },
      ],
    });
  }

  const counts = {
    allowed: await db.allowedNumber.count(),
    blocked: await db.blockedNumber.count(),
    numbers: await db.phoneNumber.count(),
    patterns: await db.spamPattern.count(),
    operatorRules: await db.operatorRule.count(),
    calls: await db.callEvent.count(),
    sms: await db.smsMessage.count(),
  };
  console.log("Seed completed:", counts);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => db.$disconnect());
