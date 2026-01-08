# api-gateway

Servico Spring Boot 3 (Java 21) para o apipratudo.

## Como rodar
```bash
mvn spring-boot:run
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
