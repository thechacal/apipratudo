# Billing SaaS + PagBank (Modelo B)

Modelo B: cada cliente conecta o proprio PagBank e o PIX pago cai direto na conta do cliente.

## Endpoints
- `POST /v1/provedores/pagbank/conectar`
- `GET /v1/provedores/pagbank/status`
- `DELETE /v1/provedores/pagbank/desconectar`

## Seguranca (resumo)
- O token do PagBank nao e armazenado em texto claro.
- Criptografia AES-GCM com master key em base64.
- Configure `BILLING_SAAS_MASTER_KEY_BASE64` no ambiente do billing-saas-service.

## Webhook
- `POST /v1/pix/webhook`
- Nao exige `X-Api-Key`.
- Exige `X-Webhook-Secret` e validacao do provedor.

## Smoke (modo FAKE x PagBank)
- FAKE: fluxo completo local (inclui webhook simulado).
- PAGBANK: valida criacao/QR/copy-paste; confirmacao depende do webhook real do PagBank.
