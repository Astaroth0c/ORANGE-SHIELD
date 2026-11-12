import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { formatPhone, normalizePhone } from "@/lib/engine/normalize";
import { riskBand } from "@/lib/engine/types";

/**
 * GET /api/lookup?number=... — Reputation database query (Identificação)
 */
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const raw = searchParams.get("number");
  if (!raw) return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });

  const e164 = normalizePhone(raw);
  if (!e164) return NextResponse.json({ error: "Número inválido" }, { status: 400 });

  const record = await db.phoneNumber.findUnique({
    where: { e164 },
    include: {
      reputation: true,
      reports: { orderBy: { createdAt: "desc" }, take: 5 },
      callEvents: { orderBy: { occurredAt: "desc" }, take: 5 },
    },
  });

  const allowed = await db.allowedNumber.findUnique({ where: { e164 } });
  const blocked = await db.blockedNumber.findUnique({ where: { e164 } });

  if (!record && !allowed && !blocked) {
    return NextResponse.json({
      e164,
      formatted: formatPhone(e164),
      found: false,
      listStatus: null,
      message: "Número sem registro na base de inteligência",
    });
  }

  return NextResponse.json({
    e164,
    formatted: formatPhone(e164),
    found: true,
    listStatus: allowed ? "allowed" : blocked ? "blocked" : null,
    listLabel: allowed?.name ?? blocked?.label ?? null,
    numberType: record?.numberType ?? null,
    operator: record?.operator ?? null,
    verified: record?.verified ?? false,
    reputation: record?.reputation
      ? {
          riskScore: record.reputation.riskScore,
          classification: record.reputation.classification,
          reportCount: record.reputation.reportCount,
          confidence: record.reputation.confidence,
          isConfirmedFraud: record.reputation.isConfirmedFraud,
          band: riskBand(record.reputation.riskScore),
          lastAnalyzedAt: record.reputation.lastAnalyzedAt,
        }
      : null,
    recentReports: record?.reports.map((r) => ({
      category: r.category,
      description: r.description,
      status: r.status,
      createdAt: r.createdAt,
    })),
    recentCalls: record?.callEvents.map((c) => ({
      decision: c.decision,
      riskScore: c.riskScore,
      reason: c.reason,
      occurredAt: c.occurredAt,
    })),
  });
}
