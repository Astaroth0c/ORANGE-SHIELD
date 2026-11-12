import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * GET /api/history/calls?decision=BLOCK&limit=50 — Call screening history
 */
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const decision = searchParams.get("decision");
  const limit = Math.min(200, Number(searchParams.get("limit")) || 80);

  const events = await db.callEvent.findMany({
    where: decision && decision !== "ALL" ? { decision } : undefined,
    orderBy: { occurredAt: "desc" },
    take: limit,
    include: { phoneNumber: { include: { reputation: true } } },
  });

  return NextResponse.json(
    events.map((e) => ({
      id: e.id,
      e164: e.e164,
      decision: e.decision,
      riskScore: e.riskScore,
      category: e.category,
      evidences: JSON.parse(e.evidences ?? "[]"),
      reason: e.reason,
      ruleVersion: e.ruleVersion,
      occurredAt: e.occurredAt,
      operator: e.phoneNumber?.operator ?? null,
      classification: e.phoneNumber?.reputation?.classification ?? null,
    }))
  );
}

/** DELETE /api/history/calls — clears call history */
export async function DELETE() {
  await db.callEvent.deleteMany({});
  return NextResponse.json({ ok: true });
}
