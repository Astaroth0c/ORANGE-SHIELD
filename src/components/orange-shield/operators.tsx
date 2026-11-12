"use client";

import { useState } from "react";
import {
  useAddOperator,
  useDeleteOperator,
  useOperators,
  useUpdateOperator,
} from "@/hooks/use-orange-data";
import { OperatorRuleResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { Plus, Radio, Trash2 } from "lucide-react";

const OPERATOR_COLORS: Record<string, string> = {
  Claro: "border-red-500/30 bg-red-500/8 text-red-300",
  Vivo: "border-purple-500/30 bg-purple-500/8 text-purple-300",
  TIM: "border-blue-500/30 bg-blue-500/8 text-blue-300",
  Oi: "border-yellow-500/30 bg-yellow-500/8 text-yellow-300",
  Outras: "border-white/15 bg-white/6 text-zinc-300",
};

const CATEGORY_LABELS: Record<string, string> = {
  advertising: "Publicidade",
  authentication: "Autenticação",
  billing: "Cobrança",
  service: "Serviço",
};

const ACTION_LABELS: Record<string, string> = {
  allow: "Permitir",
  quarantine: "Quarentena",
  block: "Bloquear",
};

const ACTION_CLASSES: Record<string, string> = {
  allow: "text-emerald-400",
  quarantine: "text-amber-400",
  block: "text-red-400",
};

export function Operators() {
  const { data, isLoading } = useOperators();
  const update = useUpdateOperator();
  const add = useAddOperator();
  const remove = useDeleteOperator();
  const { toast } = useToast();

  const [operator, setOperator] = useState("Claro");
  const [sender, setSender] = useState("");
  const [category, setCategory] = useState("advertising");
  const [action, setAction] = useState("block");

  const submit = () => {
    if (!sender.trim()) {
      toast({ title: "Informe o remetente (ex: 280 ou VIVO)", variant: "destructive" });
      return;
    }
    add.mutate(
      { operator, sender, category, action },
      {
        onSuccess: () => {
          toast({ title: "Regra de remetente adicionada" });
          setSender("");
        },
        onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }),
      }
    );
  };

  const grouped = (data ?? []).reduce<Record<string, OperatorRuleResponse[]>>((acc, r) => {
    (acc[r.operator] ??= []).push(r);
    return acc;
  }, {});

  return (
    <div className="space-y-4">
      <p className="text-xs leading-relaxed text-zinc-500">
        Regras por remetente, não apenas por operadora: uma mensagem de autenticação do mesmo
        remetente comercial pode ser preservada enquanto a publicidade é bloqueada.
      </p>

      {/* Add rule */}
      <section className="glass flex flex-col gap-2 rounded-2xl p-4 lg:flex-row">
        <Select value={operator} onValueChange={setOperator}>
          <SelectTrigger className="lg:w-36" aria-label="Operadora">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {["Claro", "Vivo", "TIM", "Oi", "Outras"].map((o) => (
              <SelectItem key={o} value={o}>
                {o}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          value={sender}
          onChange={(e) => setSender(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Remetente (ex: 5050, CLARO)"
          className="font-mono"
          aria-label="Remetente"
        />
        <Select value={category} onValueChange={setCategory}>
          <SelectTrigger className="lg:w-44" aria-label="Categoria">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
              <SelectItem key={k} value={k}>
                {v}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={action} onValueChange={setAction}>
          <SelectTrigger className="lg:w-40" aria-label="Ação">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(ACTION_LABELS).map(([k, v]) => (
              <SelectItem key={k} value={k}>
                {v}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          onClick={submit}
          disabled={add.isPending}
          className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
        >
          <Plus className="size-4" /> Adicionar regra
        </Button>
      </section>

      {isLoading ? (
        <div className="space-y-3">
          {[...Array(4)].map((_, i) => (
            <Skeleton key={i} className="h-40 w-full rounded-2xl" />
          ))}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {Object.entries(grouped).map(([op, rules]) => (
            <section key={op} className="glass rounded-2xl p-4">
              <div className="mb-3 flex items-center gap-2">
                <span
                  className={cn(
                    "flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold",
                    OPERATOR_COLORS[op] ?? OPERATOR_COLORS["Outras"]
                  )}
                >
                  <Radio className="size-3" />
                  {op}
                </span>
                <span className="text-[11px] text-zinc-500">{rules.length} regra(s) de remetente</span>
              </div>
              <div className="space-y-2">
                {rules.map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center justify-between gap-2 rounded-lg border border-white/6 bg-white/3 px-3 py-2"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="font-mono text-sm font-semibold">{r.sender}</span>
                      <span className="rounded bg-white/6 px-1.5 py-0.5 text-[9px] uppercase tracking-wider text-zinc-400">
                        {CATEGORY_LABELS[r.category] ?? r.category}
                      </span>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      <Select
                        value={r.action}
                        onValueChange={(v) =>
                          update.mutate(
                            { id: r.id, action: v },
                            { onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }) }
                          )
                        }
                      >
                        <SelectTrigger
                          size="sm"
                          className={cn("h-8 w-28 border-white/10 text-xs font-semibold", ACTION_CLASSES[r.action])}
                          aria-label={`Ação para ${r.sender}`}
                        >
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {Object.entries(ACTION_LABELS).map(([k, v]) => (
                            <SelectItem key={k} value={k}>
                              {v}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <button
                        onClick={() =>
                          update.mutate(
                            { id: r.id, active: !r.active },
                            { onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }) }
                          )
                        }
                        role="switch"
                        aria-checked={r.active}
                        aria-label={`Ativar regra ${r.sender}`}
                        className={cn(
                          "relative h-5 w-9 shrink-0 rounded-full transition-colors",
                          r.active ? "bg-orange-500" : "bg-white/15"
                        )}
                      >
                        <span
                          className={cn(
                            "absolute top-0.5 size-4 rounded-full bg-white transition-all",
                            r.active ? "left-4.5" : "left-0.5"
                          )}
                        />
                      </button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => remove.mutate(r.id)}
                        aria-label={`Remover regra ${r.sender}`}
                        className="size-7 text-zinc-600 hover:text-red-400"
                      >
                        <Trash2 className="size-3.5" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
