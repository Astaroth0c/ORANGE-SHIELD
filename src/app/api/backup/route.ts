import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * ORANGE SHIELD — /api/backup (v0.5.0)
 *
 * GET  → full JSON export of every protection dataset (settings, lists,
 *        intelligence base, reports and all engine events), wrapped in a
 *        versioned envelope so restores can be validated.
 * POST → restore from a valid backup payload. Wipes current data in
 *        FK-safe order and re-inserts everything, re-linking phone
 *        number relations by e164.
 */

interface BackupReputation {
  riskScore: number;
  classification: string;
  reportCount: number;
  confidence: number;
  isConfirmedFraud: boolean;
  lastAnalyzedAt: string | null;
}

interface BackupPayload {
  app?: string;
  schemaVersion?: number;
  data?: {
    settings?: Record<string, unknown>;
    phoneNumbers?: {
      e164: string;
      countryCode?: string;
      numberType?: string;
      operator?: string | null;
      verified?: boolean;
      reputation?: BackupReputation | null;
    }[];
    blockedNumbers?: { e164: string; label?: string | null; source?: string; createdAt?: string }[];
    allowedNumbers?: { e164: string; name?: string | null; kind?: string; createdAt?: string }[];
    spamPatterns?: { kind: string; value: string; weight: number; label?: string | null; channel?: string; active?: boolean }[];
    operatorRules?: { operator: string; sender: string; category: string; action?: string; active?: boolean }[];
    reports?: { e164: string; category: string; description?: string | null; status?: string; createdAt?: string }[];
    callEvents?: {
      e164: string; decision: string; riskScore: number; category?: string | null;
      evidences?: string; reason: string; spoofFlags?: string; ruleVersion?: string; occurredAt?: string;
    }[];
    smsMessages?: {
      sender: string; content: string; category?: string; decision: string; riskScore: number;
      evidences?: string; reason: string; occurredAt?: string;
    }[];
    whatsappMessages?: {
      sender: string; content: string; category?: string; decision: string; riskScore: number;
      forwardedMany?: boolean; hasLink?: boolean; isGroupInvite?: boolean;
      evidences?: string; reason: string; occurredAt?: string;
    }[];
  };
}

export async function GET() {
  const [settings, phoneNumbers, blockedNumbers, allowedNumbers, spamPatterns, operatorRules, reports, callEvents, smsMessages, whatsappMessages] =
    await Promise.all([
      db.userSettings.findUnique({ where: { id: "default" } }),
      db.phoneNumber.findMany({ include: { reputation: true } }),
      db.blockedNumber.findMany(),
      db.allowedNumber.findMany(),
      db.spamPattern.findMany(),
      db.operatorRule.findMany(),
      db.report.findMany(),
      db.callEvent.findMany(),
      db.smsMessage.findMany(),
      db.whatsAppMessage.findMany(),
    ]);

  const payload = {
    app: "ORANGE SHIELD",
    schemaVersion: 1,
    exportedAt: new Date().toISOString(),
    counts: {
      phoneNumbers: phoneNumbers.length,
      blockedNumbers: blockedNumbers.length,
      allowedNumbers: allowedNumbers.length,
      spamPatterns: spamPatterns.length,
      operatorRules: operatorRules.length,
      reports: reports.length,
      callEvents: callEvents.length,
      smsMessages: smsMessages.length,
      whatsappMessages: whatsappMessages.length,
    },
    data: {
      settings: settings
        ? {
            protectionActive: settings.protectionActive,
            blockThreshold: settings.blockThreshold,
            warnThreshold: settings.warnThreshold,
            blockTelemarketing: settings.blockTelemarketing,
            blockRobocalls: settings.blockRobocalls,
            blockCollectors: settings.blockCollectors,
            blockOperatorAds: settings.blockOperatorAds,
            blockInternational: settings.blockInternational,
            blockUnknownNumbers: settings.blockUnknownNumbers,
            silenceMode: settings.silenceMode,
            insistenceProtection: settings.insistenceProtection,
            waProtect: settings.waProtect,
            waBlockUnknown: settings.waBlockUnknown,
            waBlockLinks: settings.waBlockLinks,
            waBlockForwarded: settings.waBlockForwarded,
            waBlockCodePhish: settings.waBlockCodePhish,
            waBlockGroupInvite: settings.waBlockGroupInvite,
            spoofProtection: settings.spoofProtection,
            ownNumber: settings.ownNumber,
            ruleVersion: settings.ruleVersion,
          }
        : null,
      phoneNumbers: phoneNumbers.map((p) => ({
        e164: p.e164,
        countryCode: p.countryCode,
        numberType: p.numberType,
        operator: p.operator,
        verified: p.verified,
        reputation: p.reputation
          ? {
              riskScore: p.reputation.riskScore,
              classification: p.reputation.classification,
              reportCount: p.reputation.reportCount,
              confidence: p.reputation.confidence,
              isConfirmedFraud: p.reputation.isConfirmedFraud,
              lastAnalyzedAt: p.reputation.lastAnalyzedAt?.toISOString() ?? null,
            }
          : null,
      })),
      blockedNumbers: blockedNumbers.map((b) => ({
        e164: b.e164, label: b.label, source: b.source, createdAt: b.createdAt.toISOString(),
      })),
      allowedNumbers: allowedNumbers.map((a) => ({
        e164: a.e164, name: a.name, kind: a.kind, createdAt: a.createdAt.toISOString(),
      })),
      spamPatterns: spamPatterns.map((s) => ({
        kind: s.kind, value: s.value, weight: s.weight, label: s.label, channel: s.channel, active: s.active,
      })),
      operatorRules: operatorRules.map((o) => ({
        operator: o.operator, sender: o.sender, category: o.category, action: o.action, active: o.active,
      })),
      reports: reports.map((r) => ({
        e164: r.e164, category: r.category, description: r.description, status: r.status,
        createdAt: r.createdAt.toISOString(),
      })),
      callEvents: callEvents.map((c) => ({
        e164: c.e164, decision: c.decision, riskScore: c.riskScore, category: c.category,
        evidences: c.evidences, reason: c.reason, spoofFlags: c.spoofFlags,
        ruleVersion: c.ruleVersion, occurredAt: c.occurredAt.toISOString(),
      })),
      smsMessages: smsMessages.map((s) => ({
        sender: s.sender, content: s.content, category: s.category, decision: s.decision,
        riskScore: s.riskScore, evidences: s.evidences, reason: s.reason, occurredAt: s.occurredAt.toISOString(),
      })),
      whatsappMessages: whatsappMessages.map((w) => ({
        sender: w.sender, content: w.content, category: w.category, decision: w.decision,
        riskScore: w.riskScore, forwardedMany: w.forwardedMany, hasLink: w.hasLink,
        isGroupInvite: w.isGroupInvite, evidences: w.evidences, reason: w.reason,
        occurredAt: w.occurredAt.toISOString(),
      })),
    },
  };

  return NextResponse.json(payload);
}

export async function POST(request: Request) {
  let body: BackupPayload;
  try {
    body = (await request.json()) as BackupPayload;
  } catch {
    return NextResponse.json({ error: "JSON inválido" }, { status: 400 });
  }

  const data = body?.data;
  if (!data || body?.app !== "ORANGE SHIELD" || body?.schemaVersion !== 1) {
    return NextResponse.json(
      { error: "Arquivo de backup inválido — esperado envelope ORANGE SHIELD schemaVersion 1" },
      { status: 400 }
    );
  }

  try {
    // Wipe in FK-safe order (children first).
    await db.$transaction([
      db.report.deleteMany(),
      db.callEvent.deleteMany(),
      db.smsMessage.deleteMany(),
      db.whatsAppMessage.deleteMany(),
      db.numberReputation.deleteMany(),
      db.phoneNumber.deleteMany(),
      db.blockedNumber.deleteMany(),
      db.allowedNumber.deleteMany(),
      db.spamPattern.deleteMany(),
      db.operatorRule.deleteMany(),
    ]);

    // Re-insert phone numbers (map for reputation + event re-linking).
    const phoneMap = new Map<string, string>();
    for (const p of data.phoneNumbers ?? []) {
      const created = await db.phoneNumber.create({
        data: {
          e164: p.e164,
          countryCode: p.countryCode ?? "+55",
          numberType: p.numberType ?? "unknown",
          operator: p.operator ?? null,
          verified: p.verified ?? false,
        },
      });
      phoneMap.set(p.e164, created.id);
      if (p.reputation) {
        await db.numberReputation.create({
          data: {
            phoneNumberId: created.id,
            riskScore: p.reputation.riskScore,
            classification: p.reputation.classification,
            reportCount: p.reputation.reportCount,
            confidence: p.reputation.confidence,
            isConfirmedFraud: p.reputation.isConfirmedFraud,
            lastAnalyzedAt: p.reputation.lastAnalyzedAt ? new Date(p.reputation.lastAnalyzedAt) : null,
          },
        });
      }
    }

    if (data.settings) {
      const raw = { ...(data.settings as Record<string, unknown>) };
      delete raw.id;
      const settingsData = raw as Partial<{
        protectionActive: boolean;
        blockThreshold: number;
        warnThreshold: number;
        blockTelemarketing: boolean;
        blockRobocalls: boolean;
        blockCollectors: boolean;
        blockOperatorAds: boolean;
        blockInternational: boolean;
        blockUnknownNumbers: boolean;
        silenceMode: boolean;
        insistenceProtection: boolean;
        waProtect: boolean;
        waBlockUnknown: boolean;
        waBlockLinks: boolean;
        waBlockForwarded: boolean;
        waBlockCodePhish: boolean;
        waBlockGroupInvite: boolean;
        spoofProtection: boolean;
        ownNumber: string;
        ruleVersion: string;
      }>;
      await db.userSettings.upsert({
        where: { id: "default" },
        update: settingsData,
        create: { id: "default", ...settingsData },
      });
    }

    for (const b of data.blockedNumbers ?? []) {
      await db.blockedNumber.create({
        data: { e164: b.e164, label: b.label ?? null, source: b.source ?? "manual" },
      });
    }
    for (const a of data.allowedNumbers ?? []) {
      await db.allowedNumber.create({
        data: { e164: a.e164, name: a.name ?? null, kind: a.kind ?? "contact" },
      });
    }
    for (const s of data.spamPatterns ?? []) {
      await db.spamPattern.create({
        data: {
          kind: s.kind, value: s.value, weight: s.weight, label: s.label ?? null,
          channel: s.channel ?? "both", active: s.active ?? true,
        },
      });
    }
    for (const o of data.operatorRules ?? []) {
      await db.operatorRule.create({
        data: {
          operator: o.operator, sender: o.sender, category: o.category,
          action: o.action ?? "block", active: o.active ?? true,
        },
      });
    }
    for (const r of data.reports ?? []) {
      await db.report.create({
        data: {
          e164: r.e164, category: r.category, description: r.description ?? null,
          status: r.status ?? "pending",
        },
      });
    }
    for (const c of data.callEvents ?? []) {
      await db.callEvent.create({
        data: {
          e164: c.e164,
          phoneNumberId: phoneMap.get(c.e164) ?? null,
          decision: c.decision,
          riskScore: c.riskScore,
          category: c.category ?? null,
          evidences: c.evidences ?? "[]",
          reason: c.reason,
          spoofFlags: c.spoofFlags ?? "[]",
          ruleVersion: c.ruleVersion ?? "v1.0.0",
          occurredAt: c.occurredAt ? new Date(c.occurredAt) : new Date(),
        },
      });
    }
    for (const s of data.smsMessages ?? []) {
      await db.smsMessage.create({
        data: {
          sender: s.sender,
          phoneNumberId: phoneMap.get(s.sender) ?? null,
          content: s.content,
          category: s.category ?? "unknown",
          decision: s.decision,
          riskScore: s.riskScore,
          evidences: s.evidences ?? "[]",
          reason: s.reason,
          occurredAt: s.occurredAt ? new Date(s.occurredAt) : new Date(),
        },
      });
    }
    for (const w of data.whatsappMessages ?? []) {
      await db.whatsAppMessage.create({
        data: {
          sender: w.sender,
          phoneNumberId: phoneMap.get(w.sender) ?? null,
          content: w.content,
          category: w.category ?? "unknown",
          decision: w.decision,
          riskScore: w.riskScore,
          forwardedMany: w.forwardedMany ?? false,
          hasLink: w.hasLink ?? false,
          isGroupInvite: w.isGroupInvite ?? false,
          evidences: w.evidences ?? "[]",
          reason: w.reason,
          occurredAt: w.occurredAt ? new Date(w.occurredAt) : new Date(),
        },
      });
    }

    return NextResponse.json({
      restored: true,
      counts: {
        phoneNumbers: (data.phoneNumbers ?? []).length,
        blockedNumbers: (data.blockedNumbers ?? []).length,
        allowedNumbers: (data.allowedNumbers ?? []).length,
        spamPatterns: (data.spamPatterns ?? []).length,
        operatorRules: (data.operatorRules ?? []).length,
        reports: (data.reports ?? []).length,
        callEvents: (data.callEvents ?? []).length,
        smsMessages: (data.smsMessages ?? []).length,
        whatsappMessages: (data.whatsappMessages ?? []).length,
      },
    });
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : "Falha ao restaurar backup" },
      { status: 500 }
    );
  }
}
