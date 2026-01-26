#!/usr/bin/env bash
set -euo pipefail

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Faltando dependencia: $1" >&2
    exit 1
  }
}

require_env() {
  local name="$1"
  if [ -z "${!name-}" ]; then
    echo "Env ausente: $name" >&2
    exit 1
  fi
}

mask_key() {
  local value="$1"
  local len=${#value}
  if [ "$len" -le 8 ]; then
    echo "****"
  else
    echo "${value:0:4}****${value: -4}"
  fi
}

json_get() {
  local file="$1"
  local key="$2"
  python - "$file" "$key" <<'PY'
import json, sys
file_path = sys.argv[1]
key = sys.argv[2]
try:
  with open(file_path, "r", encoding="utf-8") as fh:
    data = json.load(fh)
except Exception:
  print("")
  sys.exit(0)

cur = data
for part in key.split('.'):
  if isinstance(cur, dict) and part in cur:
    cur = cur[part]
  else:
    print("")
    sys.exit(0)

if cur is None:
  print("")
else:
  if isinstance(cur, (dict, list)):
    print(json.dumps(cur))
  else:
    print(cur)
PY
}

http_request_to_file() {
  local method="$1"
  local url="$2"
  local outfile="$3"
  shift 3
  curl -s -o "$outfile" -w "%{http_code}" -X "$method" "$url" "$@"
}

require_cmd curl
require_cmd python

require_env PAGBANK_TOKEN
require_env PAGBANK_ENVIRONMENT

GW_URL="${GW_URL:-}"
if [ -z "$GW_URL" ]; then
  echo "Env ausente: GW_URL" >&2
  exit 1
fi

BASE_URL="$GW_URL"

# 1) solicitar API key
TS_SUFFIX=$(date +%s)
REQUEST_EMAIL="${REQUEST_EMAIL:-tenant-${TS_SUFFIX}@acme.com}"
REQUEST_ORG="${REQUEST_ORG:-ACME-${TS_SUFFIX}}"
code=$(http_request_to_file POST "$BASE_URL/v1/keys/request" /tmp/tenant_key_resp.json \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${REQUEST_EMAIL}\",\"org\":\"${REQUEST_ORG}\",\"useCase\":\"teste pagbank tenant\"}")
if [ "$code" != "201" ] && [ "$code" != "200" ]; then
  echo "Falha ao solicitar API key (status $code)." >&2
  cat /tmp/tenant_key_resp.json >&2
  exit 1
fi

API_KEY=$(json_get /tmp/tenant_key_resp.json apiKey)
if [ -z "$API_KEY" ]; then
  echo "Nao foi possivel extrair apiKey." >&2
  cat /tmp/tenant_key_resp.json >&2
  exit 1
fi

MASKED_KEY=$(mask_key "$API_KEY")

# 2) conectar PagBank
CONNECT_PAYLOAD=$(python - <<'PY'
import json, os
payload = {
  "token": os.environ["PAGBANK_TOKEN"],
  "environment": os.environ.get("PAGBANK_ENVIRONMENT", "SANDBOX")
}
webhook = os.environ.get("PAGBANK_WEBHOOK_TOKEN")
if webhook:
  payload["webhookToken"] = webhook
print(json.dumps(payload))
PY
)

code=$(http_request_to_file POST "$BASE_URL/v1/provedores/pagbank/conectar" /tmp/pagbank_connect_resp.json \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: pagbank-connect-${TS_SUFFIX}" \
  -d "$CONNECT_PAYLOAD")
if [ "$code" != "200" ]; then
  echo "Falha ao conectar PagBank (status $code)." >&2
  cat /tmp/pagbank_connect_resp.json >&2
  exit 1
fi

CONNECTED=$(json_get /tmp/pagbank_connect_resp.json connected)
FINGERPRINT=$(json_get /tmp/pagbank_connect_resp.json fingerprint)

# 3) criar cliente (CPF valido)
VALID_CPF="${CUSTOMER_DOCUMENT:-128.297.866-70}"
CUSTOMER_PAYLOAD=$(python - <<PY
import json
print(json.dumps({
  "name": "Cliente Teste",
  "document": "$VALID_CPF",
  "email": "financeiro@acme.com",
  "phone": "+5588999999999"
}))
PY
)
code=$(http_request_to_file POST "$BASE_URL/v1/clientes" /tmp/customer_resp.json \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: cliente-${TS_SUFFIX}" \
  -d "$CUSTOMER_PAYLOAD")
if [ "$code" != "201" ]; then
  echo "Falha ao criar cliente (status $code)." >&2
  cat /tmp/customer_resp.json >&2
  exit 1
fi
CUSTOMER_ID=$(json_get /tmp/customer_resp.json id)

# 4) criar cobranca
DUE_IN_DAYS="${DUE_IN_DAYS:-7}"
AMOUNT_CENTS="${AMOUNT_CENTS:-100}"
CHARGE_DESCRIPTION="${CHARGE_DESCRIPTION:-Cobranca teste PagBank}" 
DUE_DATE=$(python - <<'PY'
import datetime
import os
days = int(os.environ.get("DUE_IN_DAYS", "7"))
print((datetime.date.today() + datetime.timedelta(days=days)).isoformat())
PY
)
CHARGE_PAYLOAD=$(python - <<PY
import json
print(json.dumps({
  "customerId": "$CUSTOMER_ID",
  "amountCents": int("$AMOUNT_CENTS"),
  "currency": "BRL",
  "description": "$CHARGE_DESCRIPTION",
  "dueDate": "$DUE_DATE"
}))
PY
)
code=$(http_request_to_file POST "$BASE_URL/v1/cobrancas" /tmp/charge_resp.json \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: cobranca-${TS_SUFFIX}" \
  -d "$CHARGE_PAYLOAD")
if [ "$code" != "201" ]; then
  echo "Falha ao criar cobranca (status $code)." >&2
  cat /tmp/charge_resp.json >&2
  exit 1
fi
CHARGE_ID=$(json_get /tmp/charge_resp.json id)

# 5) gerar PIX
PIX_PAYLOAD=$(python - <<PY
import json
print(json.dumps({
  "chargeId": "$CHARGE_ID",
  "expiresInSeconds": 3600
}))
PY
)
code=$(http_request_to_file POST "$BASE_URL/v1/pix/gerar" /tmp/pix_resp.json \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: pix-${TS_SUFFIX}" \
  -d "$PIX_PAYLOAD")
if [ "$code" != "200" ]; then
  echo "Falha ao gerar PIX (status $code)." >&2
  cat /tmp/pix_resp.json >&2
  exit 1
fi

PROVIDER_CHARGE_ID=$(json_get /tmp/pix_resp.json pix.providerChargeId)
PIX_COPY_PASTE=$(json_get /tmp/pix_resp.json pix.pixCopyPaste)
QR_B64=$(json_get /tmp/pix_resp.json pix.qrCodeBase64)
QR_LEN=${#QR_B64}

cat <<OUT
OK
- apiKey: $MASKED_KEY
- pagbank.connected: $CONNECTED
- pagbank.fingerprint: $FINGERPRINT
- customerId: $CUSTOMER_ID
- chargeId: $CHARGE_ID
- providerChargeId: $PROVIDER_CHARGE_ID
- pixCopyPaste: $PIX_COPY_PASTE
- qrCodeBase64Length: $QR_LEN
OUT

# 6) polling status
POLL_SECONDS="${POLL_SECONDS:-5}"
POLL_MAX_SECONDS="${POLL_MAX_SECONDS:-600}"
max_attempts=$((POLL_MAX_SECONDS / POLL_SECONDS))
if [ "$max_attempts" -lt 1 ]; then
  max_attempts=1
fi
attempt=1
while [ $attempt -le $max_attempts ]; do
  code=$(http_request_to_file GET "$BASE_URL/v1/cobrancas/$CHARGE_ID/status" /tmp/status_resp.json \
    -H "X-Api-Key: $API_KEY")
  if [ "$code" != "200" ]; then
    echo "Falha ao consultar status (status $code)." >&2
    cat /tmp/status_resp.json >&2
    exit 1
  fi
  STATUS=$(json_get /tmp/status_resp.json status)
  echo "status: $STATUS (tentativa $attempt/$max_attempts)"
  if [ "$STATUS" = "PAID" ]; then
    echo "OK: pagamento confirmado via webhook."
    exit 0
  fi
  sleep "$POLL_SECONDS"
  attempt=$((attempt + 1))
done

echo "NAO CONFIRMOU: webhook nao chegou ou notification_url nao foi aceito." >&2
exit 2
