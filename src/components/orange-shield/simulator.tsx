"use client";

import { useState } from "react";
import { useSimulateCall, useSimulateSms, useSettings } from "@/hooks/use-orange-data";
import { CallAnalysisResponse, SmsAnalysisResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { DecisionBadge, EvidenceList, RiskGauge } from "./ui-bits";
import { formatPhone } from "@/lib/engine/normalize";
import { cn } from "@/lib/utils";
import {
  MessageSquare,
  PhoneCall,
  Radar,
  Send,
  Smartphone,
  Zap,
} from "lucide-react";

const CALL_SCENARIOS = [
  { label: "Telemarketing 0303", number: "0303 111 00111" },
  { label: "Golpe confirmado", number: "+55 11 91234-0001" },
  { label: "Contato autorizado", number: "(11) 98765-4321" },
  { label: "Robocall conhecido", number: "+55 11 99991-2345" },
  { label: "Comercial verificado", number: "+55 11 3033-1234" },
  { label: "Número legítimo novo", number: "+55 11 99123-4567" },
];

const SMS_SCENARIOS = [
  {
    label: "Oferta da operadora",
    sender: "280",
    content: "Vivo: Aproveite! Pacote de internet ilimitado com 50% de desconto. Responda SIM para contratar agora.",
  },
  {
    label: "Código de autenticação",
    sender: "244",
    content: "TIM: Seu código de verificação é 843291. Não compartilhe com ninguém.",
  },
  {
    label: "Golpe de PIX",
    sender: "+55 11 91234-0001",
    content: "Seu PIX será bloqueado! Clique no link urgentemente para atualizar o cadastro: http://pix-seguro.xyz",
  },
  {
    label: "Cobrança em quarentena",
    sender: "331",
    content: "Vivo: Sua fatura vence amanhã. Consulte o boleto no app.",
  },
  {
    label: "Mensagem pessoal",
    sender: "+55 11 97654-3210",
    content: "Carlos: reunião mudou para 15h, pode confirmar?",
  },
];

export function Simulator() {
  const [mode, setMode] = useState<"call" | "sms">("call");
  const [number, setNumber] = useState("");
  const [sender, setSender] = useState("");
  const [content, setContent] = useState("");
  const [callResult, setCallResult] = useState<CallAnalysisResponse | null>(null);
  const [smsResult, setSmsResult] = useState<SmsAnalysisResponse | null>(null);
  const { toast } = useToast();

  const simulateCall = useSimulateCall();
  const simulateSms = useSimulateSms();
  const { data: settings } = useSettings();

  const blockThreshold = settings?.blockThreshold ?? 70;
  const warnThreshold = settings?.warnThreshold ?? 30;

  const runCall = (value: string) => {
    if (!value.trim()) {
      toast({ title: "Informe um número para simular", variant: "destructive" });
      return;
    }
    setCallResult(null);
    simulateCall.mutate(value, {
      onSuccess: (data) => setCallResult(data),
      onError: (e) => toast({ title: "Erro ao simular", description: e.message, variant: "destructive" }),
    });
  };

  const runSms = (s: string, c: string) => {
    if (!s.trim() || !c.trim()) {
      toast({ title: "Informe remetente e conteúdo", variant: "destructive" });
      return;
    }
    setSmsResult(null);
    simulateSms.mutate(
      { sender: s, content: c },
      {
        onSuccess: (data) => setSmsResult(data),
        onError: (e) => toast({ title: "Erro ao simular", description: e.message, variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-5">
      {/* Mode switch */}
      <div className="glass flex w-fit rounded-xl p-1" role="tablist" aria-label="Modo do simulador">
        {(
          [
            { id: "call", label: "Chamada", icon: <PhoneCall className="size-4" /> },
            { id: "sms", label: "SMS", icon: <MessageSquare className="size-4" /> },
          ] as const
        ).map((m) => (
          <button
            key={m.id}
            role="tab"
            aria-selected={mode === m.id}
            onClick={() => setMode(m.id)}
            className={cn(
              "flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors",
              mode === m.id
                ? "bg-orange-500 text-white shadow-[0_0_16px_oklch(0.72_0.19_45/0.35)]"
                : "text-zinc-400 hover:text-foreground"
            )}
          >
            {m.icon}
            {m.label}
          </button>
        ))}
      </div>

      {mode === "call" ? (
        <div className="grid gap-4 lg:grid-cols-2">
          {/* Input */}
          <section className="glass space-y-4 rounded-2xl p-5">
            <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
              <PhoneCall className="size-4 text-orange-400" /> Chamada recebida
            </h2>
            <div className="flex gap-2">
              <Input
                value={number}
                onChange={(e) => setNumber(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && runCall(number)}
                placeholder="(11) 99999-9999 ou 0303..."
                className="font-mono"
                aria-label="Número da chamada"
              />
              <Button
                onClick={() => runCall(number)}
                disabled={simulateCall.isPending}
                className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
              >
                <Radar className="size-4" />
                Analisar
              </Button>
            </div>
            <div>
              <p className="mb-2 flex items-center gap-1.5 text-[11px] uppercase tracking-wider text-zinc-500">
                <Zap className="size-3 text-orange-400" /> Cenários de teste
              </p>
              <div className="flex flex-wrap gap-1.5">
                {CALL_SCENARIOS.map((s) => (
                  <button
                    key={s.label}
                    onClick={() => {
                      setNumber(s.number);
                      runCall(s.number);
                    }}
                    className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-zinc-300 transition-colors hover:border-orange-500/40 hover:text-orange-300"
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </div>
            <p className="rounded-lg border border-white/8 bg-white/3 px-3 py-2 text-[11px] leading-relaxed text-zinc-500">
              O motor consulta: lista autorizada → lista negra → golpes confirmados → pontuação
              (prefixo 0303 +90 · telemarketing +90 · robocall +95 · denúncias +60 · comercial
              verificado −40) → decisão pelos limites configurados.
            </p>
          </section>

          {/* Result */}
          <section className="glass min-h-64 rounded-2xl p-5" aria-live="polite">
            {simulateCall.isPending ? (
              <div className="space-y-3">
                <Skeleton className="h-6 w-40" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-24 w-full" />
              </div>
            ) : callResult ? (
              <div className="space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-mono text-lg font-bold">{formatPhone(callResult.e164)}</p>
                    <p className="text-xs text-zinc-500">{callResult.reason}</p>
                  </div>
                  <DecisionBadge decision={callResult.decision} />
                </div>
                <RiskGauge score={callResult.riskScore} warnThreshold={warnThreshold} blockThreshold={blockThreshold} />
                <div>
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                    Fluxo do SmartCall Engine
                  </p>
                  <ol className="space-y-1.5">
                    {callResult.steps.map((s, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs">
                        <span className="mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full bg-orange-500/15 font-mono text-[9px] text-orange-400">
                          {i + 1}
                        </span>
                        <span className="text-zinc-500">{s.step}:</span>
                        <span className="text-zinc-300">{s.result}</span>
                      </li>
                    ))}
                  </ol>
                </div>
                <div>
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                    Evidências
                  </p>
                  <EvidenceList evidences={callResult.evidences} />
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-48 flex-col items-center justify-center text-center text-zinc-500">
                <Smartphone className="mb-3 size-10 text-zinc-600" />
                <p className="text-sm">Simule uma chamada para ver a decisão do motor</p>
                <p className="text-xs">normalização → listas → reputação → pontuação → decisão</p>
              </div>
            )}
          </section>
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {/* Input */}
          <section className="glass space-y-4 rounded-2xl p-5">
            <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
              <MessageSquare className="size-4 text-orange-400" /> Mensagem recebida
            </h2>
            <Input
              value={sender}
              onChange={(e) => setSender(e.target.value)}
              placeholder="Remetente: 280, CLARO, +55 11 91234-0001..."
              className="font-mono"
              aria-label="Remetente do SMS"
            />
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Conteúdo da mensagem..."
              rows={4}
              aria-label="Conteúdo do SMS"
            />
            <Button
              onClick={() => runSms(sender, content)}
              disabled={simulateSms.isPending}
              className="w-full gap-2 bg-orange-500 text-white hover:bg-orange-600"
            >
              <Send className="size-4" />
              Filtrar mensagem
            </Button>
            <div>
              <p className="mb-2 flex items-center gap-1.5 text-[11px] uppercase tracking-wider text-zinc-500">
                <Zap className="size-3 text-orange-400" /> Cenários de teste
              </p>
              <div className="flex flex-wrap gap-1.5">
                {SMS_SCENARIOS.map((s) => (
                  <button
                    key={s.label}
                    onClick={() => {
                      setSender(s.sender);
                      setContent(s.content);
                      runSms(s.sender, s.content);
                    }}
                    className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-zinc-300 transition-colors hover:border-orange-500/40 hover:text-orange-300"
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </div>
            <p className="rounded-lg border border-white/8 bg-white/3 px-3 py-2 text-[11px] leading-relaxed text-zinc-500">
              Análise por: remetente (número/curto/alfanumérico) → conteúdo (palavras e links) →
              frequência → classificação (publicidade, cobrança, autenticação, serviço, fraude) →
              regras da operadora. Mensagens de autenticação nunca são descartadas.
            </p>
          </section>

          {/* Result */}
          <section className="glass min-h-64 rounded-2xl p-5" aria-live="polite">
            {simulateSms.isPending ? (
              <div className="space-y-3">
                <Skeleton className="h-6 w-40" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-24 w-full" />
              </div>
            ) : smsResult ? (
              <div className="space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-mono text-lg font-bold">{smsResult.sender}</p>
                    <p className="truncate text-xs text-zinc-500">{smsResult.reason}</p>
                  </div>
                  <DecisionBadge decision={smsResult.decision} />
                </div>
                <div className="rounded-lg border border-white/8 bg-black/30 p-3">
                  <p className="mb-1 text-[10px] uppercase tracking-wider text-zinc-500">Conteúdo analisado</p>
                  <p className="text-sm text-zinc-300">{content}</p>
                </div>
                <RiskGauge score={smsResult.riskScore} warnThreshold={warnThreshold} blockThreshold={blockThreshold} />
                <div>
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                    Fluxo do SMS Guard
                  </p>
                  <ol className="space-y-1.5">
                    {smsResult.steps.map((s, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs">
                        <span className="mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full bg-orange-500/15 font-mono text-[9px] text-orange-400">
                          {i + 1}
                        </span>
                        <span className="text-zinc-500">{s.step}:</span>
                        <span className="text-zinc-300">{s.result}</span>
                      </li>
                    ))}
                  </ol>
                </div>
                <div>
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                    Evidências
                  </p>
                  <EvidenceList evidences={smsResult.evidences} />
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-48 flex-col items-center justify-center text-center text-zinc-500">
                <MessageSquare className="mb-3 size-10 text-zinc-600" />
                <p className="text-sm">Simule uma mensagem para ver a classificação</p>
                <p className="text-xs">remetente → conteúdo → frequência → classificação → decisão</p>
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
