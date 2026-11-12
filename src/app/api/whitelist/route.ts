import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { normalizePhone } from "@/lib/engine/normalize";

export async function GET() {
  const items = await db.allowedNumber.findMany({ orderBy: { createdAt: "desc" } });
  return NextResponse.json(items);
}

export async function POST(request: Request) {
  const { number, name, kind } = await request.json();
  if (!number || typeof number !== "string") {
    return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });
  }
  const e164 = normalizePhone(number);
  if (!e164) {
    return NextResponse.json({ error: "Número inválido" }, { status: 400 });
  }
  const item = await db.allowedNumber.upsert({
    where: { e164 },
    update: { name: name ?? undefined, kind: kind ?? undefined },
    create: {
      e164,
      name: name ?? null,
      kind: kind === "emergency" || kind === "business" ? kind : "contact",
    },
  });
  return NextResponse.json(item, { status: 201 });
}

export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url);
  const id = searchParams.get("id");
  if (!id) return NextResponse.json({ error: "id obrigatório" }, { status: 400 });
  await db.allowedNumber.delete({ where: { id } }).catch(() => null);
  return NextResponse.json({ ok: true });
}
