import { timingSafeEqual } from "node:crypto";
import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { normalizePhone } from "@/lib/engine/normalize";

function isAuthorizedModerator(request: Request): boolean {
  const expected = process.env.ORANGE_ADMIN_TOKEN;
  const authorization = request.headers.get("authorization") ?? "";
  const supplied = authorization.match(/^Bearer\s+(.+)$/i)?.[1] ?? "";

  if (!expected || !supplied) return false;

  const expectedBuffer = Buffer.from(expected);
  const suppliedBuffer = Buffer.from(supplied);
  return expectedBuffer.length === suppliedBuffer.length &&
    timingSafeEqual(expectedBuffer, suppliedBuffer);
}

export async function GET() {
  const reports = await db.report.findMany({
    orderBy: { createdAt: "desc" },
    take: 50,
  });
  return NextResponse.json(reports);
}

/**
 * POST /api/reports — recebe uma denúncia sempre como pendente.
 * Aprovação automática enviada pelo cliente é deliberadamente ignorada.
 */
export async function POST(request: Request) {
  const body = await request.json();
  const { number, category, description } = body ?? {};

  if (typeof number !== "string" || !number.trim()) {
    return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });
  }

  const validCategories = ["telemarketing", "robocall", "fraud", "operator_spam", "other"];
  if (typeof category !== "string" || !validCategories.includes(category)) {
    return NextResponse.json({ error: "Categoria inválida" }, { status: 400 });
  }
  if (description !== undefined && description !== null &&
      (typeof description !== "string" || description.length > 500)) {
    return NextResponse.json({ error: "Descrição deve ter até 500 caracteres" }, { status: 400 });
  }

  const e164 = normalizePhone(number);
  if (!e164 || e164.length > 16) {
    return NextResponse.json({ error: "Número inválido" }, { status: 400 });
  }

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
      status: "pending",
    },
  });

  return NextResponse.json(report, { status: 201 });
}

/**
 * PATCH /api/reports — moderação restrita ao servidor.
 * Configure ORANGE_ADMIN_TOKEN no ambiente do servidor e envie:
 * Authorization: Bearer <token>
 */
export async function PATCH(request: Request) {
  if (!process.env.ORANGE_ADMIN_TOKEN) {
    return NextResponse.json(
      { error: "Moderação desabilitada: ORANGE_ADMIN_TOKEN não configurado no servidor" },
      { status: 503 }
    );
  }
  if (!isAuthorizedModerator(request)) {
    return NextResponse.json({ error: "Não autorizado" }, { status: 401 });
  }

  const body = await request.json();
  const { id, status } = body ?? {};
  if (typeof id !== "string" || !["approved", "rejected"].includes(status)) {
    return NextResponse.json({ error: "id e status válidos obrigatórios" }, { status: 400 });
  }

  const existing = await db.report.findUnique({ where: { id } });
  if (!existing) {
    return NextResponse.json({ error: "Denúncia não encontrada" }, { status: 404 });
  }
  if (existing.status !== "pending") {
    return NextResponse.json({ error: "A denúncia já foi moderada" }, { status: 409 });
  }

  const report = await db.report.update({
    where: { id },
    data: { status },
  });

  if (status === "approved" && report.phoneNumberId) {
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
