import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { normalizePhone } from "@/lib/engine/normalize";

export async function GET() {
  const items = await db.blockedNumber.findMany({ orderBy: { createdAt: "desc" } });
  return NextResponse.json(items);
}

export async function POST(request: Request) {
  const { number, label } = await request.json();
  if (!number || typeof number !== "string") {
    return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });
  }
  const e164 = normalizePhone(number);
  if (!e164) {
    return NextResponse.json({ error: "Número inválido" }, { status: 400 });
  }
  const item = await db.blockedNumber.upsert({
    where: { e164 },
    update: { label: label ?? undefined },
    create: { e164, label: label ?? null, source: "manual" },
  });
  return NextResponse.json(item, { status: 201 });
}

export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url);
  const id = searchParams.get("id");
  if (!id) return NextResponse.json({ error: "id obrigatório" }, { status: 400 });
  await db.blockedNumber.delete({ where: { id } }).catch(() => null);
  return NextResponse.json({ ok: true });
}
