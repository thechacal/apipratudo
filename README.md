# API Pra Tudo

API Pra Tudo e uma plataforma de APIs em Java/Spring Boot com contrato publico estavel em `/v1/*` exposto exclusivamente pelo api-gateway. O produto combina onboarding rapido, controle de cota e monetizacao por creditos, alem de um novo SaaS de cobranca (PIX + recorrencia) para clientes emitirem e gerenciarem cobrancas.

## Visao geral (produto e proposta)

- Contrato publico estavel em `/v1/*` (gateway unico).
- Autenticacao por `X-Api-Key` em rotas protegidas.
- Plano FREE com limites baixos para testes.
- Recarga por creditos via PIX (billing-service).
- Novo: SaaS de cobranca com clientes, cobrancas, PIX, webhooks e relatorios (billing-saas-service).
- Diferenciais: idempotencia, multi-tenant, webhook seguro, rastreio de cobrancas, link de WhatsApp para cobranca.

## Contrato publico e OpenAPI

- Contrato oficial exposto em `/v1/*` pelo api-gateway.
- Fonte do OpenAPI no repo: `services/api-gateway/src/main/resources/static/openapi.yaml`.
- Swagger do gateway em `/docs`.

## Modelos de monetizacao

### API publica (creditos)
- FREE: limite diario + rpm para testes.
- Pacotes de creditos: START, PRO, SCALE.
- Valores e creditos:
  - START: R$ 19,90 -> 50.000 creditos
  - PRO: R$ 49,90 -> 200.000 creditos
  - SCALE: R$ 99,90 -> 500.000 creditos
- Cada request consome 1 credito quando houver saldo.
- Quando `credits.remaining` chega a 0, volta automaticamente para FREE.
- Ao estourar FREE ou acabar creditos, o gateway responde HTTP 402 com `QUOTA_EXCEEDED` (nao alterar o payload).
- O upgrade por creditos e feito via `billing-service` (PIX).

### SaaS de cobranca (billing-saas)
- Cliente cria seus clientes e cobrancas via API.
- Gera PIX, recebe webhook, consulta status e relatorios.
- Recorrencia mensal: ao receber `PAID`, a proxima cobranca e gerada automaticamente.

## Arquitetura (alto nivel)

### Servicos e portas locais (default)
- api-gateway: 8080
- quota-service: 8081
- webhook-service: 8082
- federal-results-service: 8083
- lotofacil-results-service: 8084
- megasena-results-service: 8085
- quina-results-service: 8086
- lotomania-results-service: 8087
- timemania-results-service: 8088
- duplasena-results-service: 8089
- loteca-results-service: 8090
- diadesorte-results-service: 8091
- supersete-results-service: 8092
- maismilionaria-results-service: 8093
- developer-portal-service: 8094
- billing-service (PIX/creditos): 8095
- billing-saas-service (SaaS cobranca): 8096

### Fluxo entre servicos
- Cliente -> api-gateway (/v1/*).
- api-gateway -> quota-service (consume/refund/status).
- api-gateway -> developer-portal-service (key request, upgrade, status).
- developer-portal-service -> quota-service (criar key FREE, status).
- developer-portal-service -> billing-service (PIX creditos).
- api-gateway -> billing-saas-service (clientes, cobrancas, pix, relatorios).
- webhook externo -> api-gateway -> billing-saas-service (POST /v1/pix/webhook).

### Headers importantes
- Cliente -> gateway: `X-Api-Key`
- Gateway -> billing-saas: `X-Tenant-Id` (SHA-256 hex da API key)
- Gateway -> billing-saas: `X-Service-Token`
- Webhook externo -> gateway -> billing-saas: `X-Webhook-Secret`

### Quota e excecao de webhook
- Quota aplicada pelo gateway em rotas `/v1/*`.
- Excecao estrita: **somente** `POST /v1/pix/webhook` nao exige `X-Api-Key` e nao consome quota.

## Pre-requisitos (dev)

- Java 21
- Maven
- curl
- python (usado pelo smoke script)
- Docker (opcional)
- Firestore local: opcional (em dev, use `APP_FIRESTORE_ENABLED=false`)

## Rodar localmente (passo a passo)

Este repo nao tem reactor root. Todos os comandos devem usar `-f services/<modulo>/pom.xml`.

### Mapa de variaveis (quem usa o que)

| Variavel | Servico(s) | Funcao | Exemplo |
| --- | --- | --- | --- |
| APP_FIRESTORE_ENABLED | quota, portal, api-gateway, billing-saas, billing-service | alterna Firestore/InMemory | false |
| APP_FIRESTORE_PROJECT_ID ou GOOGLE_CLOUD_PROJECT | todos os servicos com Firestore | projeto do Firestore | api-pra-tudo |
| APP_FIRESTORE_EMULATOR_HOST ou FIRESTORE_EMULATOR_HOST | todos os servicos com Firestore | host do emulador | localhost:8089 |
| APP_ADMIN_TOKEN ou ADMIN_TOKEN | quota-service | token admin (X-Admin-Token) | changeme |
| APP_INTERNAL_TOKEN ou INTERNAL_TOKEN | quota-service | token interno (X-Internal-Token) | changeme |
| APP_PORTAL_TOKEN ou PORTAL_TOKEN | quota-service | token do portal (X-Portal-Token) | changeme |
| QUOTA_INTERNAL_TOKEN | api-gateway | token interno enviado ao quota-service | changeme |
| QUOTA_BASE_URL | api-gateway, portal, billing-service | base do quota-service | localhost:8081 |
| PORTAL_BASE_URL | api-gateway | base do developer-portal-service | localhost:8094 |
| PORTAL_TOKEN | developer-portal-service | usado no X-Portal-Token ao criar key | changeme |
| BILLING_BASE_URL | developer-portal-service | base do billing-service | localhost:8095 |
| BILLING_SERVICE_TOKEN | developer-portal-service, billing-service | X-Service-Token do billing-service | changeme |
| BILLING_SAAS_BASE_URL | api-gateway | base do billing-saas-service | localhost:8096 |
| BILLING_SAAS_SERVICE_TOKEN | api-gateway, billing-saas-service | X-Service-Token do billing-saas | changeme |
| BILLING_SAAS_WEBHOOK_SECRET | billing-saas-service | segredo do webhook PIX | changeme |
| WEBHOOK_SECRET | billing-service | segredo de webhook PagBank | changeme |
| PAGBANK_BASE_URL | billing-service | base da API PagBank | sandbox.api.pagseguro.com |
| PAGBANK_TOKEN | billing-service | token PagBank | changeme |
| PAGBANK_WEBHOOK_TOKEN | billing-service | token de assinatura do webhook PagBank | changeme |
| PAGBANK_NOTIFICATION_URL | billing-service | URL publica do webhook PagBank | gateway/public-webhook |
| START_PRICE_CENTS / START_CREDITS | developer-portal-service | pacote START | 1990 / 50000 |
| PRO_PRICE_CENTS / PRO_CREDITS | developer-portal-service | pacote PRO | 4990 / 200000 |
| SCALE_PRICE_CENTS / SCALE_CREDITS | developer-portal-service | pacote SCALE | 9990 / 500000 |
| APP_RATE_LIMIT_IP_RPM | developer-portal-service | rate limit por IP | 10 |
| APP_RATE_LIMIT_EMAIL_PER_DAY | developer-portal-service | rate limit por email | 1 |
| APP_RATE_LIMIT_ORG_PER_DAY | developer-portal-service | rate limit por org | 3 |
| GATEWAY_URL | developer-portal-service | URL do gateway usada nas respostas | localhost:8080 |
| DOCS_URL | developer-portal-service | URL dos docs usada nas respostas | localhost:8080/docs |
| APP_PLANS_FREE_REQUESTS_PER_MINUTE | quota-service | limite FREE rpm | 30 |
| APP_PLANS_FREE_REQUESTS_PER_DAY | quota-service | limite FREE dia | 200 |
| APP_PLANS_PREMIUM_REQUESTS_PER_MINUTE | quota-service | limite premium rpm | 600 |
| APP_PLANS_PREMIUM_REQUESTS_PER_DAY | quota-service | limite premium dia | 50000 |

Variaveis adicionais (timeouts, colecoes, base URLs de resultados) estao em `application.yml` de cada servico.
Para evitar confusao de tokens: `X-Internal-Token` e validado no quota-service, `X-Portal-Token` e enviado pelo portal, e o gateway envia `QUOTA_INTERNAL_TOKEN` para consumir/refund quota.

### Minimo para rodar local (stack minimo)

#### quota-service
```bash
export APP_FIRESTORE_ENABLED=false
export APP_INTERNAL_TOKEN=changeme
export PORTAL_TOKEN=changeme
mvn -B -ntp -f services/quota-service/pom.xml spring-boot:run
```

#### developer-portal-service
```bash
export APP_FIRESTORE_ENABLED=false
export QUOTA_BASE_URL=http://localhost:8081
export PORTAL_TOKEN=changeme
# Para usar /v1/keys/upgrade local:
export BILLING_BASE_URL=http://localhost:8095
export BILLING_SERVICE_TOKEN=changeme
mvn -B -ntp -f services/developer-portal-service/pom.xml spring-boot:run
```

#### billing-saas-service
```bash
export APP_FIRESTORE_ENABLED=false
export BILLING_SAAS_SERVICE_TOKEN=changeme
export BILLING_SAAS_WEBHOOK_SECRET=changeme
mvn -B -ntp -f services/billing-saas-service/pom.xml spring-boot:run
```

#### api-gateway
```bash
export QUOTA_BASE_URL=http://localhost:8081
export QUOTA_INTERNAL_TOKEN=changeme
export PORTAL_BASE_URL=http://localhost:8094
export BILLING_SAAS_BASE_URL=http://localhost:8096
export BILLING_SAAS_SERVICE_TOKEN=changeme
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```

### Exemplo 100% copiavel (stack minimo)
```bash
export APP_FIRESTORE_ENABLED=false
export APP_INTERNAL_TOKEN=changeme
export QUOTA_INTERNAL_TOKEN=changeme
export QUOTA_BASE_URL=http://localhost:8081
export PORTAL_TOKEN=changeme
export PORTAL_BASE_URL=http://localhost:8094
export BILLING_SAAS_SERVICE_TOKEN=changeme
export BILLING_SAAS_WEBHOOK_SECRET=changeme
export BILLING_SAAS_BASE_URL=http://localhost:8096
export BILLING_BASE_URL=http://localhost:8095
export BILLING_SERVICE_TOKEN=changeme

mvn -B -ntp -f services/quota-service/pom.xml spring-boot:run
mvn -B -ntp -f services/developer-portal-service/pom.xml spring-boot:run
mvn -B -ntp -f services/billing-saas-service/pom.xml spring-boot:run
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```

### Health checks
```bash
curl -sf http://localhost:8081/actuator/health
curl -sf http://localhost:8094/actuator/health
curl -sf http://localhost:8096/actuator/health
curl -sf http://localhost:8080/v1/echo
```

## Testes (canonicos)

Nao existe `./mvnw` nem reactor root. Execute por modulo:

```bash
mvn -B -ntp -f services/billing-saas-service/pom.xml test -DskipITs=false
mvn -B -ntp -f services/api-gateway/pom.xml test -DskipITs=false
```

Outros modulos (quando necessario):

```bash
mvn -B -ntp -f services/quota-service/pom.xml test -DskipITs=false
mvn -B -ntp -f services/developer-portal-service/pom.xml test -DskipITs=false
mvn -B -ntp -f services/billing-service/pom.xml test -DskipITs=false
mvn -B -ntp -f services/webhook-service/pom.xml test -DskipITs=false
```

## Smoke end-to-end

Script: `scripts/smoke-billing-saas.sh`

O script:
- solicita API key FREE
- cria cliente
- cria cobranca
- gera PIX
- chama webhook PAID
- consulta status
- chama relatorio

Dependencias: `curl` e `python`. A API key sai mascarada no output.

```bash
GW_URL=http://localhost:8080 WEBHOOK_SECRET=changeme ./scripts/smoke-billing-saas.sh
```

## Como usar como cliente (SaaS + API)

### Fluxo de chave e creditos (API publica)
```bash
curl -s -X POST http://localhost:8080/v1/keys/request \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@empresa.com","org":"Empresa","useCase":"teste"}'
```

```bash
curl -s http://localhost:8080/v1/keys/status \
  -H "X-Api-Key: SUA_API_KEY"
```

```bash
curl -s -X POST http://localhost:8080/v1/keys/upgrade \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -d '{"packageName":"START"}'
```

```bash
curl -s http://localhost:8080/v1/keys/upgrade/{chargeId} \
  -H "X-Api-Key: SUA_API_KEY"
```

SaaS de cobranca (PIX + recorrencia):

### Criar cliente (SaaS cobranca)
```bash
curl -s -X POST http://localhost:8080/v1/clientes \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: cliente-001" \
  -d '{"name":"Cliente ACME","document":"12345678900","email":"financeiro@acme.com","phone":"+5511999999999"}'
```

### Criar cobranca
```bash
curl -s -X POST http://localhost:8080/v1/cobrancas \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: cobranca-001" \
  -d '{"customerId":"cus_...","amountCents":1990,"currency":"BRL","description":"Plano mensal","dueDate":"2026-01-31","recurrence":{"frequency":"MONTHLY","interval":1}}'
```

### Gerar PIX
```bash
curl -s -X POST http://localhost:8080/v1/pix/gerar \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: pix-001" \
  -d '{"chargeId":"chg_...","expiresInSeconds":3600}'
```

### Webhook de confirmacao (sem API key)
```bash
curl -s -X POST http://localhost:8080/v1/pix/webhook \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: SEU_SEGREDO" \
  -d '{"provider":"FAKE","providerChargeId":"fake_...","event":"PAID"}'
```

### Consultar status
```bash
curl -s http://localhost:8080/v1/cobrancas/chg_123/status \
  -H "X-Api-Key: SUA_API_KEY"
```

### Relatorios
```bash
curl -s "http://localhost:8080/v1/relatorios?from=2026-01-01&to=2026-01-31" \
  -H "X-Api-Key: SUA_API_KEY"
```

### Observacoes importantes
- Use `Idempotency-Key` em POSTs mutaveis para evitar duplicacao.
- Respostas do PIX retornam `pixCopyPaste`, `qrCodeBase64` e `whatsappLink`.

## Producao (Cloud Run / Docker / Firestore)

### Variaveis por ambiente (producao)
- `APP_FIRESTORE_ENABLED=true`
- `BILLING_SAAS_SERVICE_TOKEN` (forte, gateway -> billing-saas)
- `BILLING_SAAS_WEBHOOK_SECRET` (forte, webhook externo)
- `BILLING_SAAS_BASE_URL` (URL do billing-saas)
- `QUOTA_INTERNAL_TOKEN` (gateway -> quota)
- `PORTAL_TOKEN` (portal -> quota)
- `PORTAL_BASE_URL` (gateway -> portal)
- `QUOTA_BASE_URL` (portal -> quota)
- `BILLING_BASE_URL` (portal -> billing-service)
- `BILLING_SERVICE_TOKEN` (portal -> billing-service)
- `WEBHOOK_SECRET` (billing-service webhook PagBank)
- `PAGBANK_BASE_URL`, `PAGBANK_TOKEN`, `PAGBANK_WEBHOOK_TOKEN`, `PAGBANK_NOTIFICATION_URL` (billing-service)
- `START_PRICE_CENTS`, `START_CREDITS`, `PRO_PRICE_CENTS`, `PRO_CREDITS`, `SCALE_PRICE_CENTS`, `SCALE_CREDITS` (portal)
- `APP_PLANS_FREE_REQUESTS_PER_MINUTE`, `APP_PLANS_FREE_REQUESTS_PER_DAY` (quota-service)

### Build e deploy (exemplo)
Use o Dockerfile do billing-saas-service ou o padrao Cloud Run do repo.

```bash
# build local
cd services/billing-saas-service
mvn -B -ntp package

docker build -t billing-saas-service:local .
```

```bash
# deploy Cloud Run (exemplo)
# gcloud run deploy billing-saas-service --image gcr.io/PROJETO/IMAGE:TAG --region southamerica-east1
```

### Checklist pos-deploy
- validar `/actuator/health` de cada servico
- validar `/v1/echo` no gateway
- rodar smoke com URL de producao (com cuidado)

### Firestore
- Colecoes usadas pelo billing-saas-service:
  - `tenants/{tenantId}/customers`
  - `tenants/{tenantId}/charges`
  - `pix_provider_index` (lookup de webhook por providerChargeId)
  - `billing_saas_idempotency` (idempotencia)
- TTL: o store de idempotencia grava `expiresAt`. Se o ambiente nao tiver TTL automatizado, configurar TTL manualmente no console.

### Seguranca
- Tokens em Secret Manager (nao versionar).
- Rotacionar `X-Service-Token` e `X-Webhook-Secret` periodicamente.
- Webhook secret separado do service token.

## Site apipratudo.com (index.html)

**Source of truth do site**: este monorepo nao contem o site. O HTML oficial esta em:
- `/home/neo/Documentos/projetos/apipratudo-site/public/index.html`

Nao existe `index.html` dentro deste monorepo, entao o site deve ser editado no projeto `apipratudo-site`.

### Como editar
- Edite `public/index.html` no repo do site.
- O HTML nao possui comentarios com instrucoes; o deploy esta no `firebase.json`.
- O `firebase.json` possui um `predeploy` que gera `public/sitemap.xml` a partir dos endpoints listados no HTML.
- Nao existe `.firebaserc` no repo do site (usar `--project` no deploy).

### Como publicar (Firebase Hosting)
O arquivo `firebase.json` do site define o predeploy e o diretorio `public`.

```bash
cd /home/neo/Documentos/projetos/apipratudo-site
firebase deploy --only hosting --project api-pra-tudo
```

### Links internos do site
- /docs do gateway
- /openapi.yaml do gateway
- exemplos de uso via `/v1/*`

## Checklist para comecar a vender hoje

- Deploy: gateway + quota + portal + billing-saas.
- Configurar tokens e segredos.
- Verificar endpoints principais.
- Atualizar o site com CTA e links para docs.
- Definir limites FREE e pacotes de credito.
- Ativar canal de suporte (email/whatsapp).

## Go-to-market (pronto pra vender hoje)

### Pitch de 30 segundos
API Pra Tudo permite que empresas emitam cobrancas PIX e gerenciem recorrencias sem marketplace. A integracao e simples, com webhook seguro, relatorios por periodo e link de WhatsApp pronto para cobrar clientes. Em minutos, sua equipe integra e comeca a emitir cobrancas com rastreio e status em tempo real.

### Planos sugeridos (SaaS de cobranca)
Sugestao comercial para o produto de cobrancas (nao altera o modelo de creditos da API publica):

- Starter: R$ 199/mes + ate 1.000 cobrancas
- Pro: R$ 499/mes + ate 5.000 cobrancas
- Enterprise: volume sob consulta

### Copy pronta
Landing page (curta):
```
Emita cobrancas PIX com recorrencia em minutos.
API Pra Tudo entrega webhook seguro, relatorios e link de WhatsApp automatico.
Crie sua chave FREE e comece hoje.
```

WhatsApp (convite direto):
```
Oi! A gente ajuda sua empresa a emitir cobrancas PIX com recorrencia.
Temos webhook, relatorios e link de WhatsApp pronto. Quer testar?
```

E-mail (curto):
```
Assunto: PIX + recorrencia em minutos

Oi! A API Pra Tudo permite emitir cobrancas PIX com webhook, relatorios e recorrencia.
Integracao rapida e sem marketplace. Quer uma chave FREE para testar?
```
