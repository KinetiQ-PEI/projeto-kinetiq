FROM confluentinc/cp-kafka-connect:8.0.2

ARG JDBC_VERSION=10.8.4
ARG INFLUXDB_VERSION=1.2.12
ARG MQTT_VERSION=12.1.2

# JDBC Sink (-> PostgreSQL/PostGIS) e InfluxDB Sink
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:${JDBC_VERSION} \
 && confluent-hub install --no-prompt confluentinc/kafka-connect-influxdb:${INFLUXDB_VERSION}

# MQTT Source (Lenses stream-reactor, Apache 2.0)
RUN curl -fsSL -o /tmp/mqtt.zip \
      https://github.com/lensesio/stream-reactor/releases/download/${MQTT_VERSION}/kafka-connect-mqtt-${MQTT_VERSION}.zip \
 && python3 -m zipfile -e /tmp/mqtt.zip /usr/share/confluent-hub-components/ \
 && rm /tmp/mqtt.zip
