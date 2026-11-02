"""Publicação concorrente da frota simulada no broker MQTT do KinetiQ."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
import json
import logging
import math
import os
import signal
import threading
import time
from uuid import uuid4

import paho.mqtt.client as mqtt

from telemetry import BikeSimulator

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class Config:
    host: str
    port: int
    topic_template: str
    bike_count: int
    bike_year: int
    interval: float
    timeout: float
    seed: int | None
    username: str | None
    password: str | None

    @classmethod
    def from_env(cls):
        config = cls(
            host=os.getenv("MQTT_HOST", "localhost"),
            port=int(os.getenv("MQTT_PORT", "1883")),
            topic_template=os.getenv("MQTT_TOPIC_TEMPLATE", "kinetiq/bikes/{bike_id}/telemetry"),
            bike_count=int(os.getenv("SIM_BIKE_COUNT", "5")),
            bike_year=int(os.getenv("SIM_BIKE_YEAR", str(datetime.now(timezone.utc).year))),
            interval=float(os.getenv("SIM_INTERVAL_SECONDS", "2")),
            timeout=float(os.getenv("MQTT_TIMEOUT_SECONDS", "10")),
            seed=int(os.environ["SIM_SEED"]) if "SIM_SEED" in os.environ else None,
            username=os.getenv("MQTT_USERNAME") or None,
            password=os.getenv("MQTT_PASSWORD") or None,
        )
        if not config.host.strip() or not 1 <= config.port <= 65535:
            raise ValueError("MQTT_HOST/MQTT_PORT inválidos")
        if not 5 <= config.bike_count <= 999:
            raise ValueError("SIM_BIKE_COUNT deve estar entre 5 e 999")
        if not 1000 <= config.bike_year <= 9999:
            raise ValueError("SIM_BIKE_YEAR deve ter quatro dígitos")
        for name, value in (("SIM_INTERVAL_SECONDS", config.interval),
                            ("MQTT_TIMEOUT_SECONDS", config.timeout)):
            if not math.isfinite(value) or value <= 0:
                raise ValueError(f"{name} deve ser finito e maior que zero")
        if config.password and not config.username:
            raise ValueError("MQTT_PASSWORD requer MQTT_USERNAME")
        if config.topic_template.count("{bike_id}") != 1:
            raise ValueError("MQTT_TOPIC_TEMPLATE deve conter {bike_id} uma vez")
        try:
            topic = config.topic_template.format(bike_id="MB-2026-001")
        except (KeyError, ValueError, IndexError, AttributeError) as error:
            raise ValueError("MQTT_TOPIC_TEMPLATE inválido") from error
        if any(char in topic for char in ("+", "#", "\0")) or len(topic.encode()) > 65535:
            raise ValueError("MQTT_TOPIC_TEMPLATE não pode conter wildcards ou exceder 65535 bytes")
        return config


def wait_until(predicate, stop, timeout, description):
    """Espera limitada e interrompível, incluindo durante falhas do broker."""
    deadline = time.monotonic() + timeout
    while not stop.is_set():
        if predicate():
            return True
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError(description)
        stop.wait(min(0.05, remaining))
    return False


def publish_bike(client, config, bike_index, stop, messages_per_bike):
    bike_id = f"MB-{config.bike_year:04d}-{bike_index:03d}"
    bike = BikeSimulator(bike_id, seed=None if config.seed is None else config.seed + bike_index)
    topic = config.topic_template.format(bike_id=bike_id)
    previous = time.monotonic()
    deadline = previous + config.interval
    sent = 0
    while not stop.wait(max(0, deadline - time.monotonic())):
        if not wait_until(client.is_connected, stop, config.timeout, "Broker MQTT indisponível"):
            return
        now = time.monotonic()
        sample = bike.next_sample(now - previous)
        # Na publicação, o timestamp é a hora real de recolha; o movimento usa tempo decorrido.
        sample["timestamp"] = time.time_ns() // 1_000_000
        previous = now
        result = client.publish(topic, json.dumps(sample, allow_nan=False), qos=1, retain=False)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError(f"{bike_id}: publicação recusada ({result.rc})")
        if not wait_until(result.is_published, stop, config.timeout,
                          f"{bike_id}: confirmação MQTT não recebida"):
            return
        sent += 1
        LOGGER.info("%s: mensagem %d confirmada em %s", bike_id, sent, topic)
        if messages_per_bike is not None and sent >= messages_per_bike:
            return
        # Não envia rajadas para compensar atrasos de rede.
        deadline += config.interval
        if deadline < time.monotonic():
            deadline = time.monotonic() + config.interval


def run(config, stop, messages_per_bike=None):
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                         client_id=f"kinetiq-simulator-{uuid4().hex[:12]}")
    client.connect_timeout = config.timeout
    client.reconnect_delay_set(min_delay=1, max_delay=5)
    client.max_queued_messages_set(config.bike_count)
    if config.username:
        client.username_pw_set(config.username, config.password)

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            LOGGER.error("Ligação MQTT rejeitada: %s", reason_code)
        else:
            LOGGER.info("Ligado a %s:%d; %d bicicletas", config.host, config.port, config.bike_count)

    def on_disconnect(client, userdata, flags, reason_code, properties):
        if not stop.is_set() and reason_code.is_failure:
            LOGGER.warning("Ligação MQTT perdida; a tentar reconectar: %s", reason_code)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.connect_async(config.host, config.port, keepalive=30)
    client.loop_start()
    try:
        if not wait_until(client.is_connected, stop, config.timeout, "Não foi possível ligar ao broker MQTT"):
            return
        with ThreadPoolExecutor(max_workers=config.bike_count, thread_name_prefix="bike") as pool:
            futures = [pool.submit(publish_bike, client, config, index, stop, messages_per_bike)
                       for index in range(1, config.bike_count + 1)]
            try:
                while not stop.is_set():
                    for future in futures:
                        if future.done():
                            future.result()  # Propaga falhas das threads para o processo principal.
                    if all(future.done() for future in futures):
                        for future in futures:
                            future.result()
                        break
                    stop.wait(0.05)
            finally:
                stop.set()
    finally:
        client.disconnect()
        client.loop_stop()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--messages-per-bike", type=int,
                        help="termina após N mensagens confirmadas por bicicleta (omitir: contínuo)")
    args = parser.parse_args()
    if args.messages_per_bike is not None and args.messages_per_bike < 1:
        parser.error("--messages-per-bike deve ser maior que zero")
    try:
        config = Config.from_env()
    except ValueError as error:
        parser.error(str(error))
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    try:
        run(config, stop, args.messages_per_bike)
    except (OSError, RuntimeError, ValueError) as error:
        LOGGER.error("Simulação interrompida: %s", error)
        return 1
    LOGGER.info("Simulação terminada")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
