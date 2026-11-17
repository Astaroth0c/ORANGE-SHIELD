# ORANGE LENS — Feed comunitário de reputação

Feed público consumido pelo app ORANGE SHIELD (funcionalidade **ORANGE LENS**).
Cada entrada é um número denunciado pela comunidade, **revisado manualmente pelo
mantenedor** antes de entrar aqui — o app espelha o feed na base de reputação
local, onde alimenta o **Passo 4 (Reputação) do Spoof Detector** e as decisões
do Call Guard em chamadas reais.

## Fluxo de reporte

1. Usuário toca em **Reportar número** no app → abre uma issue pré-preenchida
   neste repositório com o número e o motivo.
2. O mantenedor avalia a evidência da issue.
3. Se confirmada, o mantenedor adiciona uma entrada em `reputation-feed.json`
   e faz commit. O app sincroniza automaticamente (a cada 6 h ou sob demanda).

## Schema de `entries[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `e164` | string | Número em E.164, ex.: `+5511999999999` |
| `classification` | string | `fraud_confirmed` · `spam_reports` · `telemarketing` · `robocall` · `collector` · `nuisance` · `verified_business` |
| `reportCount` | int | Quantidade de denúncias revisadas |
| `confidence` | number | 0.0–1.0 — confiança do mantenedor na denúncia |
| `notes` | string | Resposta curta e objetiva do ocorrido (sem dados pessoais) |
| `reportedAt` | int | Timestamp UNIX (ms) da revisão |
| `version` | string | Versão do schema (hoje `1`) |

## Efeito no motor (pesos reais do Spoof Detector)

- `fraud_confirmed` → **+100** no spoof score (spoofing provável, Call Guard bloqueia conforme thresholds)
- `reportCount > 0` → **+10 a +40** ponderado pela confiança
- `robocall` / `telemarketing` / `collector` → **+30**
- `nuisance` (incômodo recorrente: volume alto de recusadas/não atendidas) → **+25**
- `verified_business` → reputação positiva (não adiciona risco)

## Compromissos

- Somente números e classificações — **nunca** nomes, endereços, CPF ou dados cadastrais.
- Toda entrada precisa citar a issue de origem no commit.
- O mantenedor pode remover entradas contestadas (basta abrir issue pedindo revisão).

---

## ORANGE CHAIN — cadeia comunitária de números restritos (v2.10.0)

Cada aparelho ORANGE SHIELD é um **nó completo** da rede. Sem servidor dedicado:

- **Estrutura**: blockchain real — blocos encadeados por SHA-256, Merkle root das denúncias, cada denúncia assinada com ECDSA P-256 pela chave do aparelho (pseudônima: `reporterId` = SHA-256 da chave pública).
- **Gênese**: determinístico e canônico (`GENESIS_HASH` = `78c5e9d3...c05c5b4e`) — idêntico no app, no repositório e em todos os nós.
- **Distribuição**: `community/chain.json` (leitura anônima por todos os nós via raw).
- **Publicação**: nó monta bloco local assinado → issue com label `orange-chain` → o validador comunitário (CI) verifica encadeamento + Merkle + assinaturas ECDSA e anexa o bloco.
- **Consenso local**: todo nó valida a cadeia INTEIRA antes de aceitar (gênese canônico, encadeamento, Merkle, assinatura de cada entrada). Cadeia adulterada é rejeitada e reportada.
- **Mesh local**: aparelhos na mesma rede Wi-Fi trocam a cadeia direto (NSD `_orangeshield._tcp` + TCP 47615), sempre passando pelo consenso local.
- **Efeito real**: denúncias aceitas são espelhadas na reputação local → o número passa a ser bloqueado/alertado nos outros aparelhos.

Auditoria: qualquer pessoa pode recomputar hashes e verificar assinaturas (`chain_validator.py` usa o mesmo algoritmo canônico do app).
