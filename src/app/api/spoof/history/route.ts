import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { formatPhone } from "@/lib/engine/normalize";

/**
 * GET /api/spoof/history — Aggregated spoofing intelligence (Engine 04, v0.4.0).
 *
 * Scans recorded call events' spoofFlags and returns:
 *   - total events carrying spoof evidence / blocked among them
 *   - per-flag-code distribution (how often each spoof technique fired)
 *   - recent flagged calls (for the "last spoof attacks" feed)
 */
export async function GET() {
  const events = await db.callEvent.findMany({
    where: { NOT: { spoofFlags: "[]" } },
    orderBy: { occurredAt: "desc" },
    select: {
      e164: true,
      decision: true,
      riskScore: true,
      spoofFlags: true,
      occurredAt: true,
    },
    take: 500,
  });

  const flagCounts = new Map<string, { code: string; label: string; count: number; points: number }>();
  let blockedCount = 0;
  let maxScore = 0;
  const recent: {
    e164: string;
    formatted: string;
    decision: string;
    riskScore: number;
    flags: { code: string; label: string; points: number }[];
    occurredAt: string;
  }[] = [];

  for (const ev of events) {
    let flags: { code: string; label: string; points: number }[] = [];
    try {
      flags = JSON.parse(ev.spoofFlags ?? "[]");
    } catch {
      flags = [];
    }
    if (flags.length === 0) continue;
    if (ev.decision === "BLOCK") blockedCount++;
    if (ev.riskScore > maxScore) maxScore = ev.riskScore;
    for (const f of flags) {
      const cur = flagCounts.get(f.code) ?? { code: f.code, label: f.label, count: 0, points: f.points };
      cur.count++;
      flagCounts.set(f.code, cur);
    }
    if (recent.length < 8) {
      recent.push({
        e164: ev.e164,
        formatted: formatPhone(ev.e164),
        decision: ev.decision,
        riskScore: ev.riskScore,
        flags,
        occurredAt: ev.occurredAt.toISOString(),
      });
    }
  }

  const distribution = [...flagCounts.values()].sort((a, b) => b.count - a.count);

  return NextResponse.json({
    totalFlagged: events.length,
    totalBlocked: blockedCount,
    highestRisk: maxScore,
    distribution,
    recent,
  });
}
