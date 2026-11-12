import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { normalizePhone } from "@/lib/engine/normalize";

export async function GET() {
  const reports = await db.report.findMany({
    orderBy: { createdAt: "desc" },
    take: 50,
  });
  return NextResponse.json(reports);
}

/**
 * POST /api/reports — User reports a number (denúncia coletiva).
 * Approved reports increment the collective reputation score.
 */
export async function POST(request: Request) {
  const { number, category, description, autoApprove } = await request.json();
  if (!number || typeof number !== "string") {
    return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });
  }
  const validCategories = ["telemarketing", "robocall", "fraud", "operator_spam", "other"];
  if (!validCategories.includes(category)) {
    return NextResponse.json({ error: "Categoria inválida" }, { status: 400 });
  }

  const e164 = normalizePhone(number);

  // Never allow reporting allowed/emergency numbers (false positive guard)
  const allowed = await db.allowedNumber.findUnique({ where: { e164 } });
  if (allowed) {
    return NextResponse.json(
      { error: "Número autorizado não pode ser denunciado — remova-o da lista de permissões primeiro" },
      { status: 409 }
    );
  }

  const phoneRecord = await db.phoneNumber.upsert({
    where: { e164 },
    update: {},
    create: { e164, numberType: "mobile" },
  });

  const report = await db.report.create({
    data: {
      e164,
      phoneNumberId: phoneRecord.id,
      category,
      description: description ?? null,
      status: autoApprove ? "approved" : "pending",
    },
  });

  if (autoApprove) {
    await applyApprovedReport(e164, phoneRecord.id, category);
  }

  return NextResponse.json(report, { status: 201 });
}

/** PATCH /api/reports — moderation: approve/reject a pending report */
export async function PATCH(request: Request) {
  const { id, status } = await request.json();
  if (!id || !["approved", "rejected", "pending"].includes(status)) {
    return NextResponse.json({ error: "id e status válidos obrigatórios" }, { status: 400 });
  }
  const report = await db.report.update({
    where: { id },
    data: { status },
  });

  if (status === "approved") {
    await applyApprovedReport(report.e164, report.phoneNumberId, report.category);
  }

  return NextResponse.json(report);
}

/** Applies an approved report to the number's collective reputation. */
async function applyApprovedReport(e164: string, phoneNumberId: string, category: string) {
  const current = await db.numberReputation.findUnique({
    where: { phoneNumberId },
  });

  const reportCount = (current?.reportCount ?? 0) + 1;
  const confidence = Math.min(0.99, 0.5 + reportCount * 0.05);
  const classification = category === "operator_spam" ? "telemarketing" : category;
  const isConfirmedFraud =
    category === "fraud" && reportCount >= 3 ? true : (current?.isConfirmedFraud ?? false);

  await db.numberReputation.upsert({
    where: { phoneNumberId },
    update: {
      reportCount,
      confidence,
      classification,
      isConfirmedFraud,
      riskScore: Math.min(100, (current?.riskScore ?? 0) + 20),
      lastAnalyzedAt: new Date(),
    },
    create: {
      phoneNumberId,
      reportCount,
      confidence,
      classification,
      isConfirmedFraud,
      riskScore: 40,
      lastAnalyzedAt: new Date(),
    },
  });
}
