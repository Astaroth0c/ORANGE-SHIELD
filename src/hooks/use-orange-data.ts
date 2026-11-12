"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type SmsAnalysisResponse, type CallAnalysisResponse } from "@/lib/api-client";

export const queryKeys = {
  stats: ["stats"] as const,
  settings: ["settings"] as const,
  callHistory: (filter: string) => ["history", "calls", filter] as const,
  smsHistory: (filter: string) => ["history", "sms", filter] as const,
  waHistory: (filter: string) => ["history", "whatsapp", filter] as const,
  spoof: (query: string) => ["spoof", query] as const,
  spoofHistory: ["spoof-history"] as const,
  blacklist: ["blacklist"] as const,
  whitelist: ["whitelist"] as const,
  patterns: ["patterns"] as const,
  operators: ["operators"] as const,
  reports: ["reports"] as const,
};

export function useStats() {
  return useQuery({
    queryKey: queryKeys.stats,
    queryFn: () => api.get<import("@/lib/api-client").StatsResponse>("/api/stats"),
    refetchInterval: 30_000,
  });
}

export function useSettings() {
  return useQuery({
    queryKey: queryKeys.settings,
    queryFn: () => api.get<import("@/lib/api-client").SettingsResponse>("/api/settings"),
  });
}

export function usePatchSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<import("@/lib/api-client").SettingsResponse>) =>
      api.patch<import("@/lib/api-client").SettingsResponse>("/api/settings", data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.settings });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useCallHistory(filter: string) {
  return useQuery({
    queryKey: queryKeys.callHistory(filter),
    queryFn: () =>
      api.get<import("@/lib/api-client").CallEventResponse[]>(
        `/api/history/calls${filter !== "ALL" ? `?decision=${filter}` : ""}`
      ),
  });
}

export function useSmsHistory(filter: string) {
  return useQuery({
    queryKey: queryKeys.smsHistory(filter),
    queryFn: () =>
      api.get<import("@/lib/api-client").SmsEventResponse[]>(
        `/api/history/sms${filter !== "ALL" ? `?decision=${filter}` : ""}`
      ),
  });
}

export function useClearHistory(kind: "calls" | "sms") {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.del(`/api/history/${kind}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useBlacklist() {
  return useQuery({
    queryKey: queryKeys.blacklist,
    queryFn: () => api.get<import("@/lib/api-client").BlockedNumberResponse[]>("/api/blacklist"),
  });
}

export function useAddBlacklist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { number: string; label?: string }) =>
      api.post("/api/blacklist", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.blacklist }),
  });
}

export function useDeleteBlacklist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.del(`/api/blacklist?id=${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.blacklist }),
  });
}

export function useWhitelist() {
  return useQuery({
    queryKey: queryKeys.whitelist,
    queryFn: () => api.get<import("@/lib/api-client").AllowedNumberResponse[]>("/api/whitelist"),
  });
}

export function useAddWhitelist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { number: string; name?: string; kind?: string }) =>
      api.post("/api/whitelist", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.whitelist }),
  });
}

export function useDeleteWhitelist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.del(`/api/whitelist?id=${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.whitelist }),
  });
}

export function usePatterns() {
  return useQuery({
    queryKey: queryKeys.patterns,
    queryFn: () => api.get<import("@/lib/api-client").PatternResponse[]>("/api/patterns"),
  });
}

export function useAddPattern() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { kind: string; value: string; weight: number; label?: string; channel: string }) =>
      api.post("/api/patterns", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.patterns }),
  });
}

export function useDeletePattern() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.del(`/api/patterns?id=${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.patterns }),
  });
}

export function useOperators() {
  return useQuery({
    queryKey: queryKeys.operators,
    queryFn: () => api.get<import("@/lib/api-client").OperatorRuleResponse[]>("/api/operators"),
  });
}

export function useUpdateOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { id: string; action?: string; active?: boolean }) =>
      api.patch("/api/operators", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.operators }),
  });
}

export function useAddOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { operator: string; sender: string; category: string; action: string }) =>
      api.post("/api/operators", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.operators }),
  });
}

export function useDeleteOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.del(`/api/operators?id=${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.operators }),
  });
}

export function useReports() {
  return useQuery({
    queryKey: queryKeys.reports,
    queryFn: () => api.get<import("@/lib/api-client").ReportResponse[]>("/api/reports"),
  });
}

export function useSubmitReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { number: string; category: string; description?: string; autoApprove?: boolean }) =>
      api.post("/api/reports", data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.reports });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useSimulateCall() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (number: string) =>
      api.post<CallAnalysisResponse>("/api/engine/call", { number }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useSimulateSms() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { sender: string; content: string }) =>
      api.post<SmsAnalysisResponse>("/api/engine/sms", data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useSimulateWhatsApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      sender: string;
      content: string;
      forwardedMany?: boolean;
      isGroupInvite?: boolean;
    }) => api.post<import("@/lib/api-client").WhatsAppAnalysisResponse>("/api/engine/whatsapp", data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useWhatsAppHistory(filter: string) {
  return useQuery({
    queryKey: queryKeys.waHistory(filter),
    queryFn: () =>
      api.get<import("@/lib/api-client").WhatsAppEventResponse[]>(
        `/api/history/whatsapp${filter !== "ALL" ? `?decision=${filter}` : ""}`
      ),
  });
}

export function useClearWhatsAppHistory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.del("/api/history/whatsapp"),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: queryKeys.stats });
    },
  });
}

export function useSpoofAnalysis(query: string | null, nonce?: number) {
  return useQuery({
    queryKey: [...queryKeys.spoof(query ?? ""), nonce ?? 0],
    enabled: !!query,
    queryFn: () =>
      api.get<import("@/lib/api-client").SpoofAnalysisResponse>(
        `/api/spoof?number=${encodeURIComponent(query!)}`
      ),
  });
}

export function useSpoofHistory() {
  return useQuery({
    queryKey: queryKeys.spoofHistory,
    queryFn: () => api.get<import("@/lib/api-client").SpoofHistoryResponse>("/api/spoof/history"),
  });
}
