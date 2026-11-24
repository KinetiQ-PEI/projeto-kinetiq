#!/bin/bash
# Configuração do InfluxDB do KinetiQ. Corre uma vez, no primeiro arranque (volume vazio),
# depois do setup inicial da imagem (que cria a org, o bucket "telemetry" e o token de admin).
#
#   telemetry         dados brutos por bicicleta, 30 dias (GDPR: ver slide "What we collect and why")
#   telemetry_hourly  médias por hora, 400 dias (BI e previsão de procura sem guardar o detalhe)
#
# Para voltar a correr num ambiente que já existe: docker compose down -v (apaga os dados) e up.
set -euo pipefail

ORG="${DOCKER_INFLUXDB_INIT_ORG}"
RAW_BUCKET="${DOCKER_INFLUXDB_INIT_BUCKET}"
HOURLY_BUCKET="${INFLUXDB_HOURLY_BUCKET:-telemetry_hourly}"
HOURLY_RETENTION="${INFLUXDB_HOURLY_RETENTION:-400d}"
SINK_USER="${INFLUXDB_SINK_USER:-kafka-connect}"
SINK_PASSWORD="${INFLUXDB_SINK_PASSWORD:-kafka-connect-dev}"

echo "[kinetiq] bucket ${HOURLY_BUCKET} (${HOURLY_RETENTION})"
influx bucket create --org "${ORG}" --name "${HOURLY_BUCKET}" --retention "${HOURLY_RETENTION}" \
  --description "Agregados por hora da telemetria (KinetiQ)"

RAW_ID=$(influx bucket list --org "${ORG}" --name "${RAW_BUCKET}" --hide-headers | cut -f1)

# API v1 (compatibilidade): o InfluxDB Sink do Kafka Connect escreve pela API 1.x.
echo "[kinetiq] mapeamento v1 ${RAW_BUCKET} -> bucket ${RAW_ID}"
influx v1 dbrp create --org "${ORG}" --db "${RAW_BUCKET}" --rp autogen --bucket-id "${RAW_ID}" --default
influx v1 auth create --org "${ORG}" --username "${SINK_USER}" --password "${SINK_PASSWORD}" \
  --write-bucket "${RAW_ID}" --description "Kafka Connect InfluxDB Sink"

# Task: de hora a hora, resume a última hora de cada bicicleta no bucket telemetry_hourly.
TASK_FILE=$(mktemp)
cat > "${TASK_FILE}" <<FLUX
option task = {name: "telemetry_hourly_downsample", every: 1h, offset: 5m}

raw = from(bucket: "${RAW_BUCKET}")
    |> range(start: -task.every)
    |> filter(fn: (r) => r._measurement == "bike_telemetry")

raw
    |> filter(fn: (r) => r._field == "speed_kmh" or r._field == "battery_level")
    |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
    |> map(fn: (r) => ({r with _field: r._field + "_mean"}))
    |> set(key: "_measurement", value: "bike_telemetry_hourly")
    |> to(bucket: "${HOURLY_BUCKET}", org: "${ORG}")

raw
    |> filter(fn: (r) => r._field == "odometer_km")
    |> aggregateWindow(every: 1h, fn: spread, createEmpty: false)
    |> set(key: "_field", value: "distance_km")
    |> set(key: "_measurement", value: "bike_telemetry_hourly")
    |> to(bucket: "${HOURLY_BUCKET}", org: "${ORG}")
FLUX
echo "[kinetiq] task telemetry_hourly_downsample"
influx task create --org "${ORG}" --file "${TASK_FILE}"
rm -f "${TASK_FILE}"

echo "[kinetiq] InfluxDB configurado"
