#!/usr/bin/env bash
set -euo pipefail

# Smoke local do MVP Agendamento (requer gateway + scheduling-service rodando).
# Uso:
#   GW_URL=http://localhost:8080 \
#   SCHEDULING_URL=http://localhost:8097 \
#   API_KEY=<opcional> \
#   ./scripts/smoke-scheduling-local.sh
#
# Recomendado no scheduling-service local:
#   SCHEDULING_AGENDA_DEFAULT_CREDITS=1

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }
}

require curl
require jq

GW_URL=${GW_URL:-http://localhost:8080}
SCHEDULING_URL=${SCHEDULING_URL:-http://localhost:8097}
NOTES="Preferencia por tesoura."

status_code=$(curl -s -o /dev/null -w "%{http_code}" "$SCHEDULING_URL/v1/servicos" || true)
if [ "$status_code" = "200" ]; then
  echo "ERROR: scheduling-service respondeu 200 sem X-Service-Token"
  exit 1
fi
echo "Direct scheduling-service status=$status_code (esperado 401/403)"

if [ -z "${API_KEY:-}" ]; then
  echo "API_KEY nao informado, solicitando via /v1/keys/request..."
  EMAIL="teste+$(date +%s)@exemplo.com"
  API_KEY=$(curl -s -X POST "$GW_URL/v1/keys/request" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"org\":\"Acme\",\"useCase\":\"smoke agendamento\"}" | jq -r '.apiKey')
fi

if [ -z "$API_KEY" ] || [ "$API_KEY" = "null" ]; then
  echo "Falha ao obter API_KEY via /v1/keys/request"
  exit 1
fi

mask_api_key() {
  local key="$1"
  local len=${#key}
  if [ "$len" -le 8 ]; then
    echo "$key"
  else
    echo "${key:0:4}...${key: -4}"
  fi
}

echo "API_KEY=$(mask_api_key "$API_KEY")"

SERVICE_ID=$(curl -s -X POST "$GW_URL/v1/servicos" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: srv-001" \
  -H "Content-Type: application/json" \
  -d '{"name":"Corte Masculino","durationMin":40,"prepMin":5,"bufferMin":10,"noShowFeeCents":2000,"active":true}' | jq -r '.id')

if [ -z "$SERVICE_ID" ] || [ "$SERVICE_ID" = "null" ]; then
  echo "Falha ao criar servico"
  exit 1
fi
echo "SERVICE_ID=$SERVICE_ID"

AGENDA_ID=$(curl -s -X POST "$GW_URL/v1/agendas" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: agd-001" \
  -H "Content-Type: application/json" \
  -d '{"name":"Agenda Principal","timezone":"America/Sao_Paulo","workingHoursStart":"08:00","workingHoursEnd":"18:00","slotStepMin":15,"noShowFeeCents":2000,"active":true}' | jq -r '.id')

if [ -z "$AGENDA_ID" ] || [ "$AGENDA_ID" = "null" ]; then
  echo "Falha ao criar agenda"
  exit 1
fi
echo "AGENDA_ID=$AGENDA_ID"

START_AT=$(curl -s "$GW_URL/v1/slots-disponiveis?serviceId=$SERVICE_ID&date=2026-01-26&agendaId=$AGENDA_ID" \
  -H "X-Api-Key: $API_KEY" | jq -r '.slots[0].startAt')

if [ -z "$START_AT" ] || [ "$START_AT" = "null" ]; then
  echo "Falha ao obter slots"
  exit 1
fi
echo "START_AT=$START_AT"

APPOINTMENT_ID=$(curl -s -X POST "$GW_URL/v1/reservar" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: res-001" \
  -H "Content-Type: application/json" \
  -d "{\"serviceId\":\"$SERVICE_ID\",\"agendaId\":\"$AGENDA_ID\",\"startAt\":\"$START_AT\",\"customer\":{\"name\":\"Ana\",\"phone\":\"+5588999990000\",\"email\":\"ana@email.com\"},\"notes\":\"$NOTES\"}" | jq -r '.appointmentId')

if [ -z "$APPOINTMENT_ID" ] || [ "$APPOINTMENT_ID" = "null" ]; then
  echo "Falha ao reservar"
  exit 1
fi
echo "APPOINTMENT_ID=$APPOINTMENT_ID"

echo "CONFIRM="
curl -s -X POST "$GW_URL/v1/confirmar" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: conf-001" \
  -H "Content-Type: application/json" \
  -d "{\"appointmentId\":\"$APPOINTMENT_ID\"}" | jq

APPOINTMENT_ID_2=$(curl -s -X POST "$GW_URL/v1/reservar" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: res-001" \
  -H "Content-Type: application/json" \
  -d "{\"serviceId\":\"$SERVICE_ID\",\"agendaId\":\"$AGENDA_ID\",\"startAt\":\"$START_AT\",\"customer\":{\"name\":\"Ana\",\"phone\":\"+5588999990000\",\"email\":\"ana@email.com\"},\"notes\":\"$NOTES\"}" | jq -r '.appointmentId')

if [ "$APPOINTMENT_ID" = "$APPOINTMENT_ID_2" ]; then
  echo "IDEMPOTENCY_OK"
else
  echo "IDEMPOTENCY_FAIL"
  exit 1
fi

SECOND_START=$(curl -s "$GW_URL/v1/slots-disponiveis?serviceId=$SERVICE_ID&date=2026-01-26&agendaId=$AGENDA_ID" \
  -H "X-Api-Key: $API_KEY" | jq -r '.slots[1].startAt')
if [ -z "$SECOND_START" ] || [ "$SECOND_START" = "null" ]; then
  SECOND_START=$(python - <<PY
from datetime import datetime, timezone, timedelta
import sys
start = "$START_AT"
dt = datetime.fromisoformat(start.replace("Z", "+00:00"))
print((dt + timedelta(minutes=15)).astimezone(timezone.utc).isoformat().replace("+00:00","Z"))
PY
)
fi

APPOINTMENT_ID_3=$(curl -s -X POST "$GW_URL/v1/reservar" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: res-002" \
  -H "Content-Type: application/json" \
  -d "{\"serviceId\":\"$SERVICE_ID\",\"agendaId\":\"$AGENDA_ID\",\"startAt\":\"$SECOND_START\",\"customer\":{\"name\":\"Ana\",\"phone\":\"+5588999990000\",\"email\":\"ana@email.com\"},\"notes\":\"$NOTES\"}" | jq -r '.appointmentId')

CONFIRM_2=$(curl -s -X POST "$GW_URL/v1/confirmar" \
  -H "X-Api-Key: $API_KEY" \
  -H "Idempotency-Key: conf-002" \
  -H "Content-Type: application/json" \
  -d "{\"appointmentId\":\"$APPOINTMENT_ID_3\"}")

if echo "$CONFIRM_2" | jq -e '.error == "AGENDA_CREDITS_EXCEEDED"' >/dev/null; then
  echo "AGENDA_CREDITS_EXCEEDED_OK"
else
  echo "AGENDA_CREDITS_EXCEEDED_FAIL"
  echo "$CONFIRM_2" | jq
  exit 1
fi
