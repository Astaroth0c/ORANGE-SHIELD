"use client";

import { useState } from "react";
import {
  useCallHistory,
  useClearHistory,
  useClearWhatsAppHistory,
  useSmsHistory,
  useWhatsAppHistory,
} from "@/hooks/use-orange-data";
import { DecisionBadge, CategoryChip, EvidenceList } from "./ui-bits";
import { formatPhone } from "@/lib/engine/normalize";
import { formatDistanceToNow } from "date-fns";
import { ptBR } from "date-fns/locale";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { AlertTriangle, ChevronDown, Inbox, MessageCircle, MessageSquare, PhoneCall, Trash2 } from "lucide-react";

const FILTERS = [
  { id: "ALL", label: "Todas" },
  { id: "BLOCK", label: "Bloqueadas" },
  { id: "WARN", label: "Suspeitas" },
  { id: "ALLOW", label: "Permitidas" },
];

export function History() {
  const [kind, setKind] = useState<"calls" | "sms" | "whatsapp">("calls");
  const [filter, setFilter] = useState("ALL");
  const [expanded, setExpanded] = useState<string | null>(null);

  const calls = useCallHistory(kind === "calls" ? filter : "ALL");
  const sms = useSmsHistory(kind === "sms" ? filter : "ALL");
  const wa = useWhatsAppHistory(kind === "whatsapp" ? filter : "ALL");
  const clearStandard = useClearHistory(kind === "whatsapp" ? "calls" : kind);
  const clearWa = useClearWhatsAppHistory();
  const clear = kind === "whatsapp" ? clearWa : clearStandard;

  const loading =
    kind === "calls" ? calls.isLoading : kind === "sms" ? sms.isLoading : wa.isLoading;
  const data = kind === "calls" ? calls.data : kind === "sms" ? sms.data : wa.data;

  // QUARANTINE (sms/whatsapp) also shows in the "suspeitas" filter
  const softFilter = (d: string) =>
    filter === "ALL" ||
    d === filter ||
    (filter === "WARN" && d === "QUARANTINE");

  const items =
    kind === "calls"
      ? (calls.data ?? []).filter((c) => filter === "ALL" || c.decision === filter)
      : kind === "sms"
        ? (sms.data ?? []).filter((s) => softFilter(s.decision))
        : (wa.data ?? []).filter((w) => softFilter(w.decision));

  return (
    <div className="space-y-4">
      <section className="glass flex flex-wrap items-center justify-between gap-3 rounded-2xl p-4">
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex rounded-xl border border-white/10 bg-white/4 p-1" role="tablist" aria-label="Tipo de histórico">
            {(
              [
                { id: "calls", label: "Chamadas", icon: <PhoneCall className="size-4" /> },
                { id: "sms", label: "SMS", icon: <MessageSquare className="size-4" /> },
                { id: "whatsapp", label: "WhatsApp", icon: <MessageCircle className="size-4" /> },
              ] as const
            ).map((k) => (
              <button
                key={k.id}
                role="tab"
                aria-selected={kind === k.id}
                onClick={() => setKind(k.id)}
                className={cn(
                  "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                  kind === k.id ? "bg-orange-500 text-white" : "text-zinc-400 hover:text-foreground"
                )}
              >
                {k.icon}
                {k.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filtro por decisão">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                onClick={() => setFilter(f.id)}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                  filter === f.id
                    ? "border-orange-500/50 bg-orange-500/15 text-orange-300"
                    : "border-white/10 bg-white/4 text-zinc-400 hover:text-foreground"
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => clear.mutate()}
          disabled={clear.isPending || (data?.length ?? 0) === 0}
          className="gap-2 border-white/12 hover:border-red-500/40 hover:text-red-300"
        >
          <Trash2 className="size-3.5" />
          Limpar
        </Button>
      </section>

      {loading ? (
        <div className="space-y-2">
          {[...Array(5)].map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <section className="glass flex flex-col items-center rounded-2xl p-12 text-center text-zinc-500">
          <Inbox className="mb-3 size-10 text-zinc-600" />
          <p className="text-sm">Nenhum registro para este filtro</p>
          <p className="text-xs">Simule chamadas, SMS ou mensagens para gerar histórico</p>
        </section>
      ) : (
        <div className="custom-scrollbar max-h-[60vh] space-y-2 overflow-y-auto pr-1">
          {items.map((item: Record<string, unknown>) => {
            const id = String(item.id);
            const decision = String(item.decision);
            const score = Number(item.riskScore);
            const reason = String(item.reason);
            const when = formatDistanceToNow(new Date(String(item.occurredAt)), {
              addSuffix: true,
              locale: ptBR,
            });
            const evidences = (item.evidences ?? []) as { label: string; points: number }[];
            const spoofFlags = (item.spoofFlags ?? []) as { code: string; label: string; points: number }[];
            const isOpen = expanded === id;

            const title =
              kind === "calls"
                ? formatPhone(String(item.e164))
                : String(item.sender);
            const content = kind !== "calls" ? String(item.content) : null;
            const category = (item.category as string) ?? null;

            return (
              <article
                key={id}
                className="glass overflow-hidden rounded-xl transition-colors"
                aria-label={`Registro ${title}`}
              >
                <button
                  className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-white/3"
                  onClick={() => setExpanded(isOpen ? null : id)}
                  aria-expanded={isOpen}
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-semibold">{title}</span>
                      <CategoryChip category={category} />
                      {spoofFlags.length > 0 && (
                        <span className="inline-flex items-center gap-1 rounded border border-red-500/30 bg-red-500/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-red-400">
                          <AlertTriangle className="size-3" />
                          spoofing ×{spoofFlags.length}
                        </span>
                      )}
                      <span className="rounded bg-white/6 px-1.5 py-0.5 font-mono text-[10px] text-zinc-400">
                        {score}/100
                      </span>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-zinc-500">{reason}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <div className="flex flex-col items-end gap-1">
                      <DecisionBadge decision={decision} />
                      <span className="text-[10px] text-zinc-500">{when}</span>
                    </div>
                    <ChevronDown
                      className={cn("size-4 text-zinc-500 transition-transform", isOpen && "rotate-180")}
                    />
                  </div>
                </button>
                {isOpen && (
                  <div className="space-y-3 border-t border-white/6 px-4 py-3">
                    {content && (
                      <div className="rounded-lg border border-white/8 bg-black/30 p-3">
                        <p className="mb-1 text-[10px] uppercase tracking-wider text-zinc-500">Conteúdo</p>
                        <p className="text-sm text-zinc-300">{content}</p>
                      </div>
                    )}
                    {spoofFlags.length > 0 && (
                      <div className="space-y-1">
                        <p className="text-[10px] uppercase tracking-wider text-zinc-500">
                          Flags de spoofing detectadas no caller ID
                        </p>
                        {spoofFlags.map((f, i) => (
                          <div
                            key={i}
                            className="flex items-center justify-between gap-3 rounded-lg border border-red-500/25 bg-red-500/8 px-3 py-2 text-xs"
                          >
                            <span className="text-red-300">{f.label}</span>
                            <span className="shrink-0 rounded bg-red-500/15 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-red-300">
                              +{f.points}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                    <EvidenceList evidences={evidences} />
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
