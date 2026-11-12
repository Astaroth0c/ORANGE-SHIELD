"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, LookupResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { DecisionBadge, CategoryChip } from "./ui-bits";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "date-fns";
import { ptBR } from "date-fns/locale";
import { Database, Search, ShieldAlert, ShieldCheck } from "lucide-react";

const CLASSIFICATION_LABELS: Record<string, string> = {
  telemarketing: "Telemarketing",
  robocall: "Robocall",
  fraud: "Golpe confirmado",
  collector: "Cobrança automatizada",
  verified_business: "Comercial verificado",
  operator: "Operadora",
  unknown: "Sem classificação",
};

export function Lookup() {
  const [input, setInput] = useState("");
  const [query, setQuery] = useState<string | null>(null);
  const { toast } = useToast();

  const { data, isFetching } = useQuery({
    queryKey: ["lookup", query],
    enabled: !!query,
    queryFn: () => api.get<LookupResponse>(`/api/lookup?number=${encodeURIComponent(query!)}`),
  });

  const run = () => {
    if (!input.trim()) {
      toast({ title: "Digite um número para consultar", variant: "destructive" });
      return;
    }
    setQuery(input.trim());
  };

  return (
    <div className="space-y-4">
      <section className="glass space-y-3 rounded-2xl p-5">
        <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
          <Database className="size-4 text-orange-400" /> Banco de reputação coletiva
        </h2>
        <div className="flex gap-2">
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && run()}
            placeholder="Consultar número: (11) 99999-9999, 0303..., 280..."
            className="font-mono"
            aria-label="Número para consulta"
          />
          <Button
            onClick={run}
            disabled={isFetching}
            className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
          >
            <Search className="size-4" />
            Consultar
          </Button>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {["+5511912340001", "+551138581111", "0303 111 00111", "+5511991234567"].map((n) => (
            <button
              key={n}
              onClick={() => {
                setInput(n);
                setQuery(n);
              }}
              className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 font-mono text-[11px] text-zinc-300 transition-colors hover:border-orange-500/40 hover:text-orange-300"
            >
              {n}
            </button>
          ))}
        </div>
      </section>

      {isFetching && (
        <section className="glass space-y-3 rounded-2xl p-5">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-16 w-full" />
        </section>
      )}

      {data && !isFetching && (
        <section className="glass space-y-4 rounded-2xl p-5" aria-live="polite">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="font-mono text-lg font-bold">{data.formatted}</p>
              {data.found ? (
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-zinc-400">
                  {data.listStatus && (
                    <span
                      className={cn(
                        "rounded-full border px-2 py-0.5 font-semibold",
                        data.listStatus === "allowed"
                          ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
                          : "border-red-500/30 bg-red-500/10 text-red-400"
                      )}
                    >
                      {data.listStatus === "allowed" ? "Na lista autorizada" : "Na lista negra"}
                    </span>
                  )}
                  {data.listLabel && <span>{data.listLabel}</span>}
                  {data.operator && <span>· Operadora {data.operator}</span>}
                  {data.verified && <span>· Verificado</span>}
                </div>
              ) : (
                <p className="mt-1 text-xs text-zinc-500">{data.message}</p>
              )}
            </div>
            {data.reputation && (
              <div
                className={cn(
                  "flex items-center gap-3 rounded-xl border px-4 py-2",
                  data.reputation.riskScore >= 70
                    ? "border-red-500/30 bg-red-500/10"
                    : data.reputation.riskScore >= 30
                      ? "border-amber-500/30 bg-amber-500/10"
                      : "border-emerald-500/30 bg-emerald-500/10"
                )}
              >
                {data.reputation.riskScore >= 70 ? (
                  <ShieldAlert className="size-6 text-red-400" />
                ) : (
                  <ShieldCheck className="size-6 text-emerald-400" />
                )}
                <div>
                  <p className="font-mono text-2xl font-bold leading-none">{data.reputation.riskScore}</p>
                  <p className="text-[10px] uppercase tracking-wider text-zinc-400">
                    risco · {data.reputation.band}
                  </p>
                </div>
              </div>
            )}
          </div>

          {data.reputation && (
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-white/8 bg-white/3 p-3">
                <p className="text-[10px] uppercase tracking-wider text-zinc-500">Classificação</p>
                <p className="mt-1 text-sm font-medium">
                  {CLASSIFICATION_LABELS[data.reputation.classification] ?? data.reputation.classification}
                </p>
              </div>
              <div className="rounded-lg border border-white/8 bg-white/3 p-3">
                <p className="text-[10px] uppercase tracking-wider text-zinc-500">Denúncias coletivas</p>
                <p className="mt-1 font-mono text-sm font-medium">{data.reputation.reportCount}</p>
              </div>
              <div className="rounded-lg border border-white/8 bg-white/3 p-3">
                <p className="text-[10px] uppercase tracking-wider text-zinc-500">Confiança da base</p>
                <p className="mt-1 font-mono text-sm font-medium">
                  {Math.round(data.reputation.confidence * 100)}%
                </p>
              </div>
            </div>
          )}

          {data.recentCalls && data.recentCalls.length > 0 && (
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Últimas triagens registradas
              </p>
              <div className="custom-scrollbar max-h-44 space-y-1.5 overflow-y-auto pr-1">
                {data.recentCalls.map((c, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between gap-3 rounded-lg border border-white/6 bg-white/3 px-3 py-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-xs text-zinc-300">{c.reason}</p>
                      <p className="text-[10px] text-zinc-500">
                        {formatDistanceToNow(new Date(c.occurredAt), { addSuffix: true, locale: ptBR })} ·
                        risco {c.riskScore}
                      </p>
                    </div>
                    <DecisionBadge decision={c.decision} />
                  </div>
                ))}
              </div>
            </div>
          )}

          {data.recentReports && data.recentReports.length > 0 && (
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Denúncias da comunidade
              </p>
              <div className="space-y-1.5">
                {data.recentReports.map((r, i) => (
                  <div key={i} className="flex items-center gap-2 rounded-lg border border-white/6 bg-white/3 px-3 py-2 text-xs">
                    <CategoryChip category={r.category} />
                    <span className="min-w-0 flex-1 truncate text-zinc-300">{r.description ?? "—"}</span>
                    <span
                      className={cn(
                        "shrink-0 text-[10px] font-semibold uppercase",
                        r.status === "approved" ? "text-emerald-400" : "text-amber-400"
                      )}
                    >
                      {r.status === "approved" ? "aprovada" : r.status === "rejected" ? "rejeitada" : "pendente"}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
