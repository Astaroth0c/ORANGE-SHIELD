import { NextResponse } from "next/server";
import { db } from "@/lib/db";

export async function GET() {
  const rules = await db.operatorRule.findMany({ orderBy: [{ operator: "asc" }, { sender: "asc" }] });
  return NextResponse.json(rules);
}

/** PATCH /api/operators — update a sender rule action/active */
export async function PATCH(request: Request) {
  const { id, action, active } = await request.json();
  if (!id) return NextResponse.json({ error: "id obrigatório" }, { status: 400 });

  const validActions = ["allow", "quarantine", "block"];
  if (action !== undefined && !validActions.includes(action)) {
    return NextResponse.json({ error: "ação inválida" }, { status: 400 });
  }

  const rule = await db.operatorRule.update({
    where: { id },
    data: {
      ...(action !== undefined ? { action } : {}),
      ...(active !== undefined ? { active } : {}),
    },
  });
  return NextResponse.json(rule);
}

/** POST /api/operators — add a sender rule */
export async function POST(request: Request) {
  const { operator, sender, category, action } = await request.json();
  if (!operator || !sender) {
    return NextResponse.json({ error: "Operadora e remetente obrigatórios" }, { status: 400 });
  }
  const rule = await db.operatorRule.upsert({
    where: { operator_sender: { operator, sender: sender.toUpperCase() } },
    update: { category: category ?? "advertising", action: action ?? "block", active: true },
    create: {
      operator,
      sender: sender.toUpperCase(),
      category: category ?? "advertising",
      action: action ?? "block",
    },
  });
  return NextResponse.json(rule, { status: 201 });
}

export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url);
  const id = searchParams.get("id");
  if (!id) return NextResponse.json({ error: "id obrigatório" }, { status: 400 });
  await db.operatorRule.delete({ where: { id } }).catch(() => null);
  return NextResponse.json({ ok: true });
}
