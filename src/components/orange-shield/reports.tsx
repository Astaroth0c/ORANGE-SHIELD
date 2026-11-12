"use client";

import { useState } from "react";
import { useReports, useStats, useSubmitReport } from "@/hooks/use-orange-data";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { CATEGORY_LABELS, DecisionBadge } from "./ui-bits";
import { cn } from "@/lib/utils";
import { format } from "date-fns";
import { ptBR } from "date-fns/locale";
import { CalendarDays, FileWarning, PieChart as PieIcon, Send, TrendingUp } from "lucide-react";

const ORANGE = "oklch(0.72 0.19 45)";
const GREEN = "oklch(0.75 0.17 150)";
const RED = "oklch(0.62 0.21 25)";
const AMBER = "oklch(0.8 0.16 85)";
const GRAY = "oklch(0.55 0.02 75)";

const PIE_COLORS = [RED, ORANGE, AMBER, GREEN, GRAY];

function ChartCard({
  title,
  icon,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="glass rounded-2xl p-4">
      <h2 className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-zinc-300">
        {icon}
        {title}
      </h2>
      <div className="h-56">{children}</div>
    </section>
  );
}

export function Reports() {
  const { data: stats, isLoading } = useStats();
  const { data: reports } = useReports();
  const submit = useSubmitReport();
  const { toast } = useToast();

  const [number, setNumber] = useState("");
  const [category, setCategory] = useState("telemarketing");
  const [description, setDescription] = useState("");

  const submitReport = () => {
    if (!number.trim()) {
      toast({ title: "Informe o número", variant: "destructive" });
      return;
    }
    submit.mutate(
      { number, category, description: description || undefined, autoApprove: true },
      {
        onSuccess: () => {
          toast({
            title: "Denúncia registrada",
            description: "A reputação coletiva do número foi atualizada.",
          });
          setNumber("");
          setDescription("");
        },
        onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }),
      }
    );
  };

  const dayData = (stats?.days ?? []).map((d) => ({
    ...d,
    label: format(new Date(`${d.day}T12:00:00`), "EEE", { locale: ptBR }),
  }));

  const callsPie = (stats?.callsByCategory ?? []).map((c) => ({
    name: CATEGORY_LABELS[c.category] ?? c.category,
    value: c.count,
  }));

  const smsPie = (stats?.smsByCategory ?? []).map((c) => ({
    name: CATEGORY_LABELS[c.category] ?? c.category,
    value: c.count,
  }));

  const weekBlocked = (stats?.days ?? []).reduce((acc, d) => acc + d.blocked, 0);
  const weekAllowed = (stats?.days ?? []).reduce((acc, d) => acc + d.allowed, 0);

  return (
    <div className="space-y-4">
      {/* Weekly summary */}
      <section className="grid gap-3 sm:grid-cols-3" aria-label="Resumo semanal">
        <div className="glass rounded-xl p-4">
          <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-zinc-500">
            <CalendarDays className="size-3 text-orange-400" /> Últimos 7 dias
          </p>
          <p className="mt-1 font-mono text-3xl font-bold text-orange-400">{weekBlocked}</p>
          <p className="text-xs text-zinc-400">chamadas bloqueadas</p>
        </div>
        <div className="glass rounded-xl p-4">
          <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-zinc-500">
            <TrendingUp className="size-3 text-emerald-400" /> Legítimas preservadas
          </p>
          <p className="mt-1 font-mono text-3xl font-bold text-emerald-400">{weekAllowed}</p>
          <p className="text-xs text-zinc-400">chamadas permitidas</p>
        </div>
        <div className="glass rounded-xl p-4">
          <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-zinc-500">
            <FileWarning className="size-3 text-amber-400" /> SMS bloqueados + quarentena
          </p>
          <p className="mt-1 font-mono text-3xl font-bold text-amber-400">
            {(stats?.blockedSms ?? 0) + (stats?.quarantinedSms ?? 0)}
          </p>
          <p className="text-xs text-zinc-400">mensagens impedidas na semana</p>
        </div>
      </section>

      {/* Charts */}
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {isLoading ? (
            <Skeleton className="h-72 w-full rounded-2xl" />
          ) : (
            <ChartCard
              title="Chamadas nos últimos 7 dias"
              icon={<TrendingUp className="size-3.5 text-orange-400" />}
            >
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={dayData} barGap={4}>
                  <CartesianGrid strokeDasharray="3 3" stroke="oklch(1 0 0 / 6%)" vertical={false} />
                  <XAxis dataKey="label" tick={{ fill: "oklch(0.64 0.015 75)", fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis allowDecimals={false} tick={{ fill: "oklch(0.64 0.015 75)", fontSize: 11 }} axisLine={false} tickLine={false} width={24} />
                  <Tooltip
                    cursor={{ fill: "oklch(1 0 0 / 4%)" }}
                    contentStyle={{
                      background: "oklch(0.17 0.012 60)",
                      border: "1px solid oklch(1 0 0 / 10%)",
                      borderRadius: 10,
                      fontSize: 12,
                    }}
                  />
                  <Bar dataKey="blocked" name="Bloqueadas" fill={RED} radius={[4, 4, 0, 0]} />
                  <Bar dataKey="allowed" name="Permitidas" fill={GREEN} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          )}
        </div>

        {isLoading ? (
          <Skeleton className="h-72 w-full rounded-2xl" />
        ) : (
          <ChartCard title="Chamadas bloqueadas por categoria" icon={<PieIcon className="size-3.5 text-orange-400" />}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={callsPie.length ? callsPie : [{ name: "Sem dados", value: 1 }]}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={45}
                  outerRadius={75}
                  paddingAngle={3}
                  strokeWidth={0}
                >
                  {(callsPie.length ? callsPie : [{ name: "x", value: 1 }]).map((_, i) => (
                    <Cell key={i} fill={callsPie.length ? PIE_COLORS[i % PIE_COLORS.length] : GRAY} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: "oklch(0.17 0.012 60)",
                    border: "1px solid oklch(1 0 0 / 10%)",
                    borderRadius: 10,
                    fontSize: 12,
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {isLoading ? (
          <Skeleton className="h-72 w-full rounded-2xl" />
        ) : (
          <ChartCard title="SMS filtrados por categoria" icon={<PieIcon className="size-3.5 text-orange-400" />}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={smsPie.length ? smsPie : [{ name: "Sem dados", value: 1 }]}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={45}
                  outerRadius={75}
                  paddingAngle={3}
                  strokeWidth={0}
                >
                  {(smsPie.length ? smsPie : [{ name: "x", value: 1 }]).map((_, i) => (
                    <Cell key={i} fill={smsPie.length ? PIE_COLORS[i % PIE_COLORS.length] : GRAY} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: "oklch(0.17 0.012 60)",
                    border: "1px solid oklch(1 0 0 / 10%)",
                    borderRadius: 10,
                    fontSize: 12,
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        )}

        {/* Report submission */}
        <section className="glass space-y-3 rounded-2xl p-4 lg:col-span-2" aria-label="Denunciar número">
          <h2 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-zinc-300">
            <Send className="size-3.5 text-orange-400" /> Denunciar número (inteligência coletiva)
          </h2>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              value={number}
              onChange={(e) => setNumber(e.target.value)}
              placeholder="Número da chamada ou remetente"
              className="font-mono"
              aria-label="Número para denunciar"
            />
            <Select value={category} onValueChange={setCategory}>
              <SelectTrigger className="sm:w-48" aria-label="Categoria da denúncia">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="telemarketing">Telemarketing</SelectItem>
                <SelectItem value="robocall">Robocall</SelectItem>
                <SelectItem value="fraud">Golpe</SelectItem>
                <SelectItem value="operator_spam">SMS de operadora</SelectItem>
                <SelectItem value="other">Outro</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Descrição opcional do que aconteceu..."
            rows={2}
            aria-label="Descrição da denúncia"
          />
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] text-zinc-500">
              Denúncias voluntárias elevam a reputação do número. Aprovadas a partir de 3
              denúncias de fraude, o número é tratado como golpe confirmado.
            </p>
            <Button
              onClick={submitReport}
              disabled={submit.isPending}
              className="shrink-0 gap-2 bg-orange-500 text-white hover:bg-orange-600"
            >
              <Send className="size-4" /> Enviar
            </Button>
          </div>
        </section>
      </div>

      {/* Recent reports */}
      <section className="glass rounded-2xl p-4" aria-label="Denúncias recentes">
        <h2 className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-zinc-300">
          <FileWarning className="size-3.5 text-amber-400" /> Denúncias registradas
        </h2>
        <div className="custom-scrollbar max-h-64 space-y-2 overflow-y-auto pr-1">
          {(reports ?? []).length === 0 && (
            <p className="py-6 text-center text-xs text-zinc-500">Nenhuma denúncia registrada ainda.</p>
          )}
          {(reports ?? []).map((r) => (
            <div
              key={r.id}
              className="flex flex-wrap items-center gap-2 rounded-lg border border-white/6 bg-white/3 px-3 py-2"
            >
              <span className="font-mono text-sm font-semibold">{r.e164}</span>
              <span
                className={cn(
                  "rounded px-1.5 py-0.5 text-[9px] uppercase tracking-wider",
                  r.category === "fraud"
                    ? "bg-red-500/10 text-red-400"
                    : "bg-orange-500/10 text-orange-400"
                )}
              >
                {CATEGORY_LABELS[r.category] ?? r.category}
              </span>
              {r.description && (
                <span className="min-w-0 flex-1 truncate text-xs text-zinc-500">{r.description}</span>
              )}
              <span
                className={cn(
                  "ml-auto rounded-full border px-2 py-0.5 text-[10px] font-semibold",
                  r.status === "approved"
                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
                    : r.status === "rejected"
                      ? "border-zinc-500/30 bg-zinc-500/10 text-zinc-400"
                      : "border-amber-500/30 bg-amber-500/10 text-amber-400"
                )}
              >
                {r.status === "approved" ? "aprovada" : r.status === "rejected" ? "rejeitada" : "pendente"}
              </span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
