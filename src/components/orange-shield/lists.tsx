"use client";

import { useState } from "react";
import {
  useAddBlacklist,
  useAddPattern,
  useAddWhitelist,
  useBlacklist,
  useDeleteBlacklist,
  useDeletePattern,
  useDeleteWhitelist,
  usePatterns,
  useWhitelist,
} from "@/hooks/use-orange-data";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { Ban, CheckCircle2, Plus, Trash2, Wand2 } from "lucide-react";

type Tab = "blacklist" | "whitelist" | "patterns";

export function Lists() {
  const [tab, setTab] = useState<Tab>("blacklist");
  const { toast } = useToast();

  return (
    <div className="space-y-4">
      <div className="glass flex w-fit rounded-xl p-1" role="tablist" aria-label="Tipo de lista">
        {(
          [
            { id: "blacklist", label: "Lista negra", icon: <Ban className="size-4" /> },
            { id: "whitelist", label: "Lista autorizada", icon: <CheckCircle2 className="size-4" /> },
            { id: "patterns", label: "Padrões inteligentes", icon: <Wand2 className="size-4" /> },
          ] as const
        ).map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              "flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              tab === t.id ? "bg-orange-500 text-white" : "text-zinc-400 hover:text-foreground"
            )}
          >
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      {tab === "blacklist" && <BlacklistTab />}
      {tab === "whitelist" && <WhitelistTab />}
      {tab === "patterns" && <PatternsTab />}

      <p className="text-[11px] leading-relaxed text-zinc-500">
        A lista de permissões tem prioridade sobre qualquer pontuação — contatos autorizados
        nunca são bloqueados automaticamente. Números de emergência nunca entram nas listas de bloqueio.
      </p>
    </div>
  );
}

function BlacklistTab() {
  const { data, isLoading } = useBlacklist();
  const add = useAddBlacklist();
  const remove = useDeleteBlacklist();
  const [number, setNumber] = useState("");
  const [label, setLabel] = useState("");
  const { toast } = useToast();

  const submit = () => {
    if (!number.trim()) return;
    add.mutate(
      { number, label: label || undefined },
      {
        onSuccess: () => {
          toast({ title: "Número bloqueado", description: number });
          setNumber("");
          setLabel("");
        },
        onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-3">
      <section className="glass flex flex-col gap-2 rounded-2xl p-4 sm:flex-row">
        <Input
          value={number}
          onChange={(e) => setNumber(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Número (ex: 0300 123 4567)"
          className="font-mono"
          aria-label="Número para bloquear"
        />
        <Input
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Motivo (opcional)"
          aria-label="Motivo do bloqueio"
        />
        <Button
          onClick={submit}
          disabled={add.isPending}
          className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
        >
          <Plus className="size-4" /> Bloquear
        </Button>
      </section>

      <ListShell isLoading={isLoading} empty="Nenhum número na lista negra">
        {(data ?? []).map((b) => (
          <Row
            key={b.id}
            title={b.e164}
            subtitle={b.label ?? "Bloqueio manual"}
            tag={b.source === "engine" ? "motor" : "manual"}
            onDelete={() => remove.mutate(b.id)}
          />
        ))}
      </ListShell>
    </div>
  );
}

function WhitelistTab() {
  const { data, isLoading } = useWhitelist();
  const add = useAddWhitelist();
  const remove = useDeleteWhitelist();
  const [number, setNumber] = useState("");
  const [name, setName] = useState("");
  const [kind, setKind] = useState("contact");
  const { toast } = useToast();

  const submit = () => {
    if (!number.trim()) return;
    add.mutate(
      { number, name: name || undefined, kind },
      {
        onSuccess: () => {
          toast({ title: "Número autorizado", description: number });
          setNumber("");
          setName("");
        },
        onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-3">
      <section className="glass flex flex-col gap-2 rounded-2xl p-4 sm:flex-row">
        <Input
          value={number}
          onChange={(e) => setNumber(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Número (ex: 11 98888-7777)"
          className="font-mono"
          aria-label="Número para autorizar"
        />
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Nome (opcional)"
          aria-label="Nome do contato"
        />
        <Select value={kind} onValueChange={setKind}>
          <SelectTrigger className="sm:w-40" aria-label="Tipo de autorização">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="contact">Contato</SelectItem>
            <SelectItem value="business">Serviço importante</SelectItem>
            <SelectItem value="emergency">Emergência</SelectItem>
          </SelectContent>
        </Select>
        <Button
          onClick={submit}
          disabled={add.isPending}
          className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
        >
          <Plus className="size-4" /> Autorizar
        </Button>
      </section>

      <ListShell isLoading={isLoading} empty="Nenhum número autorizado">
        {(data ?? []).map((w) => (
          <Row
            key={w.id}
            title={w.e164}
            subtitle={w.name ?? "Autorização manual"}
            tag={
              w.kind === "emergency" ? "emergência" : w.kind === "business" ? "serviço" : "contato"
            }
            tagClass="text-emerald-400"
            onDelete={() => remove.mutate(w.id)}
          />
        ))}
      </ListShell>
    </div>
  );
}

function PatternsTab() {
  const { data, isLoading } = usePatterns();
  const add = useAddPattern();
  const remove = useDeletePattern();
  const [kind, setKind] = useState("keyword");
  const [value, setValue] = useState("");
  const [weight, setWeight] = useState("25");
  const [channel, setChannel] = useState("sms");
  const [label, setLabel] = useState("");
  const { toast } = useToast();

  const submit = () => {
    if (!value.trim()) return;
    add.mutate(
      { kind, value, weight: Number(weight) || 25, label: label || undefined, channel },
      {
        onSuccess: () => {
          toast({ title: "Padrão adicionado ao motor" });
          setValue("");
          setLabel("");
        },
        onError: (e) => toast({ title: "Erro", description: e.message, variant: "destructive" }),
      }
    );
  };

  return (
    <div className="space-y-3">
      <section className="glass flex flex-col gap-2 rounded-2xl p-4 lg:flex-row">
        <Select value={kind} onValueChange={setKind}>
          <SelectTrigger className="lg:w-36" aria-label="Tipo de padrão">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="keyword">Palavra-chave</SelectItem>
            <SelectItem value="prefix">Prefixo</SelectItem>
            <SelectItem value="sender">Remetente</SelectItem>
          </SelectContent>
        </Select>
        <Input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder={kind === "keyword" ? "ex: oferta especial" : kind === "prefix" ? "ex: 0900" : "ex: PROMO"}
          aria-label="Valor do padrão"
        />
        <Input
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Descrição (opcional)"
          aria-label="Descrição do padrão"
        />
        <Input
          type="number"
          min={1}
          max={100}
          value={weight}
          onChange={(e) => setWeight(e.target.value)}
          className="font-mono lg:w-24"
          aria-label="Pontos do padrão"
        />
        <Select value={channel} onValueChange={setChannel}>
          <SelectTrigger className="lg:w-32" aria-label="Canal do padrão">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="call">Chamadas</SelectItem>
            <SelectItem value="sms">SMS</SelectItem>
            <SelectItem value="both">Ambos</SelectItem>
          </SelectContent>
        </Select>
        <Button
          onClick={submit}
          disabled={add.isPending}
          className="gap-2 bg-orange-500 text-white hover:bg-orange-600"
        >
          <Plus className="size-4" /> Adicionar
        </Button>
      </section>

      <ListShell isLoading={isLoading} empty="Nenhum padrão configurado">
        {(data ?? []).map((p) => (
          <Row
            key={p.id}
            title={p.value}
            subtitle={p.label ?? "Padrão do motor"}
            tag={`${p.kind} · ${p.channel} · +${p.weight}`}
            onDelete={() => remove.mutate(p.id)}
          />
        ))}
      </ListShell>
    </div>
  );
}

function ListShell({
  isLoading,
  empty,
  children,
}: {
  isLoading: boolean;
  empty: string;
  children: React.ReactNode;
}) {
  if (isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(4)].map((_, i) => (
          <Skeleton key={i} className="h-14 w-full rounded-xl" />
        ))}
      </div>
    );
  }
  const count = Array.isArray(children) ? children.length : 1;
  return (
    <div className="custom-scrollbar max-h-[55vh] space-y-2 overflow-y-auto pr-1">
      {count === 0 ? (
        <div className="glass rounded-2xl p-10 text-center text-sm text-zinc-500">{empty}</div>
      ) : (
        children
      )}
    </div>
  );
}

function Row({
  title,
  subtitle,
  tag,
  tagClass,
  onDelete,
}: {
  title: string;
  subtitle: string;
  tag: string;
  tagClass?: string;
  onDelete: () => void;
}) {
  return (
    <div className="glass flex items-center justify-between gap-3 rounded-xl px-4 py-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm font-semibold">{title}</span>
          <span
            className={cn(
              "rounded bg-white/6 px-1.5 py-0.5 text-[9px] uppercase tracking-wider text-zinc-400",
              tagClass
            )}
          >
            {tag}
          </span>
        </div>
        <p className="truncate text-xs text-zinc-500">{subtitle}</p>
      </div>
      <Button
        variant="ghost"
        size="icon"
        onClick={onDelete}
        aria-label={`Remover ${title}`}
        className="shrink-0 text-zinc-500 hover:text-red-400"
      >
        <Trash2 className="size-4" />
      </Button>
    </div>
  );
}
