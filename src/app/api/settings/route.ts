import { NextResponse } from "next/server";
import { db } from "@/lib/db";

export async function GET() {
  const settings = await db.userSettings.findUnique({ where: { id: "default" } });
  if (settings) return NextResponse.json(settings);
  const created = await db.userSettings.create({ data: { id: "default" } });
  return NextResponse.json(created);
}

export async function PATCH(request: Request) {
  const body = await request.json();
  const allowed = [
    "protectionActive",
    "blockThreshold",
    "warnThreshold",
    "blockTelemarketing",
    "blockRobocalls",
    "blockCollectors",
    "blockOperatorAds",
    "blockInternational",
    "blockUnknownNumbers",
    "silenceMode",
    "insistenceProtection",
    "waProtect",
    "waBlockUnknown",
    "waBlockLinks",
    "waBlockForwarded",
    "waBlockCodePhish",
    "waBlockGroupInvite",
    "spoofProtection",
  ] as const;

  const data: Record<string, boolean | number | string> = {};
  for (const key of allowed) {
    if (key in body) {
      if (key === "blockThreshold" || key === "warnThreshold") {
        data[key] = Math.max(0, Math.min(100, Number(body[key]) || 0));
      } else {
        data[key] = Boolean(body[key]);
      }
    }
  }
  // Own number (for neighbor-spoof detection): digits only, max 15
  if (typeof body.ownNumber === "string") {
    data.ownNumber = body.ownNumber.replace(/\D/g, "").slice(0, 15);
  }

  const settings = await db.userSettings.upsert({
    where: { id: "default" },
    update: data,
    create: { id: "default", ...data },
  });

  return NextResponse.json(settings);
}
