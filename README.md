# API Pra Tudo

API publica que junta resultados de loterias e um SaaS de cobranca PIX (com recorrencia) em um unico contrato /v1.

## Para quem e
Times de produto, devs e empresas que querem integrar dados prontos e cobranca via API sem burocracia.
Foque no seu produto; a API cuida da infraestrutura e do contrato publico.

## O que da pra fazer

### Resultados de loterias
- Consumir resultados oficiais (megasena, quina, lotofacil e mais).

### Cobranca PIX/recorrente por API
- Criar clientes e cobrancas, gerar PIX, receber webhook e consultar status.

## Em 60 segundos

### 1) Pedir uma API key
```bash
BASE_URL="https://apipratudo.com"

curl -s "$BASE_URL/v1/keys/request" \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@empresa.com","org":"Empresa","useCase":"teste rapido"}'
```

### 2) Consumir uma loteria
```bash
curl -s "$BASE_URL/v1/megasena/resultado-oficial" \
  -H "X-Api-Key: SUA_API_KEY"
```

### 3) Fluxo de cobranca (resumo)
```bash
# criar cliente
curl -s "$BASE_URL/v1/clientes" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: cliente-1" \
  -d '{"name":"Acme","document":"12345678900","email":"financeiro@acme.com","phone":"551199999999"}'

# criar cobranca
curl -s "$BASE_URL/v1/cobrancas" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: cobranca-1" \
  -d '{"customerId":"cus_...","amountCents":1990,"currency":"BRL","description":"Plano","dueDate":"2026-01-30"}'

# gerar PIX
curl -s "$BASE_URL/v1/pix/gerar" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -d '{"chargeId":"chg_...","expiresInSeconds":3600}'

# webhook (em producao, o PagBank chama este endpoint)
curl -s "$BASE_URL/v1/pix/webhook" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: SEU_SEGREDO" \
  -d '{"provider":"FAKE","providerChargeId":"fake_...","event":"PAID","paidAt":"2026-01-30T12:00:00Z"}'

# status da cobranca
curl -s "$BASE_URL/v1/cobrancas/chg_.../status" \
  -H "X-Api-Key: SUA_API_KEY"
```

## Links
- `/docs`
- `/openapi.yaml`
- `docs/ARCHITECTURE.md`
- `docs/BILLING_SAAS_PAGBANK.md`
- `docs/LOCAL_DEV.md`
- `scripts/smoke-billing-saas.sh`

## Rodar local (minimo)

Sem `./mvnw` e sem reactor root. Suba cada servico em um terminal.

```bash
export APP_FIRESTORE_ENABLED=false
export PORTAL_TOKEN=changeme
export INTERNAL_TOKEN=changeme
export QUOTA_INTERNAL_TOKEN=changeme
export BILLING_SAAS_SERVICE_TOKEN=changeme
export BILLING_SAAS_WEBHOOK_SECRET=changeme

mvn -B -ntp -f services/quota-service/pom.xml spring-boot:run
mvn -B -ntp -f services/developer-portal-service/pom.xml spring-boot:run
mvn -B -ntp -f services/billing-saas-service/pom.xml spring-boot:run
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```

Smoke end-to-end:
```bash
GW_URL=http://localhost:8080 WEBHOOK_SECRET=changeme ./scripts/smoke-billing-saas.sh
```

## Vender como SaaS (resumo)
- O cliente conecta o proprio PagBank e recebe direto na conta dele.
- PIX + recorrencia + webhook + relatorios prontos.
- Link de WhatsApp gerado para cobrar em segundos.

CTA: veja `docs/BILLING_SAAS_PAGBANK.md`.
