/**
 * ORANGE SHIELD — API client types & fetch helpers
 */

export interface StatsResponse {
  blockedCalls: number;
  blockedSms: number;
  fraudCalls: number;
  quarantinedSms: number;
  blockedCalls24h: number;
  blockedSms24h: number;
  allowedCalls24h: number;
  totalNumbers: number;
  knownNumbers: number;
  pendingReports: number;
  blockedWa: number;
  quarantinedWa: number;
  spoofEvents: number;
  spoofBlocked: number;
  days: { day: string; blocked: number; allowed: number }[];
  callsByCategory: { category: string; count: number }[];
  smsByCategory: { category: string; count: number }[];
  lastEventAt: string | null;
  // Engine intelligence (v0.3.0)
  decisionDistribution: { ALLOW: number; WARN: number; SILENCE: number; BLOCK: number };
  riskBands: { safe: number; low: number; suspect: number; high: number; critical: number };
  topThreats: { e164: string; hits: number; blocked: number; lastSeen: string }[];
  channelAttacks: { calls: number; sms: number; whatsapp: number };
  insights: { icon: string; text: string }[];
}

export interface SettingsResponse {
  id: string;
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
}

export interface EngineEvidence {
  label: string;
  points: number;
}

export interface EngineStep {
  step: string;
  result: string;
}

export interface CallAnalysisResponse {
  e164: string;
  decision: "ALLOW" | "WARN" | "SILENCE" | "BLOCK";
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
  ruleVersion: string;
}

export interface SmsAnalysisResponse {
  sender: string;
  decision: "ALLOW" | "QUARANTINE" | "BLOCK";
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
}

export interface SpoofCheckResponse {
  code: string;
  label: string;
  detail: string;
  points: number;
  status: "pass" | "flagged" | "info";
}

export interface WhatsAppAnalysisResponse {
  sender: string;
  decision: "ALLOW" | "QUARANTINE" | "BLOCK";
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  steps: EngineStep[];
  reason: string;
  spoofChecks: SpoofCheckResponse[];
}

export interface WhatsAppEventResponse {
  id: string;
  sender: string;
  content: string;
  decision: "ALLOW" | "QUARANTINE" | "BLOCK";
  riskScore: number;
  category: string;
  forwardedMany: boolean;
  hasLink: boolean;
  isGroupInvite: boolean;
  evidences: EngineEvidence[];
  reason: string;
  occurredAt: string;
  classification: string | null;
}

export interface SpoofAnalysisResponse {
  e164: string;
  formatted: string;
  ownNumberConfigured: boolean;
  checks: SpoofCheckResponse[];
  spoofScore: number;
  verdict: "clean" | "suspicious" | "spoof_likely";
  verdictLabel: string;
  recentEvents: {
    decision: string;
    riskScore: number;
    reason: string;
    occurredAt: string;
    spoofFlags: { code: string; label: string; points: number }[];
  }[];
}

export interface SpoofHistoryResponse {
  totalFlagged: number;
  totalBlocked: number;
  highestRisk: number;
  distribution: { code: string; label: string; count: number; points: number }[];
  recent: {
    e164: string;
    formatted: string;
    decision: string;
    riskScore: number;
    flags: { code: string; label: string; points: number }[];
    occurredAt: string;
  }[];
}

export interface CallEventResponse {
  id: string;
  e164: string;
  decision: "ALLOW" | "WARN" | "SILENCE" | "BLOCK";
  riskScore: number;
  category: string | null;
  evidences: EngineEvidence[];
  spoofFlags: { code: string; label: string; points: number }[];
  reason: string;
  ruleVersion: string;
  occurredAt: string;
  operator: string | null;
  classification: string | null;
}

export interface SmsEventResponse {
  id: string;
  sender: string;
  content: string;
  decision: "ALLOW" | "QUARANTINE" | "BLOCK";
  riskScore: number;
  category: string;
  evidences: EngineEvidence[];
  reason: string;
  occurredAt: string;
  classification: string | null;
}

export interface BlockedNumberResponse {
  id: string;
  e164: string;
  label: string | null;
  source: string;
  createdAt: string;
}

export interface AllowedNumberResponse {
  id: string;
  e164: string;
  name: string | null;
  kind: string;
  createdAt: string;
}

export interface PatternResponse {
  id: string;
  kind: string;
  value: string;
  weight: number;
  label: string | null;
  channel: string;
  active: boolean;
}

export interface OperatorRuleResponse {
  id: string;
  operator: string;
  sender: string;
  category: string;
  action: string;
  active: boolean;
}

export interface ReportResponse {
  id: string;
  e164: string;
  category: string;
  description: string | null;
  status: string;
  createdAt: string;
}

export interface LookupResponse {
  e164: string;
  formatted: string;
  found: boolean;
  listStatus: "allowed" | "blocked" | null;
  listLabel: string | null;
  numberType: string | null;
  operator: string | null;
  verified: boolean;
  message?: string;
  reputation?: {
    riskScore: number;
    classification: string;
    reportCount: number;
    confidence: number;
    isConfirmedFraud: boolean;
    band: string;
    lastAnalyzedAt: string | null;
  } | null;
  recentReports?: { category: string; description: string | null; status: string; createdAt: string }[];
  recentCalls?: { decision: string; riskScore: number; reason: string; occurredAt: string }[];
}

async function jsonFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data as { error?: string }).error ?? `Erro ${res.status}`);
  }
  return data as T;
}

export const api = {
  get: <T,>(url: string) => jsonFetch<T>(url),
  post: <T,>(url: string, body: unknown) =>
    jsonFetch<T>(url, { method: "POST", body: JSON.stringify(body) }),
  patch: <T,>(url: string, body: unknown) =>
    jsonFetch<T>(url, { method: "PATCH", body: JSON.stringify(body) }),
  del: <T,>(url: string) => jsonFetch<T>(url, { method: "DELETE" }),
};
