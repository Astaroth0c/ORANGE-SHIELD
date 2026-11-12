"use client";

import { useState } from "react";
import {
  usePatchSettings,
  useSettings,
  useSimulateWhatsApp,
  useWhatsAppHistory,
} from "@/hooks/use-orange-data";
import { WhatsAppAnalysisResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { DecisionBadge, EvidenceList, RiskGauge } from "./ui-bits";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "date-fns";
import { ptBR } from "date-fns/locale";
import {
  Ban,
  CheckCircle2,
  EyeOff,
  Link2,
  MessageCircle,
  Send,
  Share2,
  ShieldAlert,
  ShieldCheck,
  Users,
  Zap,
} from "lucide-react";

const WA_SCENARIOS = [
  {
    label: "Golpe do código (sequestro de conta)",
    sender: "+55 11 98888-7766",
    content:
      "Olá! Recebi um código do WhatsApp no seu número por engano. Me envie o código 482913 para eu desbloquear minha conta, por favor!",
    forwardedMany: false,
    isGroupInvite: false,
  },
  {
    label: "Falso parente (troquei de número + PIX)",
    sender: "+55 11 91234-9988",
    content:
      "Oi mãe, troquei de número! Esse é meu novo WhatsApp. Preciso que você faça um PIX urgente de R$ 850 pra mim, depois explico tudo.",
    forwardedMany: false,
    isGroupInvite: false,
  },
  {
    label: "Falso delivery (link de rastreamento)",
    sender: "+55 21 96666-5050",
    content:
      "Seu pedido foi retido na alfândega. Pague a taxa de entrega de R$ 6,90 no link para liberar a entrega: http://rastreamento-correios.top/pacote",
    forwardedMany: false,
    isGroupInvite: false,
  },
  {
    label: "Empréstimo falso",
    sender: "+55 31 97777-1234",
    content:
      "Empréstimo aprovado! R$ 15.000 sem consulta ao SPC, caixa eletrônico hoje mesmo. Responda sim para liberar.",
    forwardedMany: false,
    isGroupInvite: false,
  },
  {
    label: "Disparo em cadeia (encaminhada)",
    sender: "+55 85 95555-3131",
    content:
      "URGENTE: a operadora está dando 10GB de internet grátis para quem encaminhar esta mensagem para 10 contatos. Aproveite antes que acabe!",
    forwardedMany: true,
    isGroupInvite: false,
  },
  {
    label: "Convite de grupo desconhecido",
    sender: "+55 71 94444-8080",
    content: "Você foi adicionado ao grupo PROMOÇÕES IMPERDÍVEIS BR — 256 participantes.",
    forwardedMany: false,
    isGroupInvite: true,
  },
  {
    label: "Mensagem pessoal legítima",
    sender: "+55 11 99123-4567",
    content: "Ana:Oi! Passando para confirmar o almoço de amanhã às 12h. Pode ser?",
    forwardedMany: false,
    isGroupInvite: false,
  },
];

const WA_RULES = [
  {
    key: "waProtect",
    label: "WhatsApp Guard ativo",
    description: "Filtra mensagens recebidas de remetentes fora dos contatos",
    icon: <MessageCircle className="size-4" />,
  },
  {
    key: "waBlockCodePhish",
    label: "Phishing do código de 6 dígitos",
    description: "Bloqueia pedidos de código de verificação (sequestro de conta)",
    icon: <ShieldAlert className="size-4" />,
  },
  {
    key: "waBlockLinks",
    label: "Links de desconhecidos",
    description: "Quarentena para links de remetentes fora da base",
    icon: <Link2 className="size-4" />,
  },
  {
    key: "waBlockForwarded",
    label: "Disparos encaminhados",
    description: "Pesa mensagens \"encaminhada muitas vezes\" (cadeia)",
    icon: <Share2 className="size-4" />,
  },
  {
    key: "waBlockUnknown",
    label: "Bloquear desconhecidos",
    description: "Rigor máximo: só recebe de números autorizados (+70)",
    icon: <Ban className="size-4" />,
  },
  {
    key: "waBlockGroupInvite",
    label: "Convites de grupo",
    description: "Quarentena para convites de grupos de desconhecidos",
    icon: <Users className="size-4" />,
  },
] as const;

function WaRuleRow({
  icon,
  label,
  description,
  checked,
  onChange,
  disabled,
}: {
  icon: React.ReactNode;
  label: string;
  description: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-white/6 bg-white/3 px-3 py-2.5">
      <div className="flex min-w-0 items-center gap-2.5">
        <span
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-lg border",
            checked
              ? "border-orange-500/30 bg-orange-500/12 text-orange-400"
              : "border-white/10 bg-white/5 text-zinc-500"
          )}
        >
          {icon}
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{label}</p>
          <p className="truncate text-[11px] text-zinc-500">{description}</p>
        </div>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          "relative h-5 w-9 shrink-0 rounded-full transition-colors disabled:opacity-50",
          checked ? "bg-orange-500" : "bg-white/15"
        )}
      >
        <span
          className={cn(
            "absolute top-0.5 size-4 rounded-full bg-white transition-all",
            checked ? "left-4.5" : "left-0.5"
          )}
        />
      </button>
    </div>
  );
}

export function WhatsAppGuard() {
  const [sender, setSender] = useState("");
  const [content, setContent] = useState("");
  const [forwardedMany, setForwardedMany] = useState(false);
  const [isGroupInvite, setIsGroupInvite] = useState(false);
  const [result, setResult] = useState<WhatsAppAnalysisResponse | null>(null);

  const simulate = useSimulateWhatsApp();
  const { data: settings } = useSettings();
  const patch = usePatchSettings();
  const { data: history } = useWhatsAppHistory("ALL");
  const { toast } = useToast();

  const blockThreshold = settings?.blockThreshold ?? 70;
  const warnThreshold = settings?.warnThreshold ?? 30;

  const run = (s: string, c: string, fwd: boolean, grp: boolean) => {
    if (!s.trim() || !c.trim()) {
      toast({ title: "Informe remetente e conteúdo", variant: "destructive" });
      return;
    }
    setResult(null);
    simulate.mutate(
      { sender: s, content: c, forwardedMany: fwd, isGroupInvite: grp },
      {
        onSuccess: (data) => setResult(data),
        onError: (e) =>
          toast({ title: "Erro ao simular", description: e.message, variant: "destructive" }),
      }
    );
  };

  const toggleRule = (key: string, value: boolean, label: string) => {
    patch.mutate({ [key]: value }, {
      onSuccess: () => toast({ title: `${label}: ${value ? "ativado" : "desativado"}` }),
      onError: (e) => toast({ title: "Erro ao salvar", description: e.message, variant: "destructive" }),
    });
  };

  const waActive = (settings?.waProtect ?? false) && (settings?.protectionActive ?? false);

  return (
    <div className="space-y-5">
      {/* Guard status */}
      <div
        className={cn(
          "flex items-center gap-3 rounded-xl border px-4 py-3",
          waActive
            ? "border-emerald-500/25 bg-emerald-500/8 text-emerald-300"
            : "border-red-500/25 bg-red-500/8 text-red-300"
        )}
      >
        {waActive ? <ShieldCheck className="size-5" /> : <ShieldAlert className="size-5" />}
        <p className="text-sm font-medium">
          {waActive
            ? "WhatsApp Guard ativo — mensagens de desconhecidos filtradas antes da notificação"
            : "WhatsApp Guard inativo — todas as mensagens são entregues sem filtro"}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Simulator input */}
        <section className="glass space-y-4 rounded-2xl p-5">
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
            <MessageCircle className="size-4 text-orange-400" /> Mensagem recebida no WhatsApp
          </h2>
          <Input
            value={sender}
            onChange={(e) => setSender(e.target.value)}
            placeholder="Remetente: +55 11 98888-7766..."
            className="font-mono"
            aria-label="Remetente da mensagem"
          />
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Conteúdo da mensagem..."
            rows={4}
            aria-label="Conteúdo da mensagem"
          />
          <div className="flex flex-wrap gap-4">
            <label className="flex cursor-pointer items-center gap-2 text-xs text-zinc-300">
              <Checkbox
                checked={forwardedMany}
                onCheckedChange={(v) => setForwardedMany(v === true)}
                aria-label="Encaminhada muitas vezes"
              />
              <Share2 className="size-3.5 text-zinc-500" />
              Encaminhada muitas vezes
            </label>
            <label className="flex cursor-pointer items-center gap-2 text-xs text-zinc-300">
              <Checkbox
                checked={isGroupInvite}
                onCheckedChange={(v) => setIsGroupInvite(v === true)}
                aria-label="Convite de grupo"
              />
              <Users className="size-3.5 text-zinc-500" />
              Convite de grupo
            </label>
          </div>
          <Button
            onClick={() => run(sender, content, forwardedMany, isGroupInvite)}
            disabled={simulate.isPending}
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
              {WA_SCENARIOS.map((s) => (
                <button
                  key={s.label}
                  onClick={() => {
                    setSender(s.sender);
                    setContent(s.content);
                    setForwardedMany(s.forwardedMany);
                    setIsGroupInvite(s.isGroupInvite);
                    run(s.sender, s.content, s.forwardedMany, s.isGroupInvite);
                  }}
                  className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-zinc-300 transition-colors hover:border-orange-500/40 hover:text-orange-300"
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>
          <p className="rounded-lg border border-white/8 bg-white/3 px-3 py-2 text-[11px] leading-relaxed text-zinc-500">
            Fluxo do WhatsApp Guard: contatos autorizados → lista negra → reputação coletiva →
            spoofing do caller ID → conteúdo (código de 6 dígitos, PIX/falso parente, links,
            encaminhadas, convites) → frequência → decisão.
          </p>
        </section>

        {/* Result */}
        <section className="glass min-h-64 rounded-2xl p-5" aria-live="polite">
          {simulate.isPending ? (
            <div className="space-y-3">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : result ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-mono text-lg font-bold">{result.sender}</p>
                  <p className="truncate text-xs text-zinc-500">{result.reason}</p>
                </div>
                <DecisionBadge decision={result.decision} />
              </div>
              <div className="rounded-lg border border-white/8 bg-black/30 p-3">
                <p className="mb-1 text-[10px] uppercase tracking-wider text-zinc-500">
                  Conteúdo analisado
                </p>
                <p className="text-sm text-zinc-300">{content}</p>
              </div>
              <RiskGauge
                score={result.riskScore}
                warnThreshold={warnThreshold}
                blockThreshold={blockThreshold}
              />
              <div>
                <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                  Fluxo do WhatsApp Guard
                </p>
                <ol className="space-y-1.5">
                  {result.steps.map((s, i) => (
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
                <EvidenceList evidences={result.evidences} />
              </div>
              {result.spoofChecks.some((c) => c.status === "flagged") && (
                <div className="rounded-lg border border-amber-500/25 bg-amber-500/8 px-3 py-2 text-xs text-amber-300">
                  <p className="font-semibold">Spoofing no caller ID:</p>
                  {result.spoofChecks
                    .filter((c) => c.status === "flagged")
                    .map((c) => (
                      <p key={c.code}>
                        {c.label} (+{c.points}) — {c.detail}
                      </p>
                    ))}
                </div>
              )}
            </div>
          ) : (
            <div className="flex h-full min-h-48 flex-col items-center justify-center text-center text-zinc-500">
              <MessageCircle className="mb-3 size-10 text-zinc-600" />
              <p className="text-sm">Simule uma mensagem para ver a decisão do guard</p>
              <p className="text-xs">contatos → listas → conteúdo → frequência → decisão</p>
            </div>
          )}
        </section>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Quick rules */}
        <section className="glass space-y-2.5 rounded-2xl p-5" aria-label="Regras do WhatsApp Guard">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-zinc-300">
            Regras do WhatsApp Guard
          </h2>
          {WA_RULES.map((r) => (
            <WaRuleRow
              key={r.key}
              icon={r.icon}
              label={r.label}
              description={r.description}
              checked={(settings?.[r.key] ?? false) as boolean}
              onChange={(v) => toggleRule(r.key, v, r.label)}
              disabled={patch.isPending || !settings}
            />
          ))}
        </section>

        {/* History */}
        <section className="glass rounded-2xl p-5" aria-label="Histórico do WhatsApp">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-300">
            Mensagens filtradas
          </h2>
          {!history ? (
            <div className="space-y-2">
              {[...Array(4)].map((_, i) => (
                <Skeleton key={i} className="h-14 w-full rounded-xl" />
              ))}
            </div>
          ) : history.length === 0 ? (
            <div className="flex flex-col items-center py-10 text-center text-zinc-500">
              <EyeOff className="mb-2 size-8 text-zinc-600" />
              <p className="text-sm">Nenhuma mensagem filtrada ainda</p>
              <p className="text-xs">Use os cenários de teste para gerar histórico</p>
            </div>
          ) : (
            <div className="custom-scrollbar max-h-80 space-y-2 overflow-y-auto pr-1">
              {history.slice(0, 20).map((m) => (
                <div
                  key={m.id}
                  className="rounded-lg border border-white/6 bg-white/3 px-3 py-2.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="truncate font-mono text-sm font-medium">{m.sender}</span>
                      {m.forwardedMany && (
                        <span className="rounded bg-white/6 px-1 py-0.5 text-[9px] uppercase tracking-wider text-zinc-500">
                          encaminhada
                        </span>
                      )}
                      {m.isGroupInvite && (
                        <span className="rounded bg-white/6 px-1 py-0.5 text-[9px] uppercase tracking-wider text-zinc-500">
                          grupo
                        </span>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <DecisionBadge decision={m.decision} />
                      <span className="text-[10px] text-zinc-500">
                        {formatDistanceToNow(new Date(m.occurredAt), {
                          addSuffix: true,
                          locale: ptBR,
                        })}
                      </span>
                    </div>
                  </div>
                  <p className="mt-1 truncate text-xs text-zinc-500">{m.reason}</p>
                  <p className="mt-0.5 truncate text-[11px] text-zinc-600">{m.content}</p>
                </div>
              ))}
            </div>
          )}
          <div className="mt-4 flex items-start gap-2 rounded-lg border border-emerald-500/25 bg-emerald-500/6 px-3 py-2 text-[11px] text-zinc-400">
            <CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-emerald-400" />
            No Android, o WhatsApp Guard lê as notificações via NotificationListenerService e
            suprime/bloqueia conforme a decisão — contatos e grupos autorizados nunca são tocados.
          </div>
        </section>
      </div>
    </div>
  );
}
