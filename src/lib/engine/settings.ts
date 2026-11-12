import { db } from "@/lib/db";
import { DEFAULT_SETTINGS, type EngineSettings } from "@/lib/engine/types";

/** Loads (or creates) the user settings row. */
export async function getSettings() {
  const settings = await db.userSettings.findUnique({ where: { id: "default" } });
  if (settings) return settings;
  return db.userSettings.create({ data: { id: "default" } });
}

/** Maps DB settings row to the engine settings shape. */
export async function engineSettings(): Promise<EngineSettings> {
  const s = await getSettings();
  return {
    protectionActive: s.protectionActive,
    blockThreshold: s.blockThreshold,
    warnThreshold: s.warnThreshold,
    blockTelemarketing: s.blockTelemarketing,
    blockRobocalls: s.blockRobocalls,
    blockCollectors: s.blockCollectors,
    blockOperatorAds: s.blockOperatorAds,
    blockInternational: s.blockInternational,
    blockUnknownNumbers: s.blockUnknownNumbers,
    silenceMode: s.silenceMode,
    insistenceProtection: s.insistenceProtection,
    waProtect: s.waProtect,
    waBlockUnknown: s.waBlockUnknown,
    waBlockLinks: s.waBlockLinks,
    waBlockForwarded: s.waBlockForwarded,
    waBlockCodePhish: s.waBlockCodePhish,
    waBlockGroupInvite: s.waBlockGroupInvite,
    spoofProtection: s.spoofProtection,
    ownNumber: s.ownNumber ?? "",
    ruleVersion: s.ruleVersion ?? DEFAULT_SETTINGS.ruleVersion,
  };
}
