# reconciliation-service-v0.0.2

## Added
- API de Conciliação Bancária (Open Finance-lite) exposta via gateway:
  - POST /v1/importar-extrato (CSV/OFX)
  - POST /v1/match
  - GET /v1/conciliado
  - GET /v1/pendencias
  - POST /v1/webhook/pagamento

## Gateway
- Integração com reconciliation-service via X-Service-Token + X-Tenant-Id.
- POST /v1/webhook/pagamento exige X-Api-Key e não consome quota.

## Fixed
- Firestore idempotency: document IDs agora usam hash SHA-256 da chave (evita erro com caracteres inválidos, ex.: "/").
