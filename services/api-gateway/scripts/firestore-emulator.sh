#!/usr/bin/env bash
set -euo pipefail

HOST_PORT=${FIRESTORE_EMULATOR_HOST:-localhost:8085}
LOG_FILE=${FIRESTORE_EMULATOR_LOG:-/tmp/firestore-emulator.log}

if ! command -v gcloud >/dev/null 2>&1; then
  echo "gcloud nao encontrado. Instale o Google Cloud SDK para usar o emulator." >&2
  exit 1
fi

HOST=${HOST_PORT%:*}
PORT=${HOST_PORT##*:}

if [[ -z "${HOST}" || -z "${PORT}" ]]; then
  echo "FIRESTORE_EMULATOR_HOST deve estar no formato host:porta" >&2
  exit 1
fi

gcloud beta emulators firestore start --host-port="${HOST_PORT}" --quiet >"${LOG_FILE}" 2>&1 &
EMULATOR_PID=$!

cleanup() {
  if kill -0 "${EMULATOR_PID}" >/dev/null 2>&1; then
    kill "${EMULATOR_PID}" >/dev/null 2>&1 || true
    wait "${EMULATOR_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

for _ in {1..30}; do
  if (echo >"/dev/tcp/${HOST}/${PORT}") >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if ! kill -0 "${EMULATOR_PID}" >/dev/null 2>&1; then
    echo "Emulator encerrou. Veja logs em ${LOG_FILE}" >&2
    exit 1
  fi
done

export FIRESTORE_EMULATOR_HOST="${HOST_PORT}"
export GOOGLE_CLOUD_PROJECT="${GOOGLE_CLOUD_PROJECT:-emulator-test}"
export APP_FIRESTORE_ENABLED=true

mvn test
