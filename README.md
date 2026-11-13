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

### Engine 04 · Spoof Detector — pipeline de 4 passos (v0.6.0)
Identificação de caller ID falsificado em **quatro passos encadeados**:

| Passo | Nome | O que detecta |
|-------|------|---------------|
| **1** | 🧾 **Formato do número** | `INVALID_DDD` (DDD fora do plano ANATEL) · `BAD_LENGTH` (nono dígito ausente/fixo com 9) · `REPEATED_DIGITS` / `SEQUENTIAL_DIGITS` (padrões sintéticos) · `BAD_INTL_LENGTH` (E.164 fora de 8–15 dígitos) |
| **2** | 🌍 **Apresentação internacional** | `INTERNATIONAL_ANOMALY` (número local exibido como "+5500…") · `EXCESS_LENGTH` (dígitos excedentes no plano nacional) · `INTL_MASKED_BR` (número BR mascarado atrás de código estrangeiro) · `NEIGHBOR_SPOOF` (imita o número do usuário) |
| **3** | 🔁 **Frequência de chamadas** | `RAPID_REDIAL` (Wangiri — "1 toque e desliga", ≥3 triagens em 15 min) · `PREFIX_BURST` (onda de disparo: ≥6 números distintos do mesmo DDD em 1h) |
| **4** | 🗂️ **Reputação do número** | `REPUTATION_FRAUD` (golpe confirmado) · `REPUTATION_REPORTS` (denúncias coletivas ponderadas por confiança) · `REPUTATION_CLASS` (robocall/telemarketing/collector) · `REPUTATION_VERIFIED` (reputação positiva) |

Cada passo retorna `pass`/`flagged`/`info` com evidências individuais; o **spoof score 0–100**
soma todos os flags e alimenta o Call Guard (`spoof score ≥ 55` = spoofing provável).

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
| `/api/spoof/history` | GET | Inteligência agregada de spoofing (técnicas + ataques) |
| `/api/lookup` | POST | Consulta de reputação de número |
| `/api/blacklist` · `/api/whitelist` | GET/POST/DELETE | Listas pessoal |
| `/api/reports` | GET/POST | Denúncias coletivas |
| `/api/patterns` · `/api/operators` | GET | Inteligência (padrões e regras) |
| `/api/history/*` | GET | Histórico (chamadas, SMS, WhatsApp) |
| `/api/stats` | GET | Estatísticas agregadas + inteligência (decisões, canais, ameaças, insights) |
| `/api/settings` | GET/PATCH | Configurações e thresholds |
| `/api/backup` | GET/POST | Exportar/restaurar backup completo em JSON (envelope versionado) |

## 📱 Painéis

- **Dashboard** — estatísticas, proteção em tempo real, inteligência (decisões 7d, canais atacados, insights automáticos, principais ameaças)
- **Simulador** — teste os 4 engines com qualquer número/mensagem
- **Histórico** — chamadas, SMS e WhatsApp triados com evidências
- **Listas** — bloqueados e autorizados
- **Denúncias** — reclamação coletiva que alimenta a reputação
- **Regras** — thresholds, toggles por categoria, regras de operadora, **backup/restauração JSON**
- **Anti-Spoofing** — análise dedicada de caller ID com o **pipeline visual dos 4 passos** + inteligência de técnicas detectadas

## 🤖 App Android (`android-app/`)

Cliente Android nativo (Kotlin + Jetpack Compose, Material 3 tema laranja escuro) que roda a mesma inteligência **no aparelho**, com Room como base local. **v1.0.0 preservada como backup histórico**. A linha de melhorias está na branch `work/v1.1-security-observatory`; não use a assinatura antiga para novas versões.

| Componente | Papel |
|------------|-------|
| `CallScreeningServiceImpl` | Triagem real de chamadas (Android 10+): `BLOCK` → rejeita e notifica; `WARN` → alerta; fail-open (erro nunca bloqueia) |
| `WhatsAppGuardService` | `NotificationListenerService` filtra mensagens do WhatsApp, alerta suspeitas e orienta o **bloqueio manual** (deep link `wa.me`) |
| `engine/SpoofDetector.kt` | Os **mesmos 4 passos** anti-spoofing do console web (pesos e textos idênticos) |
| `engine/CallRuleEngine.kt` | Call Guard com score 0–100, evidências e thresholds `v1.2.0` |
| `engine/WhatsAppEngine.kt` | Catálogo determinístico de golpes BR: phishing do código de 6 dígitos, falso parente + PIX, links, prêmios |
| `ui/` | Painel (stats + ativação de permissões), **Análise** (4 passos visuais, somente leitura), Listas, WhatsApp |

### 📲 Instalação do APK (sem Android Studio)

A v1.0.0 é somente backup histórico e pode ser bloqueada pelo Play Protect por declarar acesso a notificações. A v1.1 mantém o Guardião WhatsApp com consentimento explícito e deve ser distribuída por teste interno da Play Store após revisão.
2. No aparelho (Android 10+), toque no arquivo e autorize "Instalar de fontes desconhecidas" para o navegador/gerenciador de arquivos.
3. Abra o app e conceda, na aba **Painel**:
   - **Triagem de chamadas** (diálogo nativo do sistema);
   - **Acesso a notificações** (para o Guardião WhatsApp);
   - **Notificações do app** (Android 13+).
4. Pronto: as chamadas passam a ser triadas on-device (score 0–100, 4 passos) — **sem dados simulados**: histórico, reputação e estatísticas refletem apenas chamadas reais do aparelho.

SHA-256 do APK v1.0.0: `44bbb854279b2ee30848bb013014fb940b246fc14f0a6dca4f78e7996ce16ada`

Build v1.1: `cd android-app && ./gradlew assembleRelease` (JDK 17 + Android SDK 36). A assinatura deve vir de segredos privados do GitHub Actions; nenhuma keystore deve ser commitada. A chave v1.0.0 foi exposta no repositório e não deve ser reutilizada. Detalhes em [`android-app/README.md`](android-app/README.md).

## 🗺️ Histórico de versões

| Versão | Conteúdo |
|--------|----------|
| v0.1.0 – v0.3.0 | Console web + Engine 01/02 + inteligência no dashboard |
| v0.4.0 | Inteligência anti-spoofing (Engine 04) |
| v0.5.0 | Backup & portabilidade JSON |
| v0.6.0 | **Pipeline anti-spoofing em 4 passos** (formato → apresentação internacional → frequência → reputação) + normalização internacional correta |
| v0.7.0 | **App Android nativo** (CallScreening + WhatsApp Guard + Room + 4 passos) |
| v0.8.0 | **Sem dados simulados**: aba Análise somente leitura (não grava no histórico real); correção da permissão `BIND_SCREENING_SERVICE` e de `CallResponse` |
| v0.9.0 | Responsividade/alinhamento da UI (Compose), ícone adaptativo, gradle wrapper versionado |
| v0.9.1 | Correções do primeiro build executado (compilação limpa) + repositórios Maven resilientes |
| v1.0.0 | **APK de release assinado** + CI do Android no GitHub Actions + documentação de instalação |

## 🔒 Regra de ouro

> **Nenhum número importante é bloqueado silenciosamente.** Toda decisão é explicável (evidências + score + passos do motor), prioridades de proteção vêm antes de bloqueios, e mensagens de autenticação nunca são descartadas.

---

📄 *Referência da lógica original:* [`docs/logica-de-bloqueio-referencia.md`](docs/logica-de-bloqueio-referencia.md)
