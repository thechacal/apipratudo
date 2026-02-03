# API Pra Tudo

API publica que junta resultados de loterias, cobranca PIX por API e agendamento em um unico contrato /v1.

## Para quem e
Times de produto, devs e empresas que querem integrar dados prontos, cobrancas e agendamentos sem burocracia.
Foque no seu produto; a API cuida da infraestrutura e do contrato publico.

## O que da pra fazer

### Resultados de loterias
- Consumir resultados oficiais (megasena, quina, lotofacil e mais).

### Cobranca PIX/recorrente por API
- Criar clientes e cobrancas, gerar PIX, receber webhook e consultar status.

### Agendamento (MVP)
- Cadastrar servicos, listar slots, reservar, confirmar e cancelar.
- Multas por no-show e eventos via webhooks.
- Creditos por agenda para liberar confirmacoes.

### Helpdesk/WhatsApp (MVP)
- Criar tickets, registrar mensagens e gerenciar status.
- Webhook do WhatsApp publico (sem X-Api-Key) com validacao de assinatura.
- Templates simples para respostas padronizadas.

### Conciliacao Bancaria (Open Finance-lite)
- Importar extrato CSV/OFX, aplicar regras de match e listar conciliados/pendencias.
- Webhook de pagamento com X-Api-Key e sem consumo de quota.

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

### 4) Fluxo de conciliacao (resumo)
```bash
# importar extrato (CSV/OFX)
curl -s "$BASE_URL/v1/importar-extrato" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: imp-001" \
  -F "file=@extrato.csv;type=text/csv" \
  -F "accountId=conta-main"

# executar match
curl -s "$BASE_URL/v1/match" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: match-001" \
  -d '{"importId":"imp_...","ruleset":{"matchBy":["amount","date","reference"],"dateToleranceDays":2,"amountToleranceCents":0,"dedupe":true}}'

# consultar conciliados e pendencias
curl -s "$BASE_URL/v1/conciliado?importId=imp_..." -H "X-Api-Key: SUA_API_KEY"
curl -s "$BASE_URL/v1/pendencias?importId=imp_..." -H "X-Api-Key: SUA_API_KEY"
```

## Agendamento (MVP)

Problemas comuns: no-show, agenda desorganizada e falta de notificacoes. O MVP resolve o basico com endpoints publicos no gateway.

Endpoints publicos (via /v1, com X-Api-Key):
- `GET /v1/servicos`
- `POST /v1/servicos`
- `GET /v1/agenda`
- `GET /v1/slots-disponiveis`
- `POST /v1/reservar`
- `POST /v1/confirmar`
- `POST /v1/cancelar`
- `POST /v1/notificar`
- `GET /v1/agendas`
- `POST /v1/agendas`
- `GET /v1/agendas/{id}`
- `PATCH /v1/agendas/{id}`
- `POST /v1/agendas/{id}/creditos/upgrade`
- `GET /v1/agendas/{id}/creditos/status/{chargeId}`
- `POST /v1/atendido`
- `GET /v1/multas`
- `POST /v1/multas/{id}/waive`

Headers obrigatorios:
- `X-Api-Key` em todas as rotas
- `Idempotency-Key` nos POST/PATCH mutaveis

Exemplos rapidos:
```bash
# criar servico
curl -s "$BASE_URL/v1/servicos" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: srv-001" \
  -H "Content-Type: application/json" \
  -d '{"name":"Corte Masculino","durationMin":40,"prepMin":5,"bufferMin":10,"noShowFeeCents":2000,"active":true}'

# slots disponiveis
curl -s "$BASE_URL/v1/slots-disponiveis?serviceId=srv_...&date=2026-01-26&agendaId=main" \
  -H "X-Api-Key: SUA_API_KEY"

# reservar e confirmar
curl -s "$BASE_URL/v1/reservar" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: res-001" \
  -H "Content-Type: application/json" \
  -d '{"serviceId":"srv_...","agendaId":"main","startAt":"2026-01-26T12:00:00Z","customer":{"name":"Ana","phone":"+5588999990000","email":"ana@email.com"}}'

curl -s "$BASE_URL/v1/confirmar" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: conf-001" \
  -H "Content-Type: application/json" \
  -d '{"appointmentId":"apt_..."}'
```

Seguranca:
- O `scheduling-service` e interno e exige `X-Service-Token` (alem do tenant). Em producao, use ingress interno.

Monetizacao:
- Consumo normal de quota por request.
- Confirmacoes consomem creditos de agenda. Ao zerar, retorna `AGENDA_CREDITS_EXCEEDED`.

Swagger (producao):
- `/swagger-ui/index.html`

## Helpdesk/WhatsApp (MVP)

Endpoints publicos (via /v1, com X-Api-Key):
- `GET /v1/tickets`
- `POST /v1/tickets`
- `GET /v1/tickets/{ticketId}`
- `GET /v1/tickets/{ticketId}/mensagens`
- `POST /v1/tickets/{ticketId}/mensagens`
- `POST /v1/tickets/{ticketId}/atribuir`
- `POST /v1/tickets/{ticketId}/status`
- `GET /v1/templates`
- `POST /v1/templates`

Webhook WhatsApp (publico, sem X-Api-Key):
- `GET /v1/webhook/whatsapp` (verify)
- `POST /v1/webhook/whatsapp` (eventos)

Headers obrigatorios:
- `X-Api-Key` em rotas de tickets/templates
- `Idempotency-Key` nos POST mutaveis

Exemplo rapido:
```bash
curl -s "$BASE_URL/v1/tickets" \
  -H "X-Api-Key: SUA_API_KEY" \
  -H "Idempotency-Key: tkt-001" \
  -H "Content-Type: application/json" \
  -d '{"customerWaId":"5511999999999"}'
```

## Conciliacao Bancaria (Open Finance-lite)

Endpoints publicos (via /v1, com X-Api-Key):
- `POST /v1/importar-extrato`
- `POST /v1/match`
- `GET /v1/conciliado`
- `GET /v1/pendencias`
- `POST /v1/webhook/pagamento`

Regra de quota:
- `POST /v1/webhook/pagamento` exige `X-Api-Key` mas nao consome quota.
- Demais endpoints de conciliacao seguem quota normal.

## Links
- `/swagger-ui/index.html`
- `/openapi.yaml`
- `docs/ARCHITECTURE.md`
- `docs/BILLING_SAAS_PAGBANK.md`
- `docs/LOCAL_DEV.md`
- `scripts/smoke-billing-saas.sh`
- `scripts/smoke-scheduling-local.sh`

## Rodar local (minimo)

Sem `./mvnw` e sem reactor root. Suba cada servico em um terminal.

```bash
export APP_FIRESTORE_ENABLED=false
export PORTAL_TOKEN=changeme
export INTERNAL_TOKEN=changeme
export QUOTA_INTERNAL_TOKEN=changeme
export BILLING_SAAS_SERVICE_TOKEN=changeme
export BILLING_SAAS_WEBHOOK_SECRET=changeme
export SCHEDULING_SERVICE_TOKEN=changeme
export SCHEDULING_AGENDA_DEFAULT_CREDITS=1

mvn -B -ntp -f services/quota-service/pom.xml spring-boot:run
mvn -B -ntp -f services/developer-portal-service/pom.xml spring-boot:run
mvn -B -ntp -f services/billing-saas-service/pom.xml spring-boot:run
mvn -B -ntp -f services/scheduling-service/pom.xml spring-boot:run
mvn -B -ntp -f services/api-gateway/pom.xml spring-boot:run
```

Smoke end-to-end:
```bash
GW_URL=http://localhost:8080 ./scripts/smoke-billing-saas.sh
GW_URL=http://localhost:8080 ./scripts/smoke-scheduling-local.sh
```

## Vender como SaaS (resumo)
- O cliente conecta o proprio PagBank e recebe direto na conta dele.
- PIX + recorrencia + webhook + relatorios prontos.
- Link de WhatsApp gerado para cobrar em segundos.

CTA: veja `docs/BILLING_SAAS_PAGBANK.md`.
