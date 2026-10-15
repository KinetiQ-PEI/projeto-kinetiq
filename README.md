# projeto-kinetiq
KinetiQ PEI 2026/2027

## Ambiente base (Docker Compose)

Infraestrutura partilhada pelos serviços Spring Boot. Requisitos: Docker + Docker Compose v2.

```bash
docker compose up -d --build   # arrancar tudo (o 1.º arranque faz build do Kafka Connect)
docker compose ps              # estado dos serviços (esperar por "healthy")
docker compose logs -f kafka
docker compose down            # parar (adicionar -v para apagar todos os dados)
```

Credenciais por omissão em [.env.example](.env.example). Para alterar, copiar para `.env`.

| Serviço       | Host (o teu PC)                 | Dentro do compose     | Para quê                                   |
|---------------|---------------------------------|-----------------------|--------------------------------------------|
| Mosquitto     | `localhost:1883` / `:9001` (WS) | `mosquitto:1883`      | ESP32 e simuladores publicam aqui          |
| Kafka         | `localhost:9092`                | `kafka:29092`         | event bus                                  |
| Kafka Connect | http://localhost:8083           | `kafka-connect:8083`  | MQTT Source, JDBC Sink, InfluxDB Sink      |
| Kafka UI      | http://localhost:8089           | –                     | ver tópicos, mensagens e connectors        |
| InfluxDB 2.7  | http://localhost:8086           | `influxdb:8086`       | histórico de telemetria (bucket `telemetry`) |
| PostgreSQL 17 + PostGIS | `localhost:5432`      | `postgres:5432`       | dados operacionais (BD `kinetiq`)          |
| Keycloak 26   | http://localhost:8180           | `keycloak:8080`       | autenticação (admin / admin)               |

A porta 8080 fica livre para o API Gateway.

Tópicos Kafka criados no arranque: `bike.telemetry`, `fleet.alerts`.

### Connectors

As configs estão em [infra/kafka-connect/connectors/](infra/kafka-connect/connectors/). Para registar uma:

```bash
curl -X POST -H 'Content-Type: application/json' \
  --data @infra/kafka-connect/connectors/mqtt-source.json localhost:8083/connectors
curl localhost:8083/connectors/mqtt-source-telemetry/status
```

O `mqtt-source-telemetry` copia o MQTT `kinetiq/bikes/<bikeId>/telemetry` para o Kafka `bike.telemetry`.

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

### Testes rápidos

```bash
# Terminal 1: ver o que chega ao Kafka
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bike.telemetry

# Terminal 2: publicar como se fosse uma bicicleta
docker compose exec mosquitto mosquitto_pub -t kinetiq/bikes/MB-2026-042/telemetry \
  -m '{"bikeId":"MB-2026-042","metrics":{"speed":18.5,"batteryLevel":77}}'
```

### Ligar hardware real ao broker MQTT

O dispositivo liga-se ao **IP da máquina que corre o Docker** (ex.: `192.168.x.y:1883`), nunca a `localhost`.
O IP vê-se com `ip -4 addr` (Linux) / `ipconfig` (Windows).

A **eduroam isola os clientes entre si** e o ESP32 não suporta facilmente WPA2-Enterprise, por isso o
hardware não consegue chegar ao broker por lá. Usar um hotspot (telemóvel ou portátil) e ligar
o portátil e o dispositivo à mesma rede.
