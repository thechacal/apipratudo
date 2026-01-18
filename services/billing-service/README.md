# billing-service

Servico Spring Boot 3 (Java 21) para cobranca PIX (PagBank).

## Como rodar
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## PagBank
Configuracoes:
- `PAGBANK_BASE_URL` (default `https://sandbox.api.pagseguro.com`)
- `PAGBANK_TOKEN` (obrigatorio)
- `PAGBANK_WEBHOOK_TOKEN` (opcional; default usa o token acima)
- `PAGBANK_NOTIFICATION_URL` (opcional)
- `PAGBANK_QR_TTL_SECONDS` (default 300)
- `PAGBANK_TZ` (default `America/Sao_Paulo`)
- `PAGBANK_WEBHOOK_STRICT` (default true)

## Quota service
Configuracoes:
- `QUOTA_BASE_URL` (default `http://localhost:8081`)
- `QUOTA_INTERNAL_TOKEN` (token interno)

## Seguranca
- `BILLING_SERVICE_TOKEN`: header `X-Service-Token` (obrigatorio)
- `WEBHOOK_SECRET`: header `X-Webhook-Secret` (obrigatorio)

## Cloud Run (prod)
IAM minimo para o service account:
- `roles/datastore.user`

Deploy:
```bash
gcloud run deploy billing-service \
  --source services/billing-service \
  --region southamerica-east1 \
  --allow-unauthenticated \
  --set-env-vars=APP_FIRESTORE_ENABLED=true
```

## Documentacao
- Swagger UI: http://localhost:8095/docs
- OpenAPI: http://localhost:8095/v3/api-docs

## Endpoints
- POST /v1/billing/pix/charges
- GET /v1/billing/pix/charges/{chargeId}
- POST /v1/billing/pix/webhook
