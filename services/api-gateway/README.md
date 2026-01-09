# api-gateway

Servico Spring Boot 3 (Java 21) para o apipratudo.

## Como rodar
```bash
mvn spring-boot:run
```

## Firestore
Cloud Run (prod):
- `APP_FIRESTORE_ENABLED=true`
- `APP_FIRESTORE_PROJECT_ID` (opcional; usa `GOOGLE_CLOUD_PROJECT` do ambiente por padrao)
- `APP_WEBHOOKS_COLLECTION` (opcional)
- `APP_DELIVERIES_COLLECTION` (opcional)
- `APP_WEBHOOKS_LIST_LIMIT` e `APP_DELIVERIES_LIST_LIMIT` (opcional)
- `APP_IDEMPOTENCY_STORE=memory` (opcional)

IAM minimo para o service account:
- `roles/datastore.user`

Deploy com env vars:
```bash
gcloud run deploy api-gateway \\
  --source services/api-gateway \\
  --region southamerica-east1 \\
  --allow-unauthenticated \\
  --set-env-vars=APP_FIRESTORE_ENABLED=true,APP_IDEMPOTENCY_STORE=memory
```

Rodar local sem emulator (ADC):
```bash
export APP_FIRESTORE_ENABLED=true
export GOOGLE_CLOUD_PROJECT=seu-projeto
mvn spring-boot:run
```

Rodar local com emulator (recomendado para testes):
```bash
./scripts/firestore-emulator.sh
```

Rodar local com emulator manual:
```bash
export APP_FIRESTORE_ENABLED=true
export GOOGLE_CLOUD_PROJECT=local-dev
export FIRESTORE_EMULATOR_HOST=localhost:8085
mvn spring-boot:run
```

## Deliveries reais (HTTP)
Configuracoes:
- `APP_DELIVERIES_MAX_ATTEMPTS` (default 5)
- `APP_DELIVERIES_INITIAL_BACKOFF_MS` (default 500)
- `APP_DELIVERIES_MAX_BACKOFF_MS` (default 30000)
- `APP_DELIVERIES_TIMEOUT_MS` (default 5000)
- `APP_DELIVERIES_RETRY_ON_5XX` (default true)
- `APP_DELIVERIES_RETRY_ON_429` (default true)

Teste local com WireMock:
```bash
docker run --rm -p 8089:8080 wiremock/wiremock:3.5.4
```

```bash
curl -X POST http://localhost:8089/__admin/mappings \
  -H 'Content-Type: application/json' \
  -d '{"request":{"method":"POST","url":"/webhook"},"response":{"status":200}}'
```

```bash
curl -X POST http://localhost:8080/v1/webhooks \
  -H 'Content-Type: application/json' \
  -d '{"targetUrl":"http://localhost:8089/webhook","eventType":"invoice.paid"}'

curl -X POST http://localhost:8080/v1/webhooks/{id}/test
```

## Documentacao
- Swagger UI: http://localhost:8080/docs
- OpenAPI: http://localhost:8080/openapi.yaml

## Endpoints principais
- GET /v1/echo
- POST /v1/webhooks
- GET /v1/webhooks
- GET /v1/webhooks/{id}
- PATCH /v1/webhooks/{id}
- DELETE /v1/webhooks/{id}
- POST /v1/webhooks/{id}/test
- GET /v1/deliveries
- GET /v1/deliveries/{id}
- POST /v1/deliveries/{id}/retry

## Exemplos curl
```bash
curl -X POST http://localhost:8080/v1/webhooks \\
  -H 'Content-Type: application/json' \\
  -H 'Idempotency-Key: webhook-123' \\
  -d '{\"targetUrl\":\"https://cliente.exemplo.com/webhooks/apipratudo\",\"eventType\":\"invoice.paid\"}'
```

```bash
curl http://localhost:8080/v1/webhooks?page=1&size=20
```

```bash
curl -X POST http://localhost:8080/v1/webhooks/{id}/test
```

```bash
curl http://localhost:8080/v1/deliveries?webhookId={id}
```
