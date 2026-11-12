/**
 * ORANGE SHIELD - Types shared by the protection engines
 * Based on the product specification: risk score 0-100,
 * decision bands and evidence-based classification.
 */

export type CallDecision = "ALLOW" | "WARN" | "SILENCE" | "BLOCK";
export type SmsDecision = "ALLOW" | "QUARANTINE" | "BLOCK";

export interface EngineEvidence {
  label: string;
  points: number;
}

export interface EngineStep {
  step: string;
  result: string;
}

export interface CallAnalysis {
  e164: string;
  decision: CallDecision;
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
  ruleVersion: string;
}

export interface SmsAnalysis {
  sender: string;
  decision: SmsDecision;
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
}

export interface EngineSettings {
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
  // WhatsApp Guard (Engine 03)
  waProtect: boolean;
  waBlockUnknown: boolean;
  waBlockLinks: boolean;
  waBlockForwarded: boolean;
  waBlockCodePhish: boolean;
  waBlockGroupInvite: boolean;
  // Spoof Detector (Engine 04)
  spoofProtection: boolean;
  ownNumber: string;
  ruleVersion: string;
}

export const DEFAULT_SETTINGS: EngineSettings = {
  protectionActive: true,
  blockThreshold: 70,
  warnThreshold: 30,
  blockTelemarketing: true,
  blockRobocalls: true,
  blockCollectors: true,
  blockOperatorAds: true,
  blockInternational: false,
  blockUnknownNumbers: false,
  silenceMode: false,
  insistenceProtection: true,
  waProtect: true,
  waBlockUnknown: false,
  waBlockLinks: true,
  waBlockForwarded: true,
  waBlockCodePhish: true,
  waBlockGroupInvite: false,
  spoofProtection: true,
  ownNumber: "",
  ruleVersion: "v1.1.0",
};

export function clampScore(score: number): number {
  return Math.max(0, Math.min(100, Math.round(score)));
}

export function riskBand(score: number): string {
  if (score <= 19) return "Confiável";
  if (score <= 39) return "Baixo risco";
  if (score <= 59) return "Suspeito";
  if (score <= 79) return "Alto risco";
  return "Muito alto risco";
}
