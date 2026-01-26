# Arquitetura (visao rapida)

Contrato publico unico em `/v1/*` via api-gateway.

Diagrama (alto nivel):

Cliente
  -> api-gateway (/v1)
     -> quota-service (cota/creditos)
     -> developer-portal-service (chaves)
     -> billing-saas-service (clientes/cobrancas/pix)
     -> results-services (loterias)
     -> webhook-service (webhooks e entregas)

Webhooks externos
  -> api-gateway (/v1/pix/webhook)
     -> billing-saas-service

## Contrato /v1
- Somente o api-gateway expõe rotas publicas.
- Servicos internos nao sao expostos diretamente.

## Como adicionar um endpoint (10 passos)
1) Defina o contrato publico em `/v1`.
2) Crie/ajuste o endpoint interno no servico correto.
3) Crie o proxy no api-gateway (controller + client).
4) Garanta autenticacao via `X-Api-Key` quando aplicavel.
5) Se for webhook externo, documente a excecao de quota.
6) Padronize erros com `error` e `message`.
7) Atualize `services/api-gateway/src/main/resources/static/openapi.yaml`.
8) Adicione testes minimos no servico e no gateway.
9) Atualize o smoke (se o fluxo depender dele).
10) Valide via `/docs` e `/openapi.yaml`.
