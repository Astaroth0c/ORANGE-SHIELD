import { NextResponse } from "next/server";
import { analyzeIncomingSms } from "@/lib/engine/sms-engine";
import { engineSettings } from "@/lib/engine/settings";

/**
 * POST /api/engine/sms — Screens an incoming SMS through the SMS Guard.
 * Body: { sender: string, content: string }
 */
export async function POST(request: Request) {
  try {
    const { sender, content } = await request.json();
    if (!sender || typeof sender !== "string" || typeof content !== "string") {
      return NextResponse.json({ error: "Remetente e conteúdo obrigatórios" }, { status: 400 });
    }
    const settings = await engineSettings();
    const analysis = await analyzeIncomingSms(sender, content, settings);
    return NextResponse.json(analysis);
  } catch (error) {
    console.error("SMS engine error:", error);
    return NextResponse.json({ error: "Falha ao analisar a mensagem" }, { status: 500 });
  }
}
