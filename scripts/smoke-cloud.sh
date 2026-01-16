#!/usr/bin/env bash
set -euo pipefail

REGION="${REGION:-southamerica-east1}"
GW_URL="${GW_URL:-}"
QUOTA_URL="${QUOTA_URL:-}"
API_KEY="${API_KEY:-}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Comando obrigatorio nao encontrado: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq
require_cmd gcloud

if [ -z "$GW_URL" ]; then
  GW_URL=$(gcloud run services describe api-gateway --region "$REGION" --format='value(status.url)')
fi

if [ -z "$QUOTA_URL" ]; then
  QUOTA_URL=$(gcloud run services describe quota-service --region "$REGION" --format='value(status.url)')
fi

if [ -z "$API_KEY" ]; then
  if [ -z "$ADMIN_TOKEN" ]; then
    echo "Defina API_KEY ou ADMIN_TOKEN (para criar uma key via quota-service)." >&2
    exit 1
  fi
  API_KEY=$(curl -s -X POST "$QUOTA_URL/v1/api-keys" \
    -H "Content-Type: application/json" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    -d '{"name":"smoke-cloud","owner":"cli","limits":{"requestsPerMinute":120,"requestsPerDay":10000}}' \
    | jq -r .apiKey)
  if [ -z "$API_KEY" ] || [ "$API_KEY" = "null" ]; then
    echo "Falha ao gerar API key via quota-service." >&2
    exit 1
  fi
fi

endpoints=(
  federal
  megasena
  quina
  lotomania
  timemania
  duplasena
  loteca
  diadesorte
  supersete
  maismilionaria
)

echo "GW_URL=$GW_URL"
echo "QUOTA_URL=$QUOTA_URL"

for e in "${endpoints[@]}"; do
  url="$GW_URL/v1/$e/resultado-oficial"
  resp_file=$(mktemp)
  code=$(curl -s -o "$resp_file" -w "%{http_code}" -H "X-Api-Key: $API_KEY" "$url")
  loteria=$(jq -r '.loteria // empty' "$resp_file" 2>/dev/null || true)
  err=$(jq -r '.error // empty' "$resp_file" 2>/dev/null || true)
  if [ -n "$loteria" ]; then
    echo "$e -> $code ($loteria)"
  elif [ -n "$err" ]; then
    echo "$e -> $code (error=$err)"
  else
    echo "$e -> $code"
  fi
  rm -f "$resp_file"
  if [ "$code" != "200" ]; then
    exit 1
  fi
done

echo "OK: todos os endpoints retornaram 200."
