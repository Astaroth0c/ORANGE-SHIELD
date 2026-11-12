# 🛡️ ORANGE SHIELD

**Proteção inteligente contra chamadas, SMS e mensagens indesejadas — com detecção de spoofing.**

ORANGE SHIELD é um aplicativo de bloqueio inteligente baseado em evidências, implementando um motor de decisão com **score de risco 0–100**, **explicabilidade total** (cada decisão mostra as evidências que a geraram) e **quatro engines especializados**.

---

## 🧩 Arquitetura — Os 4 Engines

| Engine | Nome | Função | Decisões |
|--------|------|--------|----------|
| **01** | 📞 **Call Guard** | Triagem inteligente de chamadas | `ALLOW` · `WARN` · `SILENCE` · `BLOCK` |
| **02** | 💬 **SMS Guard** | Classificação e filtro de SMS | `ALLOW` · `QUARANTINE` · `BLOCK` |
| **03** | 🟢 **WhatsApp Guard** | Filtragem de mensagens do WhatsApp | `ALLOW` · `QUARANTINE` · `BLOCK` |
| **04** | 🎭 **Spoof Detector** | Identificação de caller ID falsificado | Flags + score de spoofing |

### Engine 01 · Call Guard
Fluxo de triagem por prioridade (especificação §12):
1. Normalização do número (E.164)
2. Proteções prioritárias — emergência, autorizados → **ALLOW** imediato
3. Bloqueios explícitos — lista negra pessoal, golpe confirmado → **BLOCK** direto
4. Pontuação de risco — prefixos Anatel (0303), reputação, denúncias coletivas, insistência
5. Decisão por thresholds configuráveis
6. Registro de auditoria com evidências

### Engine 02 · SMS Guard
- Análise de remetente (curto comercial, alfanumérico, número real)
- Análise de conteúdo (catálogo de padrões de golpe brasileiros)
- **Regra de segurança:** mensagens de autenticação (OTP/bancos) **nunca** são descartadas por conterem links
- Regras por operadora (Claro · Vivo · TIM · Oi)

### Engine 03 · WhatsApp Guard
- Catálogo de golpes específicos do WhatsApp: phishing do código de 6 dígitos, falso parente "troquei de número" + PIX, links suspeitos, mensagens reencaminhadas em massa
- Integração com o Spoof Detector (números não-BR, sintéticos)
- Pontos de plug reais no Android: `NotificationListenerService` + checagens de contatos

### Engine 04 · Spoof Detector
Checagens de manipulação de caller ID:
- `NEIGHBOR_SPOOF` — imita o número do usuário (mesmo DDD/prefixo/sufixo)
- `INVALID_DDD` — DDD inexistente no plano da ANATEL
- `BAD_LENGTH` — nono dígito ausente (formato pré-2012) ou fixo com 9 dígitos
- `REPEATED_DIGITS` / `SEQUENTIAL_DIGITS` — padrões sintéticos gerados por software
- `RAPID_REDIAL` — padrão Wangiri ("1 toque e desliga", tarifa premium)

---

## ⚙️ Tecnologia

- **Next.js 16** (App Router) + **TypeScript 5**
- **Prisma ORM** + SQLite (schema completa: números, reputação, eventos, denúncias, padrões, regras de operadora)
- **Tailwind CSS 4** + **shadcn/ui** (New York) + Lucide Icons
- API Routes para todos os engines (simulação de triagem em tempo real)

## 🚀 Como rodar

```bash
bun install
bun run db:push   # cria o schema no SQLite
bun run db:generate
bun run dev       # http://localhost:3000
```

Seed de dados de demonstração:

```bash
bunx tsx prisma/seed.ts
```

## 📡 API

| Rota | Método | Descrição |
|------|--------|-----------|
| `/api/engine/call` | POST | Triagem de chamada (Engine 01 + 04) |
| `/api/engine/sms` | POST | Classificação de SMS (Engine 02) |
| `/api/engine/whatsapp` | POST | Triagem de mensagem WA (Engine 03 + 04) |
| `/api/spoof` | POST | Análise anti-spoofing dedicada (Engine 04) |
| `/api/lookup` | POST | Consulta de reputação de número |
| `/api/blacklist` · `/api/whitelist` | GET/POST/DELETE | Listas pessoal |
| `/api/reports` | GET/POST | Denúncias coletivas |
| `/api/patterns` · `/api/operators` | GET | Inteligência (padrões e regras) |
| `/api/history/*` | GET | Histórico (chamadas, SMS, WhatsApp) |
| `/api/stats` | GET | Estatísticas agregadas |
| `/api/settings` | GET/PATCH | Configurações e thresholds |

## 📱 Painéis

- **Dashboard** — estatísticas, proteção em tempo real
- **Simulador** — teste os 4 engines com qualquer número/mensagem
- **Histórico** — chamadas, SMS e WhatsApp triados com evidências
- **Listas** — bloqueados e autorizados
- **Denúncias** — reclamação coletiva que alimenta a reputação
- **Regras** — thresholds, toggles por categoria, regras de operadora
- **Anti-Spoofing** — análise dedicada de caller ID

## 🔒 Regra de ouro

> **Nenhum número importante é bloqueado silenciosamente.** Toda decisão é explicável (evidências + score + passos do motor), prioridades de proteção vêm antes de bloqueios, e mensagens de autenticação nunca são descartadas.

---

📄 *Referência da lógica original:* [`docs/logica-de-bloqueio-referencia.md`](docs/logica-de-bloqueio-referencia.md)
