# Webhooks (milestone 2)

Este documento mostra o fluxo end-to-end de webhooks no ambiente local.

## 1) Criar webhook via api-gateway
```bash
curl -i -X POST "http://localhost:8080/v1/webhooks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: wh-test" \
  -H "Idempotency-Key: idem-1" \
  -d '{
    "targetUrl":"https://example.com/hook",
    "events":["delivery.created"],
    "secret":"s3cr3t"
  }'
```

## 2) Listar webhooks
```bash
curl -s -H "X-Api-Key: wh-test" "http://localhost:8080/v1/webhooks"
```

## 3) Criar delivery (endpoint existente)
```bash
curl -i -X POST "http://localhost:8080/v1/webhooks/{id}/test" \
  -H "X-Api-Key: wh-test"
```

Ao criar um delivery, o api-gateway publica o evento interno e o webhook-service dispara o POST no targetUrl.
