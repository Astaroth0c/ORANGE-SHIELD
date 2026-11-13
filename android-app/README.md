# ORANGE SHIELD — App Android (cliente on-device)

Cliente Android do ORANGE-SHIELD: triagem inteligente de chamadas com
**detector de spoofing em 4 passos**, guardião de notificações do WhatsApp,
listas pessoais e painel local. Toda a inteligência do console web
(`src/lib/engine/*.ts`) foi portada para Kotlin e roda **100% on-device** —
sem rede, sem telemetria, sem aleatoriedade (motor determinístico).

- **Pacote:** `com.orangeshield.app`
- **Versão:** 1.0.0 (versionCode 5) — **APK de release assinado** em [`../download/ORANGE-SHIELD-v1.0.0.apk`](../download/ORANGE-SHIELD-v1.0.0.apk) e nas Releases do GitHub
- **Stack:** Kotlin 2.0.20 · AGP 8.5.2 · Jetpack Compose (Material3) · Room 2.6.1 (KSP) · corrotinas
- **SDK:** compileSdk/targetSdk 34 · minSdk 29 (Android 10+ — necessário para `CallScreeningService` + `RoleManager`)
- **Idioma:** pt-BR em toda a UI

---

## 1. Instalação do APK (sem Android Studio)

1. Baixe `ORANGE-SHIELD-v1.0.0.apk` (pasta `download/` do repositório ou a aba **Releases**).
2. Toque no APK no aparelho (Android 10+) e autorize a instalação de fontes desconhecidas.
3. Abra o app e ative, na aba **Painel**: **Triagem de chamadas** (diálogo do sistema),
   **Acesso a notificações** (Guardião WhatsApp) e **Notificações do app** (Android 13+).

> **Sem dados simulados:** histórico, reputação e estatísticas refletem apenas
> chamadas e mensagens reais processadas no aparelho. A aba **Análise** roda o
> motor em modo **somente leitura** (não grava nada no histórico).

SHA-256 do APK v1.0.0:
`44bbb854279b2ee30848bb013014fb940b246fc14f0a6dca4f78e7996ce16ada`

**Atenção à assinatura:** o APK v1.0.0 foi assinado com uma chave que esteve armazenada no repositório público. Considere essa chave comprometida. A v1.1.0 deve usar credenciais protegidas como secrets do ambiente de build. Uma chave nova impede a atualização direta sobre a v1.0.0: será necessário exportar os dados, desinstalar a versão antiga e instalar a nova. Não publique novas versões com a chave antiga.

### APK de desenvolvimento v1.1.0

A branch `development/v1.1.0` gera um APK de teste pelo GitHub Actions. O build `debug` usa o pacote `com.orangeshield.app.dev`, então pode ser instalado **ao lado** da versão estável `com.orangeshield.app`, sem desinstalá-la e sem misturar os bancos locais. O APK aparece em **Actions → Android development APK → Artifacts** após o workflow concluir.

O APK de desenvolvimento usa assinatura debug e não substitui a versão de produção. Para builds de release, configure `ORANGE_KEYSTORE_PATH`, `ORANGE_KEYSTORE_PASSWORD`, `ORANGE_KEY_ALIAS` e `ORANGE_KEY_PASSWORD` como variáveis seguras do ambiente de build. Não armazene a keystore nem senhas no Git.

## 2. Pré-requisitos (para compilar)

| Ferramenta | Versão recomendada |
|---|---|
| JDK | 17 |
| Android SDK | platform 34 + build-tools 34.0.0 |
| Gradle | 8.7 (via wrapper versionado no repo) |

## 3. Como buildar

O wrapper está versionado (`gradlew` + `gradle-wrapper.jar`) — não precisa de
Gradle instalado:

```bash
cd android-app
./gradlew assembleDebug      # APK debug isolado (.dev) em app/build/outputs/apk/debug/
./gradlew assembleRelease    # APK release ASSINADO em app/build/outputs/apk/release/
./gradlew installDebug       # instala no dispositivo/emulador conectado
```

No Android Studio: abra a pasta `android-app/` como projeto e rode normalmente.
O build resolve dependências de `google()` e Maven Central; em IPs com rate-limit
(429), o projeto já inclui o espelho Aliyun como reserva (ver `settings.gradle.kts`).

## 4. Fluxos de permissão (pt-BR, executados no app)

| Permissão | Como conceder | Onde no app |
|---|---|---|
| **Triagem de chamadas** (`ROLE_CALL_SCREENING`) | Diálogo nativo do `RoleManager` (Android 10+). Enquanto o papel não estiver ativo, o sistema não invoca o serviço. | Painel → “Ativar triagem de chamadas” |
| **Acesso a notificações** (NotificationListenerService) | `Configurações → Acesso a notificações` (`ACTION_NOTIFICATION_LISTENER_SETTINGS`). | Painel / WhatsApp → “Permitir acesso a notificações” |
| **Notificações do app** (`POST_NOTIFICATIONS`, Android 13+) | Diálogo de runtime. Sem ela os alertas de bloqueio ficam silenciados (a triagem continua funcionando). | Painel → “Conceder permissão de notificações” |
| Permissões sensíveis de telefone e internet | Não são solicitadas nesta versão: a triagem usa o papel nativo `ROLE_CALL_SCREENING` e o processamento permanece local. | — |

## 5. Arquitetura

```
android-app/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties / gradle/wrapper/
└─ app/
   ├─ build.gradle.kts · proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml                # permissões + CallScreeningService + NotificationListenerService
      ├─ res/                               # ícone escudo, strings, tema escuro
      └─ java/com/orangeshield/app/
         ├─ EngineProvider.kt               # DI manual (sem Hilt): banco + motores + settings
         ├─ SettingsStore.kt                # EngineSettings persistido em SharedPreferences
         ├─ data/                           # Room: entidades, DAOs, AppDatabase (v1)
         ├─ engine/                         # PORTA DOS MOTORES DO CONSOLE WEB
         │  ├─ Normalize.kt                 # espelho de normalize.ts
         │  ├─ SpoofDetector.kt             # espelho de spoof.ts (pipeline de 4 passos)
         │  ├─ CallRuleEngine.kt            # espelho de call-engine.ts (SmartCall Engine)
         │  ├─ WhatsAppEngine.kt            # catálogo determinístico de golpes de WhatsApp
         │  ├─ EngineTypes.kt               # espelho de types.ts (settings, evidências, faixas)
         │  └─ EngineJson.kt                # serialização de evidências (org.json)
         ├─ notify/Notifier.kt              # canais + alertas locais
         ├─ screening/CallScreeningServiceImpl.kt   # android.telecom.CallScreeningService
         ├─ whatsapp/WhatsAppGuardService.kt        # NotificationListenerService
         └─ ui/                             # Compose Material3 (tema laranja escuro)
            ├─ Theme.kt · MainActivity.kt
            ├─ PainelScreen.kt · AnaliseScreen.kt
            ├─ ListasScreen.kt · WhatsAppScreen.kt
```

### Decisões de engenharia

- **Permissão de triagem:** o serviço exige exatamente `android.permission.BIND_SCREENING_SERVICE`, conforme `Manifest.permission.BIND_SCREENING_SERVICE` do Android. Não use `android.telecom.permission...`.

- **Privacidade local:** o manifesto remove permissões de leitura do histórico de chamadas, estado do telefone, inicialização automática e internet que não eram utilizadas. `allowBackup=false` evita incluir histórico e configurações em backups automáticos do Android.
- **Sem Hilt:** `EngineProvider` (object) entrega banco/motores/configurações; os
  serviços chamam `EngineProvider.init(this)` no `onCreate`.
- **Fail-open na triagem:** qualquer erro/timeout (2,5 s) em `onScreenCall`
  responde **permitindo** a chamada — nunca bloqueamos por falha.
- **Chamadas bloqueadas** ficam no registro de chamadas (`skipCallLog = false`)
  e sem notificação nativa (`skipNotification = true`, pois o app alerta).
- **Modo SILENCE:** a decisão agora também chama `setSilenceCall(true)` no Android; a chamada continua recebida, mas o toque é silenciado. O app registra uma notificação informativa.
- **WhatsApp:** não existe API pública de bloqueio automático; o guardião
  classifica, alerta e abre o deep link `wa.me` para bloqueio manual
  (`buildBlockGuidanceIntent`). Acessos concedidos valerão para a detecção de
  mensagens, não para interferir no app.

## 6. O pipeline de 4 passos (idêntico ao console web)

| Passo | Nome | O que valida | Flags principais (pesos) |
|---|---|---|---|
| 1 | **FORMATO** | DDD da ANATEL, regra do nono dígito (9 celular / 8 fixo), repetição em massa (≥6) e sequências artificiais (≥8); E.164 8–15 dígitos para internacionais | `INVALID_DDD` 30 · `BAD_LENGTH` 25 · `BAD_INTL_LENGTH` 25 · `REPEATED_DIGITS` 25 · `SEQUENTIAL_DIGITS` 20 |
| 2 | **APRESENTAÇÃO** | DDD 00 após +55, excesso de dígitos (>11 nacionais), BR mascarado atrás de código estrangeiro, neighbor spoofing (mesmo DDD + 5 dígitos ou sufixo ≥7 do seu número) | `INTERNATIONAL_ANOMALY` 35 · `EXCESS_LENGTH` 30 · `INTL_MASKED_BR` 25 · `NEIGHBOR_SPOOF` 35 |
| 3 | **FREQUÊNCIA** | Rediscagem Wangiri (≥3 triagens/15 min) e onda de disparo por DDD (≥6 números distintos e ≥10 triagens/60 min) | `RAPID_REDIAL` 40 · `PREFIX_BURST` 30 |
| 4 | **REPUTAÇÃO** | Fraude confirmada (+100), denúncias coletivas `min(40, 10+10×denúncias) × confiança`, classificação robocall/telemarketing/collector (+30), empresa verificada (reduz suspeita) | `REPUTATION_FRAUD` 100 · `REPUTATION_REPORTS` 40 · `REPUTATION_CLASS` 30 |

**Veredito de spoofing:** score ≥ 55 → “Spoofing provável”; ≥ 25 → “Indícios de
spoofing”; senão “Nenhum indício de spoofing”.

**Decisão do Call Guard:** `score ≥ blockThreshold (70)` → BLOCK; modo silêncio +
número desconhecido → SILENCE; `score ≥ warnThreshold (30)` → WARN; senão ALLOW.
Evidências somadas: prefixo 0303 (+90), telemarketing (+90), robocall (+95),
cobrança (+45), denúncias (máx. 60, ponderadas pela confiança), internacional
(+100), shortcode (+25), insistência (+30/+50), política de desconhecidos (+100),
empresa verificada (−40), flags de spoofing (pesos do Passo 1–4).

## 7. Limitações conhecidas

- **Bloqueio automático de WhatsApp é impossível** via API pública — o fluxo é
  análise + alerta + orientação de bloqueio manual (deep link `wa.me`).
- **STIR/SHAKEN não está disponível no Brasil** — o Passo 2 (apresentação) e o
  Passo 4 (reputação local) cobrem a detecção de caller ID forjado com as
  evidências disponíveis no aparelho.
- **Android 10+ (minSdk 29):** `CallScreeningService` com `RoleManager` exige
  Android 10; versões anteriores não são suportadas.
- O marcador “encaminhada muitas vezes” do WhatsApp não é exposto pela API de
  notificações; usamos um marcador textual determinístico quando presente.
- Reputação é **local** (base do dispositivo); a sincronização coletiva
  multi-dispositivo fica para uma fase futura.

## 8. Correspondência com os motores do console web

| Console web (TypeScript) | App Android (Kotlin) | Observações |
|---|---|---|
| `src/lib/engine/normalize.ts` | `engine/Normalize.kt` | Porta 1:1 (normalização E.164, shortcodes, 0303/0800/0300, alfanuméricos) |
| `src/lib/engine/call-engine.ts` | `engine/CallRuleEngine.kt` | Mesmo fluxo, pesos e textos pt-BR; decisão persistida em Room (`CallEventEntity`) |
| `src/lib/engine/spoof.ts` | `engine/SpoofDetector.kt` | Pipeline de 4 passos idêntico (pesos, DDDs da ANATEL, neighbor spoofing) |
| `src/lib/engine/whatsapp-engine.ts` | `engine/WhatsAppEngine.kt` | Catálogo determinístico pt-BR (código de 6 dígitos, falso parente + PIX, links, prêmio, cadeias, PIX urgente) |
| `src/lib/engine/types.ts` | `engine/EngineTypes.kt` + `SettingsStore.kt` | `EngineSettings` com os mesmos padrões; `ruleVersion "v1.2.0"` |
| Console (React) | `ui/*.kt` (Compose) | Painel ↔ Dashboard, Análise ↔ Simulator (somente leitura), Listas ↔ Lists, WhatsApp ↔ WhatsApp |

## 9. Testando rapidamente (aba Análise — somente leitura)

A aba **Análise** roda o motor real sobre qualquer número informado **sem gravar
nada** no histórico (parâmetro `record = false`); as estatísticas do Painel
continuam refletindo apenas chamadas reais triadas pelo serviço.

- `+55 11 99999-9999` → checagens OK (se o seu número estiver configurado, o
  neighbor spoofing também é avaliado).
- `+550011999999999` → `INTERNATIONAL_ANOMALY` (+35).
- `+1 999 999 99999` (formato inválido) → `BAD_INTL_LENGTH`.
- `0303 317 2000` → telemarketing Anatel (+90 com a regra ativa).
