import { NextResponse } from "next/server";
import { analyzeIncomingWhatsApp } from "@/lib/engine/whatsapp-engine";
import { engineSettings } from "@/lib/engine/settings";

/**
 * POST /api/engine/whatsapp — Screens an incoming WhatsApp message
 * through the WhatsApp Guard engine.
 * Body: { sender: string, content: string, forwardedMany?: boolean, isGroupInvite?: boolean }
 */
export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { sender, content, forwardedMany, isGroupInvite } = body ?? {};
    if (!sender || typeof sender !== "string") {
      return NextResponse.json({ error: "Remetente obrigatório" }, { status: 400 });
    }
    if (typeof content !== "string" || !content.trim()) {
      return NextResponse.json({ error: "Conteúdo da mensagem obrigatório" }, { status: 400 });
    }
    const settings = await engineSettings();
    const analysis = await analyzeIncomingWhatsApp(sender, content, settings, {
      forwardedMany: Boolean(forwardedMany),
      isGroupInvite: Boolean(isGroupInvite),
    });
    return NextResponse.json(analysis);
  } catch (error) {
    console.error("WhatsApp engine error:", error);
    return NextResponse.json({ error: "Falha ao analisar a mensagem" }, { status: 500 });
  }
}
