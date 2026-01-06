# API Guidelines

## Versionamento
- Todas as rotas publicas iniciam com `/v1`.
- Mudancas breaking exigem nova versao (ex.: `/v2`).

## Formato de erros (problem+json)
- `Content-Type: application/problem+json`.
- Campos obrigatorios: `type`, `title`, `status`, `detail`, `instance`.

Exemplo:
```json
{
  "type": "https://apipratudo.local/errors/validation",
  "title": "Validation error",
  "status": 400,
  "detail": "email is required",
  "instance": "/v1/webhooks"
}
```

## Paginacao
- Parametros: `page` (>= 1) e `size` (1..100).
- Resposta: `items`, `page`, `size`, `total`.

## Idempotencia
- Para POST com efeitos colaterais, aceitar `Idempotency-Key`.
- Mesma chave + mesmo payload retornam a mesma resposta.
