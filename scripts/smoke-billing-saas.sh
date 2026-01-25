#!/usr/bin/env bash
set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
  echo "Missing dependency: curl"
  exit 1
fi
if ! command -v python >/dev/null 2>&1; then
  echo "Missing dependency: python"
  exit 1
fi

GW_URL=${GW_URL:-}
if [[ -z "${GW_URL}" ]]; then
  echo "Missing GW_URL"
  exit 1
fi

WEBHOOK_SECRET=${BILLING_SAAS_WEBHOOK_SECRET:-${WEBHOOK_SECRET:-}}
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  echo "Missing WEBHOOK_SECRET (or BILLING_SAAS_WEBHOOK_SECRET)"
  exit 1
fi

request() {
  local method=$1
  local url=$2
  local data=$3
  shift 3
  local resp
  if [[ -n "${data}" ]]; then
    resp=$(curl -sS -w "\n%{http_code}" -X "${method}" "${url}" "$@" -d "${data}")
  else
    resp=$(curl -sS -w "\n%{http_code}" -X "${method}" "${url}" "$@")
  fi
  local body=${resp%$'\n'*}
  local code=${resp##*$'\n'}
  printf '%s\n' "${code}"
  printf '%s\n' "${body}"
}

json_get() {
  python -c 'import sys, json
path = sys.argv[1].split(".")
data = json.load(sys.stdin)
for key in path:
    if not key:
        continue
    data = data[key]
print(data)
' "$1"
}

idempotency() {
  date +%s%N
}

echo "== Requesting FREE api key"
resp=$(request POST "${GW_URL}/v1/keys/request" '{"email":"dev@exemplo.com","org":"ACME","useCase":"smoke"}' \
  -H "Content-Type: application/json")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "201" ]]; then
  echo "Failed to request key: ${code} ${body}"
  exit 1
fi
api_key=$(printf '%s' "${body}" | json_get 'apiKey')
masked_key="${api_key}"
if [[ ${#api_key} -gt 8 ]]; then
  masked_key="${api_key:0:4}...${api_key: -4}"
fi
echo "apiKey=${masked_key}"

if [[ -n "${PAGBANK_TOKEN:-}" ]]; then
  echo "== Connecting PagBank"
  env_name=${PAGBANK_ENV:-SANDBOX}
  webhook_token_json=""
  if [[ -n "${PAGBANK_WEBHOOK_TOKEN:-}" ]]; then
    webhook_token_json=",\"webhookToken\":\"${PAGBANK_WEBHOOK_TOKEN}\""
  fi
  payload="{\"token\":\"${PAGBANK_TOKEN}\",\"environment\":\"${env_name}\"${webhook_token_json}}"
  resp=$(request POST "${GW_URL}/v1/provedores/pagbank/conectar" "${payload}" \
    -H "Content-Type: application/json" \
    -H "X-Api-Key: ${api_key}")
  code=$(printf '%s' "${resp}" | head -n1)
  body=$(printf '%s' "${resp}" | tail -n +2)
  if [[ "${code}" != "200" ]]; then
    echo "Failed to connect PagBank: ${code} ${body}"
    exit 1
  fi
  connected=$(printf '%s' "${body}" | json_get 'connected')
  echo "pagbankConnected=${connected}"
fi

echo "== Creating customer"
resp=$(request POST "${GW_URL}/v1/clientes" '{"name":"Cliente Smoke","document":"12345678900","email":"financeiro@exemplo.com","phone":"+5511999999999"}' \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: ${api_key}" \
  -H "Idempotency-Key: cust-$(idempotency)")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "201" ]]; then
  echo "Failed to create customer: ${code} ${body}"
  exit 1
fi
customer_id=$(printf '%s' "${body}" | json_get 'id')
echo "customerId=${customer_id}"

echo "== Creating charge"
resp=$(request POST "${GW_URL}/v1/cobrancas" "{\"customerId\":\"${customer_id}\",\"amountCents\":1990,\"currency\":\"BRL\",\"description\":\"Smoke Charge\",\"dueDate\":\"$(date -u +%Y-%m-%d)\",\"recurrence\":{\"frequency\":\"MONTHLY\",\"interval\":1}}" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: ${api_key}" \
  -H "Idempotency-Key: chg-$(idempotency)")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "201" ]]; then
  echo "Failed to create charge: ${code} ${body}"
  exit 1
fi
charge_id=$(printf '%s' "${body}" | json_get 'id')
echo "chargeId=${charge_id}"

echo "== Generating PIX"
resp=$(request POST "${GW_URL}/v1/pix/gerar" "{\"chargeId\":\"${charge_id}\",\"expiresInSeconds\":3600}" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: ${api_key}" \
  -H "Idempotency-Key: pix-$(idempotency)")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "200" ]]; then
  echo "Failed to generate pix: ${code} ${body}"
  exit 1
fi
provider_charge_id=$(printf '%s' "${body}" | json_get 'pix.providerChargeId')
echo "providerChargeId=${provider_charge_id}"

echo "== Calling webhook (PAID)"
resp=$(request POST "${GW_URL}/v1/pix/webhook" "{\"provider\":\"FAKE\",\"providerChargeId\":\"${provider_charge_id}\",\"event\":\"PAID\"}" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: ${WEBHOOK_SECRET}")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "200" ]]; then
  echo "Failed to call webhook: ${code} ${body}"
  exit 1
fi

echo "== Checking charge status"
resp=$(request GET "${GW_URL}/v1/cobrancas/${charge_id}/status" "" \
  -H "Accept: application/json" \
  -H "X-Api-Key: ${api_key}")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "200" ]]; then
  echo "Failed to get charge status: ${code} ${body}"
  exit 1
fi
status=$(printf '%s' "${body}" | json_get 'status')
echo "status=${status}"

if [[ "${status}" != "PAID" ]]; then
  echo "Expected status PAID, got ${status}"
  exit 1
fi

echo "== Fetching report"
resp=$(request GET "${GW_URL}/v1/relatorios?from=$(date -u +%Y-%m-%d)&to=$(date -u +%Y-%m-%d)" "" \
  -H "Accept: application/json" \
  -H "X-Api-Key: ${api_key}")
code=$(printf '%s' "${resp}" | head -n1)
body=$(printf '%s' "${resp}" | tail -n +2)
if [[ "${code}" != "200" ]]; then
  echo "Failed to get report: ${code} ${body}"
  exit 1
fi

echo "Smoke OK"
