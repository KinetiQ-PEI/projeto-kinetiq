# projeto-kinetiq
KinetiQ PEI 2026/2027

## Ambiente base (Docker Compose)

Infraestrutura partilhada pelos serviços Spring Boot. Requisitos: Docker + Docker Compose v2.

```bash
docker compose up -d --build   # arrancar tudo (o 1.º arranque faz build do Kafka Connect)
docker compose ps -a           # estado dos serviços (esperar por "healthy"; os *-init/-certs terminam com Exited (0))
docker compose logs -f kafka
docker compose down            # parar (adicionar -v para apagar todos os dados)
```

Credenciais por omissão em [.env.example](.env.example). Para alterar, copiar para `.env`.

| Serviço       | Host (o teu PC)                 | Dentro do compose     | Para quê                                   |
|---------------|---------------------------------|-----------------------|--------------------------------------------|
| Mosquitto     | `localhost:1883` / `:8883` (TLS) / `:9001` (WS) | `mosquitto:1883` | ESP32 e simuladores publicam aqui |
| Kafka         | `localhost:9092`                | `kafka:29092`         | event bus                                  |
| Kafka Connect | http://localhost:8083           | `kafka-connect:8083`  | MQTT Source, JDBC Sink, InfluxDB Sink      |
| Kafka UI      | http://localhost:8089           | –                     | ver tópicos, mensagens e connectors        |
| InfluxDB 2.7  | http://localhost:8086           | `influxdb:8086`       | histórico de telemetria (bucket `telemetry`) |
| PostgreSQL 17 + PostGIS | `localhost:5432`      | `postgres:5432`       | dados operacionais (BD `kinetiq`)          |
| Keycloak 26   | http://localhost:8180           | `keycloak:8080`       | autenticação (admin / admin)               |

A porta 8080 fica livre para o API Gateway.

Serviços que correm uma vez e terminam:

| Serviço              | O que faz                                                                 |
|----------------------|---------------------------------------------------------------------------|
| `mosquitto-certs`    | gera a CA e o certificado TLS do Mosquitto em `infra/mosquitto/certs/`    |
| `kafka-init`         | cria os tópicos Kafka                                                     |
| `kafka-connect-init` | regista/atualiza os connectors de `infra/kafka-connect/connectors/`       |

### Pipeline de ingestão

```
ESP32 / simulador ──MQTT──▶ Mosquitto ──MQTT Source connector──▶ Kafka: telemetry.device.raw
                                                                       │
                                     Telemetry Processing Service ◀────┘
                                       ├──▶ telemetry.device.processed
                                       └──▶ telemetry.device.dlq (mensagens rejeitadas)
```

### Tópicos Kafka

Criados no arranque pelo `kafka-init` (3 partições cada):

| Tópico                       | Conteúdo                                                        | Retenção |
|------------------------------|-----------------------------------------------------------------|----------|
| `telemetry.device.raw`       | payload MQTT tal e qual (bytes); **chave = tópico MQTT de origem** | 7 dias   |
| `telemetry.device.processed` | telemetria validada e normalizada                               | 7 dias   |
| `telemetry.device.dlq`       | mensagens que o processamento rejeitou                          | 30 dias  |
| `fleet.alerts`               | alertas da frota                                                | 7 dias   |

A chave do `telemetry.device.raw` é o tópico MQTT (ex.: `kinetiq/bikes/MB-2026-042/telemetry` ou
`heartbox/heart/bpm`). Assim, quem consome sabe de onde veio cada mensagem e as mensagens do mesmo
dispositivo ficam na mesma partição, por ordem.

A DLQ tem tantas partições como o `raw` porque o `DeadLetterPublishingRecoverer` do Spring Kafka
envia cada mensagem para a mesma partição de onde ela veio.

### Connectors

As configs estão em [infra/kafka-connect/connectors/](infra/kafka-connect/connectors/) (um ficheiro
por connector, o nome do ficheiro é o nome do connector) e são registadas automaticamente pelo
`kafka-connect-init`. Depois de editar um JSON, ou se um connector aparecer `FAILED`:

```bash
docker compose up kafka-connect-init                    # regista/atualiza e reinicia tasks FAILED
curl localhost:8083/connectors/mqtt-source-telemetry/status
```

O `mqtt-source-telemetry` copia para o `telemetry.device.raw`:

- `kinetiq/bikes/<bikeId>/telemetry`: formato do KinetiQ (simuladores, JSON);
- `heartbox/gps/+`, `heartbox/heart/+`, `heartbox/alerts/+`, `heartbox/sensor/+`: sensores do PECI
  (ver abaixo). O `heartbox/cam/thermal_raw` (frames binários de 3 KB) fica de fora.

Notas sobre o connector MQTT (stream-reactor):

- Não tem `connect.mqtt.client.id` de propósito. Se a config mudar com o Mosquitto em baixo, o
  cliente MQTT antigo não é fechado. Com um ID fixo, os dois clientes expulsam-se um ao outro em
  loop (`session taken over` nos logs do Mosquitto) e deixam de chegar dados ao Kafka. Se isto
  acontecer: `docker compose restart kafka-connect`.
- Se a task arrancar com o Mosquitto em baixo, fica `FAILED` e não recupera sozinha. Por isso o
  `kafka-connect-init` espera pelo Mosquitto, e voltar a corrê-lo reinicia a task.

### MQTT com TLS (porta 8883)

No 1.º arranque, o `mosquitto-certs` cria uma CA de desenvolvimento e um certificado para
`localhost`, `mosquitto` e `127.0.0.1`. Os clientes validam o broker com o `ca.crt`:

```bash
mosquitto_sub -h localhost -p 8883 --cafile infra/mosquitto/certs/ca.crt -t 'heartbox/#' -T 'heartbox/cam/#' -v
```

Para ligar por TLS a partir de outra máquina (ex.: `192.168.1.50`), pôr `MQTT_TLS_EXTRA_SAN=IP:192.168.1.50`
no `.env` e correr `docker compose up -d && docker compose restart mosquitto`.

Por agora todos os listeners aceitam ligações anónimas, porque os ESP32 do PECI ligam-se sem
utilizador/password. Os certificados são só para desenvolvimento e não vão para o git.

### Sensores do PECI (Mobile HeartBox)

As placas ESP32 do grupo 7 de PECI publicam em texto simples (não JSON). Nenhum tópico leva o ID
da caixa, por isso duas caixas ligadas ao mesmo broker misturam os dados.

| Placa         | Tópico MQTT                 | Payload                                       | Frequência        |
|---------------|-----------------------------|-----------------------------------------------|-------------------|
| ESP32-S3      | `heartbox/gps/coords`       | `40.630512,-8.657981` ou `Sem sinal GPS`      | 2 s               |
| ESP32-S3      | `heartbox/heart/bpm`        | `72` (45–150) ou `!` (elétrodos soltos)       | 5 s               |
| ESP32-S3      | `heartbox/alerts/fall`      | `ALERTA: Queda detetada`                      | quando acontece   |
| ESP32-S3-Zero | `heartbox/sensor/proximity` | `OBSTACULO_PERTO` / `CAMINHO_LIVRE`           | quando muda       |
| ESP32-CAM     | `heartbox/sensor/thermal`   | temperatura máxima em °C, ex.: `36.42`        | ~0,5 s            |
| ESP32-CAM     | `heartbox/cam/thermal_raw`  | frame 32×24 em float32 (3072 bytes)           | ~0,5 s            |

**Apontar as placas para o teu portátil.** A app Flutter do PECI grava nas placas o IP de um servidor
Azure que já não existe. O script [tools/heartbox_provision.py](tools/heartbox_provision.py) faz o
mesmo por Bluetooth, mas com o IP do teu portátil:

```bash
uv run tools/heartbox_provision.py --scan                 # ver que placas estão em modo BLE
uv run tools/heartbox_provision.py --ssid "MeuHotspot"    # pede a password; IP do portátil detetado
```

(Sem `uv`: `pip install bleak` e `python3 tools/heartbox_provision.py ...`.)

- O portátil e as placas têm de estar na mesma rede, com password (ver a secção sobre a eduroam abaixo).
- A ESP32-S3 aceita configuração a qualquer momento. A S3-Zero e a CAM só entram em modo BLE
  quando não conseguem ligar-se ao Wi-Fi guardado: desliga a rede que elas conhecem, reinicia-as e
  espera ~30 s.
- Cada placa reinicia ~5 s depois de receber o IP.

**Ver os dados a chegar em tempo real:**

```bash
docker compose logs -f mosquitto | grep "New client"     # as placas ligaram-se? (ESP32_S3_HEARTBOX, ESP32S3ZERO-HB-..., ESPCAM-HeartBox-...)
docker compose exec mosquitto mosquitto_sub -v -t 'heartbox/#' -T 'heartbox/cam/#'   # lado MQTT (-T esconde os frames binários da câmara)
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic telemetry.device.raw --property print.key=true   # lado Kafka
```

Ou no Kafka UI: http://localhost:8089 → Topics → `telemetry.device.raw` → Messages (modo Live).

### Ligar um serviço Spring Boot

Quando o serviço corre **no host** (IDE / `./mvnw spring-boot:run`):

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  datasource:
    url: jdbc:postgresql://localhost:5432/kinetiq
    username: kinetiq
    password: kinetiq
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/kinetiq
```

Quando corre **dentro do compose**, os hosts mudam para `kafka:29092`, `postgres:5432` e `influxdb:8086`.
O Keycloak emite sempre os tokens com issuer `http://localhost:8180/...`. Dentro do compose, usar
`jwk-set-uri: http://keycloak:8080/realms/kinetiq/protocol/openid-connect/certs`.

Para consumir o `telemetry.device.raw`, usar `StringDeserializer` na chave (tópico MQTT) e
`ByteArrayDeserializer` no valor (os payloads dos sensores do PECI não são JSON).

### Testes rápidos

```bash
# Terminal 1: ver o que chega ao Kafka
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic telemetry.device.raw --property print.key=true

# Terminal 2: publicar como se fosse uma bicicleta...
docker compose exec mosquitto mosquitto_pub -t kinetiq/bikes/MB-2026-042/telemetry \
  -m '{"bikeId":"MB-2026-042","metrics":{"speed":18.5,"batteryLevel":77}}'

# ...ou como se fosse a HeartBox
docker compose exec mosquitto mosquitto_pub -t heartbox/heart/bpm -m 72
```

### Ligar hardware real ao broker MQTT

O dispositivo liga-se ao **IP da máquina que corre o Docker** (ex.: `192.168.x.y:1883`), nunca a `localhost`.
O IP vê-se com `ip -4 addr` (Linux) / `ipconfig` (Windows).

A **eduroam isola os clientes entre si** e o ESP32 não suporta facilmente WPA2-Enterprise, por isso o
hardware não consegue chegar ao broker por lá. Usar um hotspot (telemóvel ou portátil) e ligar
o portátil e o dispositivo à mesma rede.
