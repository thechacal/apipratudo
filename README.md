# apipratudo

Plataforma de APIs em Java/Spring Boot com entrada unica via api-gateway e foco em consumo por terceiros com quotas.

## Servicos
- Implementados: api-gateway, quota-service, webhook-service
- Planejados: file-service, cep-service, developer-portal

## Implementado ate agora
- api-gateway como entrada /v1, exigindo X-Api-Key nas rotas publicas (exceto /v1/echo e docs).
- webhook-service com cadastro e leitura de webhooks (POST/GET), idempotencia por Idempotency-Key e fallback InMemory quando Firestore nao esta disponivel.
- api-gateway encaminha POST/GET /v1/webhooks para o webhook-service preservando X-Api-Key e Idempotency-Key.
- quota-service com api-keys e quota (consume/refund/status) protegido por X-Admin-Token e X-Internal-Token.
- Integracao de quota no gateway: consume antes do request e refund best-effort em respostas 5xx.
- Idempotencia de quota: requestId usa Idempotency-Key apenas em metodos mutaveis; GET/HEAD/OPTIONS nao usam.
- Fallback automatico para InMemory quando Firestore nao esta disponivel.
- Calculo consistente de janelas minute/day e status alinhado ao consume.

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

## Curls minimos
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
