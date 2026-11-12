import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { formatPhone, normalizePhone } from "@/lib/engine/normalize";
import { analyzeSpoofing } from "@/lib/engine/spoof";
import { engineSettings } from "@/lib/engine/settings";

/**
 * GET /api/spoof?number=... — Dedicated anti-spoofing analysis (Engine 04).
 * Returns every check (pass/flag/info), the spoof score and verdict,
 * plus the number's recent screening history (for Wangiri context).
 */
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const raw = searchParams.get("number");
  if (!raw) return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });

  const e164 = normalizePhone(raw);
  if (!e164) return NextResponse.json({ error: "Número inválido" }, { status: 400 });

  const settings = await engineSettings();
  const analysis = await analyzeSpoofing(e164, settings.ownNumber);

  const recent = await db.callEvent.findMany({
    where: { e164 },
    orderBy: { occurredAt: "desc" },
    take: 5,
    select: { decision: true, riskScore: true, reason: true, occurredAt: true, spoofFlags: true },
  });

  return NextResponse.json({
    e164,
    formatted: formatPhone(e164),
    ownNumberConfigured: !!settings.ownNumber,
    ...analysis,
    recentEvents: recent.map((r) => ({
      decision: r.decision,
      riskScore: r.riskScore,
      reason: r.reason,
      occurredAt: r.occurredAt,
      spoofFlags: JSON.parse(r.spoofFlags ?? "[]"),
    })),
  });
}
