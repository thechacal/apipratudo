#!/usr/bin/env bash
set -euo pipefail

REGION="${REGION:-southamerica-east1}"
GW_URL="${GW_URL:-}"
QUOTA_URL="${QUOTA_URL:-}"
API_KEY="${API_KEY:-}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
SMOKE_SHOW_JSON="${SMOKE_SHOW_JSON:-0}"
SMOKE_SUMMARY="${SMOKE_SUMMARY:-0}"

TOKENS_FILE="/tmp/apipratudo-cloud/tokens.env"
API_KEY_FILE="/tmp/apipratudo-cloud/api_key"

if [ -z "$API_KEY" ] && [ -z "$ADMIN_TOKEN" ] && [ -f "$TOKENS_FILE" ]; then
  # shellcheck source=/dev/null
  source "$TOKENS_FILE"
fi

if [ -z "$API_KEY" ] && [ -f "$API_KEY_FILE" ]; then
  API_KEY=$(tr -d '\n' < "$API_KEY_FILE")
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Comando obrigatorio nao encontrado: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq
require_cmd gcloud

refresh_api_key() {
  if [ -z "$ADMIN_TOKEN" ]; then
    echo "API key invalida e ADMIN_TOKEN nao definido para regenerar." >&2
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
  printf "%s" "$API_KEY" > "$API_KEY_FILE"
}

print_summary() {
  local file="$1"
  if jq -e . >/dev/null 2>&1 < "$file"; then
    local loteria concurso dataApuracao dezenas timeCoracao
    loteria=$(jq -r '.loteria // empty' "$file")
    concurso=$(jq -r '.concurso // empty' "$file")
    dataApuracao=$(jq -r '.dataApuracao // empty' "$file")
    dezenas=$(jq -c '.dezenas // empty' "$file")
    timeCoracao=$(jq -r '.timeCoracao // empty' "$file")
    echo "  resumo: loteria=${loteria:-} concurso=${concurso:-} dataApuracao=${dataApuracao:-}"
    if [ -n "$dezenas" ] && [ "$dezenas" != "null" ]; then
      echo "  dezenas: $dezenas"
    fi
    if [ -n "$timeCoracao" ]; then
      echo "  timeCoracao: $timeCoracao"
    fi
  else
    echo "  resumo: (json invalido)"
    cat "$file"
  fi
}

print_json() {
  local file="$1"
  if jq -e . >/dev/null 2>&1 < "$file"; then
    jq . "$file"
  else
    cat "$file"
  fi
}

if [ -z "$GW_URL" ]; then
  GW_URL=$(gcloud run services describe api-gateway --region "$REGION" --format='value(status.url)')
fi

if [ -z "$QUOTA_URL" ]; then
  QUOTA_URL=$(gcloud run services describe quota-service --region "$REGION" --format='value(status.url)')
fi

if [ -z "$API_KEY" ]; then
  refresh_api_key
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

refreshed=0

for e in "${endpoints[@]}"; do
  url="$GW_URL/v1/$e/resultado-oficial"
  while true; do
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
    if [ "$SMOKE_SUMMARY" = "1" ]; then
      print_summary "$resp_file"
    fi
    if [ "$SMOKE_SHOW_JSON" = "1" ]; then
      print_json "$resp_file"
    fi
    rm -f "$resp_file"
    if [ "$code" = "401" ] && [ "$refreshed" -eq 0 ]; then
      refresh_api_key
      refreshed=1
      continue
    fi
    if [ "$code" != "200" ]; then
      exit 1
    fi
    break
  done
done

echo "OK: todos os endpoints retornaram 200."
