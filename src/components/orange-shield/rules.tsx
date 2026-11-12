"use client";

import { useState } from "react";
import { usePatchSettings, useSettings } from "@/hooks/use-orange-data";
import { Skeleton } from "@/components/ui/skeleton";
import { Slider } from "@/components/ui/slider";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import {
  Ban,
  Fingerprint,
  Globe,
  Info,
  Link2,
  MessageCircle,
  MoonStar,
  PhoneMissed,
  Repeat2,
  Share2,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  UserRoundX,
  Users,
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
  const [ownInput, setOwnInput] = useState<string | null>(null);

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

  const mutate = (data: Record<string, boolean | number | string>, msg: string) => {
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
      {/* WhatsApp rules */}
      <section className="space-y-2.5" aria-label="Regras do WhatsApp">
        <h3 className="px-1 text-xs font-semibold uppercase tracking-wider text-zinc-400">
          WhatsApp Guard
        </h3>
        <ToggleRow
          icon={<MessageCircle className="size-4" />}
          title="WhatsApp Guard ativo"
          description="Filtragem de mensagens recebidas fora dos contatos"
          checked={settings.waProtect}
          onChange={(v) => mutate({ waProtect: v }, "WhatsApp Guard atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<ShieldCheck className="size-4" />}
          title="Phishing do código de 6 dígitos"
          description="Bloqueia pedidos de código (sequestro de conta) (+80)"
          checked={settings.waBlockCodePhish}
          onChange={(v) => mutate({ waBlockCodePhish: v }, "Phishing de código atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Link2 className="size-4" />}
          title="Links de desconhecidos"
          description="Encurtadores e TLDs de risco pesam +30; links de desconhecido +15"
          checked={settings.waBlockLinks}
          onChange={(v) => mutate({ waBlockLinks: v }, "Links atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Share2 className="size-4" />}
          title="Disparos encaminhados"
          description={'Mensagens "encaminhada muitas vezes" pesam +25'}
          checked={settings.waBlockForwarded}
          onChange={(v) => mutate({ waBlockForwarded: v }, "Disparos atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Users className="size-4" />}
          title="Convites de grupo"
          description="Convites de grupos de desconhecidos em quarentena (+35)"
          checked={settings.waBlockGroupInvite}
          onChange={(v) => mutate({ waBlockGroupInvite: v }, "Convites atualizado")}
          disabled={patch.isPending}
        />
        <ToggleRow
          icon={<Ban className="size-4" />}
          title="Bloquear desconhecidos no WhatsApp"
          description="Rigor máximo: só recebe de autorizados (+70)"
          checked={settings.waBlockUnknown}
          onChange={(v) => mutate({ waBlockUnknown: v }, "Desconhecidos WA atualizado")}
          disabled={patch.isPending}
        />
      </section>

      {/* Spoofing rules */}
      <section className="space-y-2.5" aria-label="Regras anti-spoofing">
        <h3 className="px-1 text-xs font-semibold uppercase tracking-wider text-zinc-400">
          Anti-Spoofing (Spoof Detector)
        </h3>
        <ToggleRow
          icon={<Fingerprint className="size-4" />}
          title="Detector de spoofing"
          description="Neighbor spoofing, DDD inválido, formato impossível, Wangiri"
          checked={settings.spoofProtection}
          onChange={(v) => mutate({ spoofProtection: v }, "Detector de spoofing atualizado")}
          disabled={patch.isPending}
        />
        <div className="rounded-xl border border-white/8 bg-white/3 px-4 py-3">
          <p className="text-sm font-medium">Seu número (referência)</p>
          <p className="mt-0.5 text-xs text-zinc-500">
            Usado para detectar chamadas que imitam o seu número (neighbor spoofing).
          </p>
          <div className="mt-2 flex gap-2">
            <Input
              value={ownInput ?? (settings.ownNumber ? `+${settings.ownNumber}` : "")}
              onChange={(e) => setOwnInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && mutate({ ownNumber: ownInput ?? "" }, "Seu número atualizado")}
              placeholder="+55 11 98765-4321"
              className="font-mono"
              aria-label="Seu número de telefone"
            />
            <Button
              onClick={() => mutate({ ownNumber: ownInput ?? "" }, "Seu número atualizado")}
              disabled={patch.isPending}
              variant="outline"
              className="shrink-0 border-white/12 hover:border-orange-500/40 hover:text-orange-300"
            >
              Salvar
            </Button>
          </div>
        </div>
        <div className="rounded-xl border border-orange-500/25 bg-orange-500/6 px-4 py-3">
          <p className="text-sm font-medium text-orange-300">Pesos do Spoof Detector</p>
          <p className="mt-0.5 text-xs text-zinc-400">
            Neighbor spoofing +35 · DDD inexistente +30 · celular sem 9º dígito +25 · dígitos
            repetidos +25 · sequência artificial +20 · Wangiri (rediscagem) +40. Score ≥ 55 =
            spoofing provável; evidências entram na pontuação do SmartCall Engine.
          </p>
        </div>
      </section>
    </div>
  );
}
