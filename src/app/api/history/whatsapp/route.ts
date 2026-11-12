import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * GET /api/history/whatsapp?decision=BLOCK&limit=50 — WhatsApp Guard history
 */
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const decision = searchParams.get("decision");
  const limit = Math.min(200, Number(searchParams.get("limit")) || 80);

  const messages = await db.whatsAppMessage.findMany({
    where: decision && decision !== "ALL" ? { decision } : undefined,
    orderBy: { occurredAt: "desc" },
    take: limit,
    include: { phoneNumber: { include: { reputation: true } } },
  });

  return NextResponse.json(
    messages.map((m) => ({
      id: m.id,
      sender: m.sender,
      content: m.content,
      decision: m.decision,
      riskScore: m.riskScore,
      category: m.category,
      forwardedMany: m.forwardedMany,
      hasLink: m.hasLink,
      isGroupInvite: m.isGroupInvite,
      evidences: JSON.parse(m.evidences ?? "[]"),
      reason: m.reason,
      occurredAt: m.occurredAt,
      classification: m.phoneNumber?.reputation?.classification ?? null,
    }))
  );
}

/** DELETE /api/history/whatsapp — clears WhatsApp history */
export async function DELETE() {
  await db.whatsAppMessage.deleteMany({});
  return NextResponse.json({ ok: true });
}
