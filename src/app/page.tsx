"use client";

import { useState } from "react";
import { ShieldHeader } from "@/components/orange-shield/header";
import { Dashboard } from "@/components/orange-shield/dashboard";
import { Simulator } from "@/components/orange-shield/simulator";
import { Lookup } from "@/components/orange-shield/lookup";
import { History } from "@/components/orange-shield/history";
import { Lists } from "@/components/orange-shield/lists";
import { Rules } from "@/components/orange-shield/rules";
import { Operators } from "@/components/orange-shield/operators";
import { Reports } from "@/components/orange-shield/reports";
import { cn } from "@/lib/utils";
import {
  BarChart3,
  History as HistoryIcon,
  LayoutDashboard,
  ListFilter,
  PhoneCall,
  Radar,
  ShieldCheck,
  SlidersHorizontal,
  Radio,
} from "lucide-react";

const TABS = [
  { id: "dashboard", label: "Painel", icon: <LayoutDashboard className="size-4" /> },
  { id: "simulator", label: "Simulador", icon: <Radar className="size-4" /> },
  { id: "lookup", label: "Identificação", icon: <ShieldCheck className="size-4" /> },
  { id: "history", label: "Histórico", icon: <HistoryIcon className="size-4" /> },
  { id: "lists", label: "Listas", icon: <ListFilter className="size-4" /> },
  { id: "rules", label: "Regras", icon: <SlidersHorizontal className="size-4" /> },
  { id: "operators", label: "Operadoras", icon: <Radio className="size-4" /> },
  { id: "reports", label: "Relatórios", icon: <BarChart3 className="size-4" /> },
];

export default function Home() {
  const [tab, setTab] = useState("dashboard");
  const activeTab = TABS.find((t) => t.id === tab) ?? TABS[0];

  return (
    <div className="flex min-h-screen flex-col">
      <ShieldHeader />

      {/* Tab navigation */}
      <nav
        aria-label="Navegação principal"
        className="sticky top-[65px] z-30 border-b border-white/6 bg-background/75 backdrop-blur-xl"
      >
        <div className="custom-scrollbar mx-auto flex max-w-6xl gap-1 overflow-x-auto px-3 py-2 sm:px-6">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              aria-current={tab === t.id ? "page" : undefined}
              className={cn(
                "flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                tab === t.id
                  ? "bg-orange-500/15 text-orange-300 shadow-[inset_0_0_0_1px_oklch(0.72_0.19_45/0.35)]"
                  : "text-zinc-400 hover:bg-white/5 hover:text-foreground"
              )}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </div>
      </nav>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-5 sm:px-6 sm:py-6" aria-label={activeTab.label}>
        {tab === "dashboard" && <Dashboard onNavigate={setTab} />}
        {tab === "simulator" && (
          <div className="space-y-3">
            <IntroBlock
              title="Simulador do motor"
              description="Teste em tempo real como o SmartCall Engine e o SMS Guard classificam, pontuam e decidem sobre cada comunicação — a mesma lógica que roda localmente no aparelho (CallScreeningService / SMS Role)."
            />
            <Simulator />
          </div>
        )}
        {tab === "lookup" && (
          <div className="space-y-3">
            <IntroBlock
              title="Identificação de números"
              description="Consulte a base de inteligência coletiva: classificação, pontuação de risco, denúncias da comunidade e histórico de triagens do número."
            />
            <Lookup />
          </div>
        )}
        {tab === "history" && (
          <div className="space-y-3">
            <IntroBlock
              title="Histórico e auditoria"
              description="Cada decisão do motor é registrada com pontuação, evidências e justificativa — transparência total sobre por que cada bloqueio ocorreu."
            />
            <History />
          </div>
        )}
        {tab === "lists" && (
          <div className="space-y-3">
            <IntroBlock
              title="Gerenciamento de listas"
              description="Lista negra, lista autorizada e padrões inteligentes de prefixos, palavras-chave e remetentes usados pelo motor de pontuação."
            />
            <Lists />
          </div>
        )}
        {tab === "rules" && (
          <div className="space-y-3">
            <IntroBlock
              title="Regras inteligentes"
              description="Nível de rigor da proteção: limites de decisão e regras específicas para telemarketing, robocalls, cobranças, internacionais e SMS comercial."
            />
            <Rules />
          </div>
        )}
        {tab === "operators" && (
          <div className="space-y-3">
            <IntroBlock
              title="Operadoras"
              description="Regras individuais por remetente das operadoras (Claro, Vivo, TIM, Oi): bloqueie publicidade preservando mensagens de autenticação e alertas."
            />
            <Operators />
          </div>
        )}
        {tab === "reports" && (
          <div className="space-y-3">
            <IntroBlock
              title="Relatórios e denúncias"
              description="Estatísticas semanais de ameaças impedidas e envio de denúncias para a base de inteligência coletiva."
            />
            <Reports />
          </div>
        )}
      </main>

      {/* Sticky footer (mt-auto keeps it at the bottom on short pages) */}
      <footer className="mt-auto border-t border-white/6 bg-black/25">
        <div
          className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-1.5 px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] text-[11px] text-zinc-500 sm:flex-row sm:px-6"
        >
          <p className="flex items-center gap-1.5">
            <PhoneCall className="size-3 text-orange-400" />
            <span className="font-semibold text-zinc-400">ORANGE SHIELD</span> · Smart Call &amp;
            Message Protection · SmartCall Engine v1.0.0
          </p>
          <p>
            Bloqueio local sem internet · Lista autorizada tem prioridade · Proteção reversível ·
            LGPD
          </p>
        </div>
      </footer>
    </div>
  );
}

function IntroBlock({ title, description }: { title: string; description: string }) {
  return (
    <div>
      <h2 className="text-lg font-bold">{title}</h2>
      <p className="max-w-3xl text-xs leading-relaxed text-zinc-500">{description}</p>
    </div>
  );
}
