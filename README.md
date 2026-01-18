# apipratudo

Plataforma de APIs em Java/Spring Boot com entrada unica via api-gateway e foco em consumo por terceiros com quotas.

## Servicos
- Implementados: api-gateway, quota-service, webhook-service, developer-portal-service, billing-service, federal-results-service, lotofacil-results-service, megasena-results-service, quina-results-service, lotomania-results-service, timemania-results-service, duplasena-results-service, loteca-results-service, diadesorte-results-service, supersete-results-service, maismilionaria-results-service
- Planejados: file-service, cep-service

## Implementado ate agora
- api-gateway como entrada /v1, exigindo X-Api-Key nas rotas publicas (exceto /v1/echo e docs).
- webhook-service com cadastro e leitura de webhooks (POST/GET), idempotencia por Idempotency-Key e fallback InMemory quando Firestore nao esta disponivel.
- api-gateway encaminha POST/GET /v1/webhooks para o webhook-service preservando X-Api-Key e Idempotency-Key.
- quota-service com api-keys e quota (consume/refund/status) protegido por X-Admin-Token e X-Internal-Token.
- Integracao de quota no gateway: consume antes do request e refund best-effort em respostas 5xx.
- Idempotencia de quota: requestId usa Idempotency-Key apenas em metodos mutaveis; GET/HEAD/OPTIONS nao usam.
- Fallback automatico para InMemory quando Firestore nao esta disponivel.
- Calculo consistente de janelas minute/day e status alinhado ao consume.
- Servicos de resultados da CAIXA expostos pelo gateway (/v1/*/resultado-oficial).
- developer-portal-service publico para solicitar API key FREE e iniciar recarga via PIX.
- billing-service interno com cobranca PIX PagBank e adicao de creditos.

## Lottery Results Services (via gateway)
Endpoints (usar X-Api-Key):
- GET /v1/federal/resultado-oficial
- GET /v1/lotofacil/resultado-oficial
- GET /v1/megasena/resultado-oficial
- GET /v1/quina/resultado-oficial
- GET /v1/lotomania/resultado-oficial
- GET /v1/timemania/resultado-oficial
- GET /v1/duplasena/resultado-oficial
- GET /v1/loteca/resultado-oficial
- GET /v1/diadesorte/resultado-oficial
- GET /v1/supersete/resultado-oficial
- GET /v1/maismilionaria/resultado-oficial

Exemplo:
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/v1/megasena/resultado-oficial | jq
```

## Smoke test Cloud Run
```bash
make smoke-cloud
```

```bash
make smoke-cloud-summary
```

```bash
make smoke-cloud-json
```

```bash
ADMIN_TOKEN=... make smoke-cloud
API_KEY=... make smoke-cloud
SHOW_JSON=1 ./scripts/smoke-cloud.sh
```

## Arquitetura
- docs/architecture.md
- docs/diagrams/README.md

## Como rodar localmente
Use o profile `local` para rodar sem Firestore.

quota-service:
```bash
cd services/quota-service
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

api-gateway:
```bash
cd services/api-gateway
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

webhook-service:
```bash
cd services/webhook-service
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Tokens locais (quota-service)
- X-Admin-Token: dev-admin
- X-Internal-Token: dev-internal

## Comece gratis em menos de 1 minuto
1. Abra o Swagger: http://localhost:8080/docs (em prod: https://<gateway-url>/docs)
2. Solicite sua API key gratuita em POST /v1/keys/request
3. Use a key para testar os endpoints
4. Ao atingir o limite FREE, recarregue via PIX

## Como obter API key (FREE)
Fluxo publico via gateway (sem X-Api-Key). A chave so aparece uma vez na resposta.

```bash
curl -s -X POST http://localhost:8080/v1/keys/request \
  -H 'Content-Type: application/json' \
  -d '{"email":"teste@exemplo.com","org":"Acme","useCase":"integracao resultados"}' | jq
```

## Limites e upgrade (creditos)
Plano FREE:
- 30 req/min
- 200 req/dia
- gratuito

Pacotes de creditos (pagamento por consumo, sem assinatura recorrente):
- START: R$ 19,90 -> 50.000 creditos
- PRO: R$ 49,90 -> 200.000 creditos
- SCALE: R$ 99,90 -> 500.000 creditos

Regras de consumo:
- Pagamento compra creditos (nao ha plano por periodo)
- Creditos nao tem validade temporal
- Enquanto credits.remaining > 0: usa limites PREMIUM (rpm alto, sem bloqueio diario)
- Quando credits.remaining = 0: volta automaticamente para FREE

Ao estourar a quota FREE ou acabar os creditos, o gateway devolve HTTP 402 com `QUOTA_EXCEEDED`
e instrucao de recarga.

```bash
curl -s -X POST http://localhost:8080/v1/keys/upgrade \
  -H "X-Api-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"packageName":"START"}' | jq
```

```bash
curl -s http://localhost:8080/v1/keys/upgrade/{chargeId} \
  -H "X-Api-Key: $API_KEY" | jq
```

Depois do pagamento PIX:
- webhook PagBank confirma a cobranca no billing-service
- billing-service adiciona creditos no quota-service
- /v1/keys/status retorna `credits.remaining` e `plan=PREMIUM` (derivado) enquanto houver creditos

## Curls minimos (admin)
```bash
# 1) criar API key (copie o campo apiKey do response)
curl -X POST http://localhost:8081/v1/api-keys \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: dev-admin' \
  -d '{"name":"Cliente A","owner":"cliente-a","limits":{"requestsPerMinute":60,"requestsPerDay":1000}}'
```

```bash
# 2) chamar api-gateway com a API key criada
curl http://localhost:8080/v1/webhooks \
  -H 'X-Api-Key: <API_KEY>'
```

```bash
# 3) consultar status de quota com X-Admin-Token
curl "http://localhost:8081/v1/quota/status?apiKey=<API_KEY>" \
  -H 'X-Admin-Token: dev-admin'
```
