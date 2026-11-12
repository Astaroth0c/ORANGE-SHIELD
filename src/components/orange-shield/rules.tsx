"use client";

import { usePatchSettings, useSettings } from "@/hooks/use-orange-data";
import { Skeleton } from "@/components/ui/skeleton";
import { Slider } from "@/components/ui/slider";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import {
  Globe,
  Info,
  MoonStar,
  PhoneMissed,
  Repeat2,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  UserRoundX,
} from "lucide-react";

function ToggleRow({
  icon,
  title,
  description,
  checked,
  onChange,
  disabled,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        "flex w-full items-center justify-between gap-3 rounded-xl border px-4 py-3 text-left transition-colors",
        checked
          ? "border-orange-500/25 bg-orange-500/6 hover:bg-orange-500/10"
          : "border-white/8 bg-white/3 hover:bg-white/5",
        disabled && "opacity-60"
      )}
    >
      <div className="flex min-w-0 items-center gap-3">
        <span
          className={cn(
            "flex size-9 shrink-0 items-center justify-center rounded-lg border",
            checked
              ? "border-orange-500/30 bg-orange-500/12 text-orange-400"
              : "border-white/10 bg-white/5 text-zinc-500"
          )}
        >
          {icon}
        </span>
        <div className="min-w-0">
          <p className="text-sm font-medium">{title}</p>
          <p className="truncate text-xs text-zinc-500">{description}</p>
        </div>
      </div>
      <span
        className={cn(
          "relative h-5 w-9 shrink-0 rounded-full transition-colors",
          checked ? "bg-orange-500" : "bg-white/15"
        )}
      >
        <span
          className={cn(
            "absolute top-0.5 size-4 rounded-full bg-white transition-all",
            checked ? "left-4.5" : "left-0.5"
          )}
        />
      </span>
    </button>
  );
}

export function Rules() {
  const { data: settings, isLoading } = useSettings();
  const patch = usePatchSettings();
  const { toast } = useToast();

  if (isLoading || !settings) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-24 w-full rounded-2xl" />
        {[...Array(4)].map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  const mutate = (data: Record<string, boolean | number>, msg: string) => {
    patch.mutate(data, {
      onSuccess: () => toast({ title: msg }),
      onError: (e) => toast({ title: "Erro ao salvar", description: e.message, variant: "destructive" }),
    });
  };

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {/* Thresholds */}
      <section className="glass space-y-5 rounded-2xl p-5 lg:col-span-2" aria-label="Limites de decisão">
        <div>
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
            <ShieldAlert className="size-4 text-orange-400" /> Limites de decisão do SmartCall Engine
          </h2>
          <p className="mt-1 text-xs text-zinc-500">
            Pontuação 0–100: abaixo do limite de alerta a comunicação passa livre; acima do
            limite de bloqueio é rejeitada. A lista autorizada sempre tem prioridade.
          </p>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium">Limite de alerta</span>
              <span className="font-mono text-amber-400">≥ {settings.warnThreshold}</span>
            </div>
            <Slider
              value={[settings.warnThreshold]}
              min={0}
              max={Math.min(69, settings.blockThreshold - 1)}
              step={1}
              disabled={patch.isPending}
              onValueChange={([v]) => mutate({ warnThreshold: v }, "Limite de alerta atualizado")}
              aria-label="Limite de alerta"
            />
            <p className="text-[11px] text-zinc-500">Chamadas suspeitas são identificadas na tela.</p>
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium">Limite de bloqueio</span>
              <span className="font-mono text-red-400">≥ {settings.blockThreshold}</span>
            </div>
            <Slider
              value={[settings.blockThreshold]}
              min={Math.max(1, settings.warnThreshold + 1)}
              max={100}
              step={1}
              disabled={patch.isPending}
              onValueChange={([v]) => mutate({ blockThreshold: v }, "Limite de bloqueio atualizado")}
              aria-label="Limite de bloqueio"
            />
            <p className="text-[11px] text-zinc-500">Chamadas de alto risco são rejeitadas automaticamente.</p>
          </div>
        </div>
        <div className="flex items-start gap-2 rounded-lg border border-white/8 bg-white/3 px-3 py-2 text-[11px] text-zinc-500">
          <Info className="mt-0.5 size-3.5 shrink-0 text-orange-400" />
          Pesos por evidência: 0303 +90 · telemarketing +90 · robocall +95 · cobrança +45 ·
          denúncias até +60 · insistência +30/+50 · comercial verificado −40.
        </div>
      </section>

      {/* Call rules */}
      <section className="space-y-2.5" aria-label="Regras de chamadas">
        <h3 className="px-1 text-xs font-semibold uppercase tracking-wider text-zinc-400">
          Proteção de chamadas
        </h3>
        <ToggleRow
          icon={<ShieldCheck className="size-4" />}
          title="Bloqueio de telemarketing"
          description="Centrais comerciais e prefixo 0303 (Anatel)"
          checked={settings.blockTelemarketing}
          onChange={(v) => mutate({ blockTelemarketing: v }, "Telemarketing atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<PhoneMissed className="size-4" />}
          title="Bloqueio de robocalls"
          description="Chamadas com disparo automático identificado"
          checked={settings.blockRobocalls}
          onChange={(v) => mutate({ blockRobocalls: v }, "Robocalls atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Repeat2 className="size-4" />}
          title="Proteção contra insistência"
          description="Pesa +30/+50 pontos para números reincidentes na hora"
          checked={settings.insistenceProtection}
          onChange={(v) => mutate({ insistenceProtection: v }, "Insistência atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Globe className="size-4" />}
          title="Bloquear chamadas internacionais"
          description="Números com DDI diferente de +55"
          checked={settings.blockInternational}
          onChange={(v) => mutate({ blockInternational: v }, "Internacionais atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<UserRoundX className="size-4" />}
          title="Bloquear desconhecidos"
          description="Rigor máximo: números fora da base e dos contatos (+100)"
          checked={settings.blockUnknownNumbers}
          onChange={(v) => mutate({ blockUnknownNumbers: v }, "Desconhecidos atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<MoonStar className="size-4" />}
          title="Modo silêncio total"
          description="Somente autorizados tocam; suspeitos são silenciados"
          checked={settings.silenceMode}
          onChange={(v) => mutate({ silenceMode: v }, "Modo silêncio atualizado")}
          disabled={patch.isPending}
        />
      </section>

      {/* SMS rules */}
      <section className="space-y-2.5" aria-label="Regras de SMS">
        <h3 className="px-1 text-xs font-semibold uppercase tracking-wider text-zinc-400">
          Proteção de mensagens
        </h3>
        <ToggleRow
          icon={<Smartphone className="size-4" />}
          title="Filtro de SMS comercial"
          description="Publicidade de operadoras e disparos promocionais"
          checked={settings.blockOperatorAds}
          onChange={(v) => mutate({ blockOperatorAds: v }, "SMS comercial atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<ShieldCheck className="size-4" />}
          title="Bloqueio de cobranças automatizadas"
          description="Coletores e robôs de cobrança (chamadas e SMS)"
          checked={settings.blockCollectors}
          onChange={(v) => mutate({ blockCollectors: v }, "Cobranças atualizado")}
          disabled={patch.isPending}
        />
        <div className="rounded-xl border border-emerald-500/25 bg-emerald-500/6 px-4 py-3">
          <p className="text-sm font-medium text-emerald-300">Proteção prioritária permanente</p>
          <p className="mt-0.5 text-xs text-zinc-400">
            Mensagens de autenticação (códigos, tokens, 2FA) nunca são descartadas — mesmo com
            links ou palavras comerciais. Quarentena sempre recuperável.
          </p>
        </div>
      </section>
    </div>
  );
}
