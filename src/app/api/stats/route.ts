import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * GET /api/stats — Dashboard aggregate metrics
 */
export async function GET() {
  const now = new Date();
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

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
  });
}
