"use client";

import { useState } from "react";
import {
  usePatchSettings,
  useSettings,
  useSpoofAnalysis,
} from "@/hooks/use-orange-data";
import { SpoofAnalysisResponse, SpoofCheckResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { api } from "@/lib/api-client";
import { formatDistanceToNow } from "date-fns";
import { ptBR } from "date-fns/locale";
import {
  AlertTriangle,
  CheckCircle2,
  Fingerprint,
  Info,
  Loader2,
  Radar,
  ScanSearch,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  Zap,
} from "lucide-react";

function CheckRow({ check }: { check: SpoofCheckResponse }) {
  const meta =
    check.status === "flagged"
      ? { icon: <AlertTriangle className="size-4" />, cls: "text-red-400 border-red-500/25 bg-red-500/8" }
      : check.status === "pass"
        ? { icon: <CheckCircle2 className="size-4" />, cls: "text-emerald-400 border-emerald-500/25 bg-emerald-500/8" }
        : { icon: <Info className="size-4" />, cls: "text-zinc-400 border-white/10 bg-white/4" };
  return (
    <div className={cn("flex items-start gap-3 rounded-xl border px-3.5 py-3", meta.cls)}>
      <span className="mt-0.5 shrink-0">{meta.icon}</span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium">{check.label}</p>
          {check.status === "flagged" && (
            <span className="rounded bg-red-500/15 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-red-300">
              +{check.points}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs leading-relaxed text-zinc-500">{check.detail}</p>
      </div>
    </div>
  );
}

export function SpoofDetector() {
  const [input, setInput] = useState("");
  const [query, setQuery] = useState<{ n: string; ts: number } | null>(null);
  const [ownInput, setOwnInput] = useState<string | null>(null);
  const [wangiriRunning, setWangiriRunning] = useState(false);
  const { toast } = useToast();

  const { data: settings } = useSettings();
  const patch = usePatchSettings();
  const { data, isFetching } = useSpoofAnalysis(query?.n ?? null, query?.ts);

  const ownNumber = settings?.ownNumber ?? "";

  const run = (value: string) => {
    if (!value.trim()) {
      toast({ title: "Digite um número para analisar", variant: "destructive" });
      return;
    }
    setQuery({ n: value.trim(), ts: Date.now() });
  };

  const saveOwn = () => {
    const digits = (ownInput ?? "").replace(/\D/g, "");
    patch.mutate(
      { ownNumber: digits },
      {
        onSuccess: () =>
          toast({
            title: digits ? "Seu número foi salvo" : "Seu número foi removido",
            description: digits
              ? "Neighbor spoofing agora compara o caller ID com o seu número"
              : "Neighbor spoofing ficará sem comparação",
          }),
        onError: (e) =>
          toast({ title: "Erro ao salvar", description: e.message, variant: "destructive" }),
      }
    );
  };

  /** Builds a neighbor-spoof variant of the user's own number (last digit changed). */
  const buildNeighborVariant = (): string => {
    if (!ownNumber) return "+55 11 98765-4322";
    const d = ownNumber.replace(/\D/g, "");
    if (d.length < 6) return "+55 11 98765-4322";
    const head = d.slice(0, -1);
    const last = d[d.length - 1];
    const swapped = last === "2" ? "3" : "2";
    return `+${head}${swapped}`;
  };

  /** Simulates 3 rapid calls (Wangiri pattern) then runs the analysis. */
  const runWangiri = async () => {
    const target = input.trim() || "+55 11 97070-7070";
    setWangiriRunning(true);
    try {
      for (let i = 0; i < 3; i++) {
        await api.post("/api/engine/call", { number: target });
      }
      setInput(target);
      setQuery({ n: target.trim(), ts: Date.now() });
      toast({
        title: "3 chamadas rápidas simuladas",
        description: "O padrão Wangiri foi registrado — veja o resultado da análise",
      });
    } catch (e) {
      toast({
        title: "Erro ao simular Wangiri",
        description: e instanceof Error ? e.message : "tente novamente",
        variant: "destructive",
      });
    } finally {
      setWangiriRunning(false);
    }
  };

  const verdictMeta = (verdict: SpoofAnalysisResponse["verdict"]) =>
    verdict === "spoof_likely"
      ? { icon: <ShieldAlert className="size-5" />, cls: "border-red-500/30 bg-red-500/10 text-red-300" }
      : verdict === "suspicious"
        ? { icon: <AlertTriangle className="size-5" />, cls: "border-amber-500/30 bg-amber-500/10 text-amber-300" }
        : { icon: <ShieldCheck className="size-5" />, cls: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300" };

  const PRESETS = [
    { label: "Vizinho falso (imita seu número)", value: buildNeighborVariant() },
    { label: "DDD inexistente (39)", value: "+55 39 99999-9999" },
    { label: "Celular sem 9º dígito", value: "+551191234567" },
    { label: "Dígitos repetidos", value: "+5511977777777" },
    { label: "Sequência artificial", value: "+5511912345678" },
  ];

  return (
    <div className="space-y-5">
      {/* Own number config */}
      <section className="glass space-y-3 rounded-2xl p-5" aria-label="Configuração anti-spoofing">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
            <Smartphone className="size-4 text-orange-400" /> Seu número (referência anti-spoofing)
          </h2>
          <div className="flex items-center gap-2">
            <span className="text-xs text-zinc-400">Detector ativo</span>
            <button
              type="button"
              role="switch"
              aria-checked={settings?.spoofProtection ?? false}
              disabled={patch.isPending}
              onClick={() => patch.mutate({ spoofProtection: !settings?.spoofProtection })}
              className={cn(
                "relative h-5 w-9 rounded-full transition-colors disabled:opacity-50",
                settings?.spoofProtection ? "bg-orange-500" : "bg-white/15"
              )}
            >
              <span
                className={cn(
                  "absolute top-0.5 size-4 rounded-full bg-white transition-all",
                  settings?.spoofProtection ? "left-4.5" : "left-0.5"
                )}
              />
            </button>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Input
            value={ownInput ?? (ownNumber ? `+${ownNumber}` : "")}
            onChange={(e) => setOwnInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && saveOwn()}
            placeholder="Ex.: +55 11 98765-4321"
            className="max-w-xs font-mono"
            aria-label="Seu número de telefone"
          />
          <Button
            onClick={saveOwn}
            disabled={patch.isPending}
            variant="outline"
            className="border-white/12 hover:border-orange-500/40 hover:text-orange-300"
          >
            Salvar
          </Button>
        </div>
        <p className="text-[11px] leading-relaxed text-zinc-500">
          Com o seu número salvo, o motor detecta <strong className="text-zinc-300">neighbor spoofing</strong>{" "}
          (chamadas que imitam seu número — mesmo DDD + dígitos iniciais ou final idêntico). Sem
          preencher, essa checagem fica neutra e as demais continuam ativas.
        </p>
      </section>

      {/* Analyzer */}
      <section className="glass space-y-3 rounded-2xl p-5" aria-label="Analisador de spoofing">
        <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
          <ScanSearch className="size-4 text-orange-400" /> Analisar caller ID suspeito
        </h2>
        <div className="flex gap-2">
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && run(input)}
            placeholder="(11) 99999-9999 ou número internacional..."
            className="font-mono"
            aria-label="Número para análise de spoofing"
          />
          <Button
            onClick={() => run(input)}
            disabled={isFetching}
            className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
          >
            {isFetching ? <Loader2 className="size-4 animate-spin" /> : <Radar className="size-4" />}
            Analisar
          </Button>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => (
            <button
              key={p.label}
              onClick={() => {
                setInput(p.value);
                run(p.value);
              }}
              className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-zinc-300 transition-colors hover:border-orange-500/40 hover:text-orange-300"
            >
              {p.label}
            </button>
          ))}
          <button
            onClick={runWangiri}
            disabled={wangiriRunning}
            className="flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-500/10 px-2.5 py-1 text-[11px] font-medium text-amber-300 transition-colors hover:bg-amber-500/15 disabled:opacity-60"
          >
            {wangiriRunning ? <Loader2 className="size-3 animate-spin" /> : <Zap className="size-3" />}
            Simular Wangiri (3 toques)
          </button>
        </div>
      </section>

      {/* Result */}
      {isFetching && !data && (
        <section className="glass space-y-3 rounded-2xl p-5">
          <Skeleton className="h-6 w-48" />
          {[...Array(4)].map((_, i) => (
            <Skeleton key={i} className="h-14 w-full rounded-xl" />
          ))}
        </section>
      )}

      {data && !isFetching && (
        <section className="space-y-4" aria-live="polite">
          <div className="glass flex flex-wrap items-center justify-between gap-3 rounded-2xl p-5">
            <div>
              <p className="font-mono text-lg font-bold">{data.formatted}</p>
              <p className="text-xs text-zinc-500">
                {data.ownNumberConfigured
                  ? "Comparação com seu número ativa"
                  : "Neighbor spoofing sem seu número configurado"}
              </p>
            </div>
            <div
              className={cn(
                "flex items-center gap-3 rounded-xl border px-4 py-2",
                verdictMeta(data.verdict).cls
              )}
            >
              {verdictMeta(data.verdict).icon}
              <div>
                <p className="font-mono text-2xl font-bold leading-none">{data.spoofScore}</p>
                <p className="text-[10px] uppercase tracking-wider">spoof score · {data.verdictLabel}</p>
              </div>
            </div>
          </div>

          <div className="space-y-2">
            {data.checks.map((c) => (
              <CheckRow key={c.code} check={c} />
            ))}
          </div>

          {data.recentEvents.length > 0 && (
            <div className="glass rounded-2xl p-5">
              <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                Últimas triagens deste número
              </p>
              <div className="custom-scrollbar max-h-40 space-y-1.5 overflow-y-auto pr-1">
                {data.recentEvents.map((e, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between gap-3 rounded-lg border border-white/6 bg-white/3 px-3 py-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-xs text-zinc-300">{e.reason}</p>
                      <p className="text-[10px] text-zinc-500">
                        {formatDistanceToNow(new Date(e.occurredAt), { addSuffix: true, locale: ptBR })}{" "}
                        · risco {e.riskScore}
                      </p>
                    </div>
                    <span className="shrink-0 rounded bg-white/6 px-1.5 py-0.5 font-mono text-[10px] text-zinc-400">
                      {e.decision}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {/* Education */}
      <section className="glass rounded-2xl p-5" aria-label="Como o detector funciona">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
          <Fingerprint className="size-4 text-orange-400" /> Técnicas de spoofing detectadas
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div className="rounded-xl border border-white/8 bg-white/3 p-3.5">
            <p className="text-sm font-medium text-zinc-200">Neighbor spoofing (+35)</p>
            <p className="mt-1 text-xs leading-relaxed text-zinc-500">
              O golpe liga &quot;de você mesmo&quot;: imita seu DDD e os primeiros dígitos (ou o
              final do seu número) para ganhar confiança. Comum em falsas operadoras e bancos.
            </p>
          </div>
          <div className="rounded-xl border border-white/8 bg-white/3 p-3.5">
            <p className="text-sm font-medium text-zinc-200">DDD/len inválidos (+30/+25)</p>
            <p className="mt-1 text-xs leading-relaxed text-zinc-500">
              Caller ID com DDD que não existe na ANATEL ou celular sem o 9º dígito — números
              fisicamente impossíveis, gerados por software de spoofing.
            </p>
          </div>
          <div className="rounded-xl border border-white/8 bg-white/3 p-3.5">
            <p className="text-sm font-medium text-zinc-200">Wangiri / rediscagem (+40)</p>
            <p className="mt-1 text-xs leading-relaxed text-zinc-500">
              1 toque e desliga, várias vezes: ao retornar, você cai em tarifa premium. O detector
              cruza rediscagens rápidas com o histórico de triagens.
            </p>
          </div>
        </div>
        <p className="mt-3 rounded-lg border border-white/8 bg-white/3 px-3 py-2 text-[11px] leading-relaxed text-zinc-500">
          As evidências de spoofing entram direto na pontuação do SmartCall Engine: spoof score ≥ 55
          sinaliza <strong className="text-zinc-300">spoofing provável</strong>, e combinado com
          insistência ou reputação o número é bloqueado automaticamente.
        </p>
      </section>
    </div>
  );
}
