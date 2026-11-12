"use client";

import { useCallHistory, useSettings, useSmsHistory, useStats, useWhatsAppHistory } from "@/hooks/use-orange-data";
import {
  DecisionBadge,
  ProtectionRing,
  StatCard,
} from "./ui-bits";
import { formatPhone } from "@/lib/engine/normalize";
import { formatDistanceToNow } from "date-fns";
import { ptBR } from "date-fns/locale";
import {
  Ban,
  Database,
  FileWarning,
  Fingerprint,
  Lightbulb,
  MessageCircle,
  MessageSquareOff,
  PhoneOff,
  ShieldCheck,
  Siren,
  Target,
} from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** Master toggle switches shown as "Proteções ativadas" on the dashboard. */
function ProtectionItem({
  label,
  description,
  checked,
  onToggle,
  disabled,
}: {
  label: string;
  description: string;
  checked: boolean;
  onToggle: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-white/6 bg-white/3 px-3 py-2.5">
      <div className="min-w-0">
        <div className="flex items-center gap-2 text-sm font-medium">
          <span
            className={cn(
              "size-1.5 rounded-full",
              checked ? "bg-orange-400 shadow-[0_0_8px_oklch(0.72_0.19_45)]" : "bg-zinc-600"
            )}
          />
          {label}
        </div>
        <p className="truncate text-[11px] text-zinc-500">{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        onClick={() => onToggle(!checked)}
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

/** Horizontal stacked bar for the 7-day decision distribution. */
function DecisionBar({
  dist,
}: {
  dist: { ALLOW: number; WARN: number; SILENCE: number; BLOCK: number };
}) {
  const total = dist.ALLOW + dist.WARN + dist.SILENCE + dist.BLOCK;
  const segs = [
    { key: "ALLOW", count: dist.ALLOW, color: "bg-emerald-500", label: "Permitidas" },
    { key: "WARN", count: dist.WARN, color: "bg-amber-500", label: "Suspeitas" },
    { key: "SILENCE", count: dist.SILENCE, color: "bg-zinc-500", label: "Silenciadas" },
    { key: "BLOCK", count: dist.BLOCK, color: "bg-red-500", label: "Bloqueadas" },
  ].filter((s) => s.count > 0);

  if (total === 0) {
    return <p className="py-4 text-center text-xs text-zinc-500">Sem chamadas triadas nos últimos 7 dias.</p>;
  }

  return (
    <div>
      <div className="flex h-4 w-full overflow-hidden rounded-full bg-white/8" role="img" aria-label="Distribuição de decisões dos últimos 7 dias">
        {segs.map((s) => (
          <div
            key={s.key}
            className={cn(s.color, "h-full transition-all duration-700")}
            style={{ width: `${(s.count / total) * 100}%` }}
          />
        ))}
      </div>
      <div className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1">
        {segs.map((s) => (
          <div key={s.key} className="flex items-center gap-1.5 text-[11px] text-zinc-400">
            <span className={cn("size-2 rounded-sm", s.color)} />
            {s.label}
            <span className="font-mono font-semibold text-zinc-300">{s.count}</span>
            <span className="text-zinc-600">({Math.round((s.count / total) * 100)}%)</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Channel attack mini-bars: calls vs SMS vs WhatsApp (7 days). */
function ChannelBars({
  attacks,
}: {
  attacks: { calls: number; sms: number; whatsapp: number };
}) {
  const rows = [
    { label: "Chamadas", icon: <PhoneOff className="size-3.5" />, count: attacks.calls, color: "bg-orange-500" },
    { label: "SMS", icon: <MessageSquareOff className="size-3.5" />, count: attacks.sms, color: "bg-red-500" },
    { label: "WhatsApp", icon: <MessageCircle className="size-3.5" />, count: attacks.whatsapp, color: "bg-emerald-500" },
  ];
  const max = Math.max(1, ...rows.map((r) => r.count));

  return (
    <div className="space-y-2.5">
      {rows.map((r) => (
        <div key={r.label} className="flex items-center gap-2.5">
          <span className="flex w-24 shrink-0 items-center gap-1.5 text-xs text-zinc-400">
            {r.icon}
            {r.label}
          </span>
          <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-white/8">
            <div
              className={cn(r.color, "h-full rounded-full transition-all duration-700")}
              style={{ width: `${Math.max(r.count > 0 ? 6 : 0, (r.count / max) * 100)}%` }}
            />
          </div>
          <span className="w-8 shrink-0 text-right font-mono text-xs font-semibold">{r.count}</span>
        </div>
      ))}
    </div>
  );
}

/** Insight rows with icon mapping. */
function InsightRow({ icon, text }: { icon: string; text: string }) {
  const Icon =
    icon === "siren" ? Siren : icon === "target" ? Target : icon === "fingerprint" ? Fingerprint : ShieldCheck;
  return (
    <li className="flex items-start gap-2.5 rounded-lg border border-white/6 bg-white/3 px-3 py-2">
      <Icon className="mt-0.5 size-4 shrink-0 text-orange-400" />
      <span className="text-xs leading-relaxed text-zinc-300">{text}</span>
    </li>
  );
}

export function Dashboard({ onNavigate }: { onNavigate: (tab: string) => void }) {
  const { data: stats } = useStats();
  const { data: settings } = useSettings();
  const { data: recentCalls } = useCallHistory("ALL");
  const { data: recentSms } = useSmsHistory("ALL");
  const { data: recentWa } = useWhatsAppHistory("ALL");

  const protections = [
    { key: "blockTelemarketing", label: "Bloqueio de telemarketing", description: "Prefixo 0303 e centrais comerciais" },
    { key: "blockRobocalls", label: "Bloqueio de robocalls", description: "Disparos automáticos identificados" },
    { key: "blockOperatorAds", label: "Filtro de SMS comercial", description: "Propaganda de operadoras em quarentena/bloqueio" },
    { key: "blockCollectors", label: "Proteção contra golpes", description: "Números confirmados como fraude" },
    { key: "waProtect", label: "WhatsApp Guard", description: "Filtra mensagens e golpes no WhatsApp" },
    { key: "spoofProtection", label: "Detector de spoofing", description: "Caller ID imitado/DDD inválido/Wangiri" },
  ] as const;

  const activeCount = protections.filter((p) => settings?.[p.key]).length;
  const percent = settings?.protectionActive ? Math.round((activeCount / protections.length) * 100) : 0;

  const feed = [
    ...(recentCalls ?? []).slice(0, 5).map((c) => ({
      id: c.id,
      kind: "call" as const,
      title: formatPhone(c.e164),
      decision: c.decision,
      reason: c.reason,
      occurredAt: c.occurredAt,
    })),
    ...(recentSms ?? []).slice(0, 4).map((s) => ({
      id: s.id,
      kind: "sms" as const,
      title: s.sender,
      decision: s.decision,
      reason: s.reason,
      occurredAt: s.occurredAt,
    })),
    ...(recentWa ?? []).slice(0, 3).map((w) => ({
      id: w.id,
      kind: "whatsapp" as const,
      title: w.sender,
      decision: w.decision,
      reason: w.reason,
      occurredAt: w.occurredAt,
    })),
  ]
    .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
    .slice(0, 8);

  return (
    <div className="space-y-6">
      {/* Hero: protection ring + stats */}
      <div className="grid gap-4 lg:grid-cols-[auto_1fr]">
        <section className="glass glow-orange flex flex-col items-center justify-center rounded-2xl p-6" aria-label="Status da proteção">
          {settings ? (
            <ProtectionRing
              percent={percent}
              active={settings.protectionActive}
              label={settings.protectionActive ? "Protegido" : "Inativo"}
              sublabel="Motor de proteção"
            />
          ) : (
            <Skeleton className="size-[168px] rounded-full" />
          )}
          <p className="mt-4 text-center text-xs text-zinc-400">
            SmartCall Engine <span className="font-mono text-orange-400">{settings?.ruleVersion ?? "v1.0.0"}</span>
          </p>
        </section>

        <section className="grid grid-cols-2 gap-3 sm:grid-cols-2 xl:grid-cols-3" aria-label="Estatísticas de proteção">
          <StatCard icon={<PhoneOff className="size-5" />} value={stats?.blockedCalls ?? 0} label="Chamadas bloqueadas" />
          <StatCard icon={<MessageSquareOff className="size-5" />} value={stats?.blockedSms ?? 0} label="SMS bloqueados" accent="red" />
          <StatCard icon={<MessageCircle className="size-5" />} value={stats?.blockedWa ?? 0} label="WhatsApp bloqueados" accent="green" />
          <StatCard icon={<Siren className="size-5" />} value={stats?.fraudCalls ?? 0} label="Golpes identificados" accent="red" />
          <StatCard icon={<Fingerprint className="size-5" />} value={stats?.spoofEvents ?? 0} label="Spoofing detectado" accent="amber" />
          <StatCard icon={<Ban className="size-5" />} value={stats?.quarantinedSms ?? 0} label="SMS em quarentena" accent="amber" />
          <StatCard
            icon={<ShieldCheck className="size-5" />}
            value={(stats?.blockedCalls24h ?? 0) + (stats?.blockedSms24h ?? 0) + (stats?.blockedWa ?? 0)}
            label="Bloqueios nas últimas 24h"
            accent="green"
          />
          <StatCard
            icon={<Database className="size-5" />}
            value={`${stats?.knownNumbers ?? 0}/${stats?.totalNumbers ?? 0}`}
            label="Base de inteligência"
            accent="orange"
          />
        </section>
      </div>

      {/* Active protections + recent feed */}
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="glass rounded-2xl p-5" aria-label="Proteções ativadas">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-zinc-300">Proteções ativadas</h2>
            <button
              onClick={() => onNavigate("rules")}
              className="text-xs font-medium text-orange-400 hover:text-orange-300"
            >
              Configurar regras →
            </button>
          </div>
          <div className="space-y-2">
            {protections.map((p) => (
              <ProtectionItem
                key={p.key}
                label={p.label}
                description={p.description}
                checked={settings?.[p.key] ?? false}
                onToggle={() => onNavigate("rules")}
                disabled
              />
            ))}
          </div>
          {settings?.silenceMode && (
            <p className="mt-3 rounded-lg border border-amber-500/25 bg-amber-500/8 px-3 py-2 text-xs text-amber-300">
              Modo silêncio total ativo: apenas contatos autorizados farão o telefone tocar.
            </p>
          )}
        </section>

        <section className="glass flex flex-col rounded-2xl p-5" aria-label="Atividade recente">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-zinc-300">Atividade recente</h2>
            <button
              onClick={() => onNavigate("history")}
              className="text-xs font-medium text-orange-400 hover:text-orange-300"
            >
              Ver histórico →
            </button>
          </div>
          <div className="custom-scrollbar max-h-72 space-y-2 overflow-y-auto pr-1">
            {feed.length === 0 && (
              <p className="py-8 text-center text-xs text-zinc-500">
                Nenhuma atividade ainda. Use o simulador para testar o motor.
              </p>
            )}
            {feed.map((item) => (
              <div
                key={`${item.kind}-${item.id}`}
                className="flex items-center justify-between gap-3 rounded-lg border border-white/6 bg-white/3 px-3 py-2.5"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-mono text-sm font-medium">{item.title}</span>
                    <span className="rounded bg-white/6 px-1 py-0.5 text-[9px] uppercase tracking-wider text-zinc-500">
                      {item.kind === "call" ? "Chamada" : item.kind === "whatsapp" ? "WhatsApp" : "SMS"}
                    </span>
                  </div>
                  <p className="truncate text-[11px] text-zinc-500">{item.reason}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  <DecisionBadge decision={item.decision} />
                  <span className="text-[10px] text-zinc-500">
                    {formatDistanceToNow(new Date(item.occurredAt), { addSuffix: true, locale: ptBR })}
                  </span>
                </div>
              </div>
            ))}
          </div>
          {stats && stats.pendingReports > 0 && (
            <button
              onClick={() => onNavigate("reports")}
              className="mt-3 flex items-center gap-2 rounded-lg border border-amber-500/25 bg-amber-500/8 px-3 py-2 text-xs text-amber-300 hover:bg-amber-500/12"
            >
              <FileWarning className="size-4" />
              {stats.pendingReports} denúncia(s) aguardando moderação
            </button>
          )}
        </section>
      </div>
      {/* Intelligence (v0.3.0): decisions, channels, top threats, insights */}
      <div className="grid gap-4 lg:grid-cols-3">
        <section className="glass rounded-2xl p-5" aria-label="Distribuição de decisões">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-300">Decisões · 7 dias</h2>
          {stats ? (
            <DecisionBar dist={stats.decisionDistribution} />
          ) : (
            <Skeleton className="h-16 w-full rounded-lg" />
          )}
        </section>

        <section className="glass rounded-2xl p-5" aria-label="Canais atacados">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-300">Canais atacados · 7 dias</h2>
          {stats ? (
            <ChannelBars attacks={stats.channelAttacks} />
          ) : (
            <Skeleton className="h-16 w-full rounded-lg" />
          )}
        </section>

        <section className="glass flex flex-col rounded-2xl p-5" aria-label="Insights automáticos">
          <h2 className="mb-4 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
            <Lightbulb className="size-4 text-orange-400" />
            Insights automáticos
          </h2>
          {stats ? (
            <ul className="space-y-2">
              {stats.insights.map((i, idx) => (
                <InsightRow key={idx} icon={i.icon} text={i.text} />
              ))}
            </ul>
          ) : (
            <Skeleton className="h-16 w-full rounded-lg" />
          )}
        </section>
      </div>

      {stats && stats.topThreats.length > 0 && (
        <section className="glass rounded-2xl p-5" aria-label="Principais ameaças da semana">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-zinc-300">
              <Target className="size-4 text-red-400" />
              Principais ameaças · 7 dias
            </h2>
            <button
              onClick={() => onNavigate("history")}
              className="text-xs font-medium text-orange-400 hover:text-orange-300"
            >
              Ver histórico →
            </button>
          </div>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {stats.topThreats.map((t) => (
              <div
                key={t.e164}
                className="flex items-center justify-between gap-3 rounded-lg border border-red-500/12 bg-red-500/5 px-3 py-2.5"
              >
                <div className="min-w-0">
                  <span className="block truncate font-mono text-sm font-medium">{formatPhone(t.e164)}</span>
                  <span className="text-[11px] text-zinc-500">
                    {t.blocked} bloqueio(s) · {t.hits} contato(s) com o motor
                  </span>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Ban className="size-4 text-red-400" />
                  <span className="font-mono text-lg font-bold text-red-400">{t.blocked}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
