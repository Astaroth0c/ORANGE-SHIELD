"use client";

import { cn } from "@/lib/utils";
import { CheckCircle2, Eye, Moon, OctagonX, Lock, Inbox } from "lucide-react";

/* ------------------------------------------------------------------ */
/* Decision badges                                                     */
/* ------------------------------------------------------------------ */

const CALL_DECISION_META: Record<string, { label: string; className: string; icon: React.ReactNode }> = {
  ALLOW: {
    label: "PERMITIDA",
    className: "bg-emerald-500/12 text-emerald-400 border-emerald-500/30",
    icon: <CheckCircle2 className="size-3.5" />,
  },
  WARN: {
    label: "SUSPEITA",
    className: "bg-amber-500/12 text-amber-400 border-amber-500/30",
    icon: <Eye className="size-3.5" />,
  },
  SILENCE: {
    label: "SILENCIADA",
    className: "bg-zinc-500/12 text-zinc-300 border-zinc-500/30",
    icon: <Moon className="size-3.5" />,
  },
  BLOCK: {
    label: "BLOQUEADA",
    className: "bg-red-500/12 text-red-400 border-red-500/30",
    icon: <OctagonX className="size-3.5" />,
  },
  QUARANTINE: {
    label: "QUARENTENA",
    className: "bg-amber-500/12 text-amber-400 border-amber-500/30",
    icon: <Inbox className="size-3.5" />,
  },
};

export function DecisionBadge({ decision, className }: { decision: string; className?: string }) {
  const meta = CALL_DECISION_META[decision] ?? CALL_DECISION_META.ALLOW;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold tracking-wide",
        meta.className,
        className
      )}
    >
      {meta.icon}
      {meta.label}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Category labels                                                     */
/* ------------------------------------------------------------------ */

export const CATEGORY_LABELS: Record<string, string> = {
  telemarketing: "Telemarketing",
  robocall: "Robocall",
  fraud: "Golpe",
  collector: "Cobrança",
  verified_business: "Comercial verificado",
  blacklisted: "Lista negra",
  authorized: "Autorizado",
  international: "Internacional",
  unknown: "Desconhecido",
  advertising: "Publicidade",
  billing: "Cobrança (SMS)",
  authentication: "Autenticação",
  service: "Serviço",
  personal: "Pessoal",
  spoofing: "Spoofing",
  phishing_code: "Phishing de código",
  group_invite: "Convite de grupo",
};

export function CategoryChip({ category }: { category: string | null }) {
  if (!category) return null;
  return (
    <span className="rounded-md border border-white/10 bg-white/5 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider text-zinc-400">
      {CATEGORY_LABELS[category] ?? category}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Risk gauge — horizontal 0-100 bar with threshold markers            */
/* ------------------------------------------------------------------ */

export function RiskGauge({
  score,
  warnThreshold,
  blockThreshold,
}: {
  score: number;
  warnThreshold: number;
  blockThreshold: number;
}) {
  const color =
    score >= blockThreshold
      ? "bg-red-500"
      : score >= warnThreshold
        ? "bg-amber-500"
        : "bg-emerald-500";
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs text-zinc-400">
        <span>Pontuação de risco</span>
        <span className="font-mono text-sm font-semibold text-foreground">{score}/100</span>
      </div>
      <div className="relative h-3 overflow-hidden rounded-full bg-white/8">
        <div
          className={cn("absolute inset-y-0 left-0 rounded-full transition-all duration-700", color)}
          style={{ width: `${Math.max(3, score)}%` }}
        />
        {/* threshold markers */}
        <div
          className="absolute inset-y-0 w-px bg-amber-400/70"
          style={{ left: `${warnThreshold}%` }}
          title={`Alerta ≥ ${warnThreshold}`}
        />
        <div
          className="absolute inset-y-0 w-px bg-red-400/70"
          style={{ left: `${blockThreshold}%` }}
          title={`Bloqueio ≥ ${blockThreshold}`}
        />
      </div>
      <div className="flex justify-between text-[10px] text-zinc-500">
        <span>0 · Permitir</span>
        <span>{warnThreshold} · Alertar</span>
        <span>{blockThreshold} · Bloquear</span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Stat card                                                           */
/* ------------------------------------------------------------------ */

export function StatCard({
  icon,
  value,
  label,
  accent,
}: {
  icon: React.ReactNode;
  value: string | number;
  label: string;
  accent?: "orange" | "red" | "green" | "amber";
}) {
  const accentClass =
    accent === "red"
      ? "text-red-400 bg-red-500/10 border-red-500/20"
      : accent === "green"
        ? "text-emerald-400 bg-emerald-500/10 border-emerald-500/20"
        : accent === "amber"
          ? "text-amber-400 bg-amber-500/10 border-amber-500/20"
          : "text-orange-400 bg-orange-500/10 border-orange-500/20";
  return (
    <div className="glass rounded-xl p-4 transition-colors hover:border-orange-500/25">
      <div className="flex items-center gap-3">
        <div className={cn("flex size-10 items-center justify-center rounded-lg border", accentClass)}>
          {icon}
        </div>
        <div className="min-w-0">
          <div className="truncate font-mono text-2xl font-bold leading-tight">{value}</div>
          <div className="truncate text-xs text-zinc-400">{label}</div>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Protection ring                                                     */
/* ------------------------------------------------------------------ */

export function ProtectionRing({
  percent,
  active,
  label,
  sublabel,
}: {
  percent: number;
  active: boolean;
  label: string;
  sublabel: string;
}) {
  const size = 168;
  const stroke = 10;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (percent / 100) * c;

  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="oklch(1 0 0 / 8%)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={active ? "oklch(0.72 0.19 45)" : "oklch(0.5 0.02 70)"}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={offset}
          className="transition-all duration-1000"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        {active && (
          <div className="shield-pulse absolute inset-3 rounded-full border border-orange-500/40" />
        )}
        <Lock className={cn("mb-1 size-5", active ? "text-orange-400" : "text-zinc-500")} />
        <span className="font-mono text-4xl font-bold">{percent}%</span>
        <span className="text-sm font-medium">{label}</span>
        <span className="text-[11px] text-zinc-400">{sublabel}</span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Evidence list                                                       */
/* ------------------------------------------------------------------ */

export function EvidenceList({ evidences }: { evidences: { label: string; points: number }[] }) {
  if (!evidences.length) {
    return <p className="text-xs text-zinc-500">Nenhuma evidência acumulada.</p>;
  }
  return (
    <ul className="space-y-1.5">
      {evidences.map((e, i) => (
        <li key={i} className="flex items-center justify-between gap-3 text-xs">
          <span className="min-w-0 flex-1 truncate text-zinc-300">{e.label}</span>
          <span
            className={cn(
              "shrink-0 rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold",
              e.points >= 0
                ? "bg-red-500/10 text-red-400"
                : "bg-emerald-500/10 text-emerald-400"
            )}
          >
            {e.points > 0 ? `+${e.points}` : e.points}
          </span>
        </li>
      ))}
    </ul>
  );
}
