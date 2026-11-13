import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * GET /api/stats — Dashboard aggregate metrics
 */
export async function GET() {
  const now = new Date();
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

  // Engine intelligence (v0.3.0): raw rows for decision/risk distribution,
  // top threats and blocked channel counts in the last 7 days.
  const [
    blockedCalls,
    blockedSms,
    fraudCalls,
    quarantinedSms,
    blockedCalls24h,
    blockedSms24h,
    allowedCalls24h,
    totalNumbers,
    knownNumbers,
    pendingReports,
    decisionByDay,
    callsByCategory,
    smsByCategory,
    lastEvent,
    blockedWa,
    quarantinedWa,
    spoofEvents,
    spoofBlocked,
    weekCallRows,
    blockedSmsWeek,
    blockedWaWeek,
  ] = await Promise.all([
    db.callEvent.count({ where: { decision: "BLOCK" } }),
    db.smsMessage.count({ where: { decision: "BLOCK" } }),
    db.callEvent.count({ where: { decision: "BLOCK", category: "fraud" } }),
    db.smsMessage.count({ where: { decision: "QUARANTINE" } }),
    db.callEvent.count({ where: { decision: "BLOCK", occurredAt: { gte: dayAgo } } }),
    db.smsMessage.count({ where: { decision: "BLOCK", occurredAt: { gte: dayAgo } } }),
    db.callEvent.count({ where: { decision: { in: ["ALLOW", "WARN"] }, occurredAt: { gte: dayAgo } } }),
    db.phoneNumber.count(),
    db.phoneNumber.count({ where: { reputation: { isNot: null } } }),
    db.report.count({ where: { status: "pending" } }),
    db.callEvent.findMany({
      where: { occurredAt: { gte: weekAgo } },
      select: { decision: true, occurredAt: true },
    }),
    db.callEvent.groupBy({
      by: ["category"],
      _count: { _all: true },
      where: { decision: "BLOCK" },
    }),
    db.smsMessage.groupBy({
      by: ["category"],
      _count: { _all: true },
      where: { decision: { in: ["BLOCK", "QUARANTINE"] } },
    }),
    db.callEvent.findFirst({ orderBy: { occurredAt: "desc" } }),
    db.whatsAppMessage.count({ where: { decision: "BLOCK" } }),
    db.whatsAppMessage.count({ where: { decision: "QUARANTINE" } }),
    db.callEvent.count({ where: { category: "spoofing" } }),
    db.callEvent.count({ where: { category: "spoofing", decision: "BLOCK" } }),
    db.callEvent.findMany({
      where: { occurredAt: { gte: weekAgo } },
      select: { decision: true, riskScore: true, e164: true, occurredAt: true },
    }),
    db.smsMessage.count({ where: { decision: "BLOCK", occurredAt: { gte: weekAgo } } }),
    db.whatsAppMessage.count({ where: { decision: "BLOCK", occurredAt: { gte: weekAgo } } }),
  ]);

  // Build 7-day series
  const days: { day: string; blocked: number; allowed: number }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 24 * 60 * 60 * 1000);
    const key = d.toISOString().slice(0, 10);
    const dayCalls = decisionByDay.filter(
      (e) => e.occurredAt.toISOString().slice(0, 10) === key
    );
    days.push({
      day: key,
      blocked: dayCalls.filter((e) => e.decision === "BLOCK").length,
      allowed: dayCalls.filter((e) => e.decision !== "BLOCK").length,
    });
  }

  // ---- Engine intelligence (v0.3.0) --------------------------------
  const decisionDistribution = {
    ALLOW: weekCallRows.filter((r) => r.decision === "ALLOW").length,
    WARN: weekCallRows.filter((r) => r.decision === "WARN").length,
    SILENCE: weekCallRows.filter((r) => r.decision === "SILENCE").length,
    BLOCK: weekCallRows.filter((r) => r.decision === "BLOCK").length,
  };

  const riskBands = { safe: 0, low: 0, suspect: 0, high: 0, critical: 0 };
  for (const r of weekCallRows) {
    if (r.riskScore <= 19) riskBands.safe++;
    else if (r.riskScore <= 39) riskBands.low++;
    else if (r.riskScore <= 59) riskBands.suspect++;
    else if (r.riskScore <= 79) riskBands.high++;
    else riskBands.critical++;
  }

  const threatMap = new Map<string, { e164: string; hits: number; blocked: number; lastSeen: Date }>();
  for (const r of weekCallRows) {
    if (r.decision !== "BLOCK" && r.decision !== "WARN") continue;
    const cur = threatMap.get(r.e164) ?? { e164: r.e164, hits: 0, blocked: 0, lastSeen: new Date(0) };
    cur.hits++;
    if (r.decision === "BLOCK") cur.blocked++;
    if (r.occurredAt > cur.lastSeen) cur.lastSeen = r.occurredAt;
    threatMap.set(r.e164, cur);
  }
  const topThreats = [...threatMap.values()]
    .sort((a, b) => b.blocked - a.blocked || b.hits - a.hits)
    .slice(0, 5)
    .map((t) => ({ e164: t.e164, hits: t.hits, blocked: t.blocked, lastSeen: t.lastSeen.toISOString() }));

  const channelAttacks = { calls: decisionDistribution.BLOCK, sms: blockedSmsWeek, whatsapp: blockedWaWeek };

  // Auto-generated insights (plain language, actionable)
  const totalWeek = weekCallRows.length;
  const blockPct = totalWeek > 0 ? Math.round((decisionDistribution.BLOCK / totalWeek) * 100) : 0;
  const mostAttackedChannel = (Object.entries(channelAttacks) as [string, number][]).sort((a, b) => b[1] - a[1])[0];
  const channelLabel: Record<string, string> = { calls: "chamadas", sms: "SMS", whatsapp: "WhatsApp" };
  const insights: { icon: string; text: string }[] = [];
  if (totalWeek === 0) {
    insights.push({ icon: "shield", text: "Sem tráfego nos últimos 7 dias — use o simulador para exercitar o motor." });
  } else {
    if (blockPct >= 50) insights.push({ icon: "shield", text: `${blockPct}% das chamadas da semana foram bloqueadas — semana de ataque intenso.` });
    else if (blockPct > 0) insights.push({ icon: "shield", text: `${blockPct}% das chamadas da semana foram bloqueadas.` });
    else insights.push({ icon: "shield", text: "Nenhuma chamada bloqueada na semana — tráfego limpo." });
    if (mostAttackedChannel && mostAttackedChannel[1] > 0) {
      insights.push({ icon: "target", text: `Canal mais atacado: ${channelLabel[mostAttackedChannel[0]]} (${mostAttackedChannel[1]} bloqueios).` });
    }
    if (riskBands.critical > 0) {
      insights.push({ icon: "siren", text: `${riskBands.critical} evento(s) com risco crítico (≥80/100) — verifique o histórico.` });
    }
    if (topThreats.length > 0) {
      insights.push({ icon: "fingerprint", text: `Ameaça recorrente: ${topThreats[0].e164} (${topThreats[0].blocked} bloqueio(s)).` });
    }
  }

  return NextResponse.json({
    blockedCalls,
    blockedSms,
    fraudCalls,
    quarantinedSms,
    blockedCalls24h,
    blockedSms24h,
    allowedCalls24h,
    totalNumbers,
    knownNumbers,
    pendingReports,
    blockedWa,
    quarantinedWa,
    spoofEvents,
    spoofBlocked,
    days,
    callsByCategory: callsByCategory.map((c) => ({ category: c.category ?? "unknown", count: c._count._all })),
    smsByCategory: smsByCategory.map((c) => ({ category: c.category ?? "unknown", count: c._count._all })),
    lastEventAt: lastEvent?.occurredAt ?? null,
    decisionDistribution,
    riskBands,
    topThreats,
    channelAttacks,
    insights,
  });
}
