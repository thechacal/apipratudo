# developer-portal-service

Servico publico para solicitar API key FREE, consultar status e iniciar upgrade via PIX.

## Rodar local
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## Endpoints
- POST /v1/keys/request
- GET /v1/keys/status
- POST /v1/keys/upgrade
- GET /v1/keys/upgrade/{chargeId}

## Docs
- Swagger UI: http://localhost:8094/docs
