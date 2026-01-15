# Resultados de loterias (CAIXA)

Os resultados oficiais sao expostos via api-gateway e exigem `X-Api-Key`.

## Endpoints (gateway)
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

## Exemplo local
```bash
curl -s -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/v1/federal/resultado-oficial | jq
```

## Exemplo cloud
```bash
curl -s -H "X-Api-Key: $API_KEY_CLOUD" \
  "$GW_URL/v1/federal/resultado-oficial" | jq
```
