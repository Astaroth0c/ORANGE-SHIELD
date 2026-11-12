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
  ] as const;

  const data: Record<string, boolean | number> = {};
  for (const key of allowed) {
    if (key in body) {
      if (key === "blockThreshold" || key === "warnThreshold") {
        data[key] = Math.max(0, Math.min(100, Number(body[key]) || 0));
      } else {
        data[key] = Boolean(body[key]);
      }
    }
  }

  const settings = await db.userSettings.upsert({
    where: { id: "default" },
    update: data,
    create: { id: "default", ...data },
  });

  return NextResponse.json(settings);
}
