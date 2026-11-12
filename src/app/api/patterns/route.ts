import { NextResponse } from "next/server";
import { db } from "@/lib/db";

export async function GET() {
  const items = await db.spamPattern.findMany({ orderBy: [{ kind: "asc" }, { weight: "desc" }] });
  return NextResponse.json(items);
}

export async function POST(request: Request) {
  const { kind, value, weight, label, channel } = await request.json();
  if (!kind || !value) {
    return NextResponse.json({ error: "Tipo e valor obrigatórios" }, { status: 400 });
  }
  const validKinds = ["prefix", "keyword", "sender"];
  const validChannels = ["call", "sms", "both"];
  if (!validKinds.includes(kind) || !validChannels.includes(channel ?? "both")) {
    return NextResponse.json({ error: "Tipo/canal inválido" }, { status: 400 });
  }
  const item = await db.spamPattern.upsert({
    where: {
      kind_value_channel: {
        kind,
        value: kind === "keyword" ? value.toLowerCase() : value.toUpperCase(),
        channel: channel ?? "both",
      },
    },
    update: { weight: Number(weight) || 20, label: label ?? null, active: true },
    create: {
      kind,
      value: kind === "keyword" ? value.toLowerCase() : value.toUpperCase(),
      weight: Number(weight) || 20,
      label: label ?? null,
      channel: channel ?? "both",
    },
  });
  return NextResponse.json(item, { status: 201 });
}

export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url);
  const id = searchParams.get("id");
  if (!id) return NextResponse.json({ error: "id obrigatório" }, { status: 400 });
  await db.spamPattern.delete({ where: { id } }).catch(() => null);
  return NextResponse.json({ ok: true });
}
