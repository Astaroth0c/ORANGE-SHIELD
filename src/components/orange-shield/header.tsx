"use client";

import { usePatchSettings, useSettings } from "@/hooks/use-orange-data";
import { Shield, ShieldAlert } from "lucide-react";
import { Switch as SwitchUI } from "@/components/ui/switch";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export function ShieldHeader() {
  const { data: settings } = useSettings();
  const patch = usePatchSettings();
  const active = settings?.protectionActive ?? false;

  return (
    <header className="sticky top-0 z-40 border-b border-white/8 bg-background/80 backdrop-blur-xl">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex size-11 items-center justify-center rounded-xl border transition-all",
              active
                ? "glow-orange border-orange-500/40 bg-orange-500/15 text-orange-400"
                : "border-white/10 bg-white/5 text-zinc-500"
            )}
          >
            <Shield className="size-6" strokeWidth={2.2} />
          </div>
          <div>
            <h1 className="text-lg font-bold leading-tight tracking-tight sm:text-xl">
              ORANGE <span className="text-gradient-orange">SHIELD</span>
            </h1>
            <p className="text-[10px] font-medium uppercase tracking-[0.18em] text-zinc-500">
              Smart Communication Protection
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span
            className={cn(
              "hidden items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold sm:inline-flex",
              active
                ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
                : "border-red-500/30 bg-red-500/10 text-red-400"
            )}
          >
            {active ? <Shield className="size-3.5" /> : <ShieldAlert className="size-3.5" />}
            {active ? "Protegido" : "Desprotegido"}
          </span>
          <div className="flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5">
            <span className="text-xs text-zinc-400">Proteção</span>
            {settings ? (
              <SwitchUI
                checked={active}
                disabled={patch.isPending}
                onCheckedChange={(v) => patch.mutate({ protectionActive: v })}
                aria-label="Alternar proteção geral"
              />
            ) : (
              <Skeleton className="h-5 w-9 rounded-full" />
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
