#!/bin/sh
# Cria ou atualiza cada connector de /connectors (nome do connector = nome do ficheiro sem .json).
# PUT /connectors/<nome>/config é idempotente: cria se não existir, atualiza se já existir.
# Voltar a correr este script também reinicia tasks que tenham falhado.
set -eu
CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"

names=""
for file in /connectors/*.json; do
  name=$(basename "$file" .json)
  names="$names $name"
  echo "A registar $name..."
  curl -sS --fail-with-body -o /dev/null -X PUT -H 'Content-Type: application/json' \
    --data @"$file" "$CONNECT_URL/connectors/$name/config"
done

# Uma task que falhe (ex.: arrancou com o broker MQTT em baixo) não recupera sozinha
sleep 5
for name in $names; do
  curl -sS -o /dev/null -X POST \
    "$CONNECT_URL/connectors/$name/restart?includeTasks=true&onlyFailed=true" || true
done

sleep 5
failed=0
for name in $names; do
  status=$(curl -sS --fail-with-body "$CONNECT_URL/connectors/$name/status")
  echo "$status"
  case "$status" in *FAILED*) failed=1 ;; esac
done
exit $failed
