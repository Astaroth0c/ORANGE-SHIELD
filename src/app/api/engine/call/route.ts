import { NextResponse } from "next/server";
import { analyzeIncomingCall } from "@/lib/engine/call-engine";
import { engineSettings } from "@/lib/engine/settings";

/**
 * POST /api/engine/call — Screens an incoming call through the SmartCall Engine.
 * Body: { number: string }
 */
export async function POST(request: Request) {
  try {
    const { number } = await request.json();
    if (!number || typeof number !== "string") {
      return NextResponse.json({ error: "Número obrigatório" }, { status: 400 });
    }
    const settings = await engineSettings();
    const analysis = await analyzeIncomingCall(number, settings);
    return NextResponse.json(analysis);
  } catch (error) {
    console.error("Call engine error:", error);
    return NextResponse.json({ error: "Falha ao analisar a chamada" }, { status: 500 });
  }
}
