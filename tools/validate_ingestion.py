#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["confluent-kafka>=2.6", "jsonschema>=4.23"]
# ///
"""Valida o pipeline de ingestão MQTT -> Kafka (KIQ-44).

Lê o tópico telemetry.device.raw a partir de um instante e verifica, para as mensagens das bicicletas
(chave kinetiq/bikes/<bikeId>/telemetry):

- JSON válido e conforme docs/schemas/telemetry-schema.json (sem corrupção);
- chave Kafka coerente com o bikeId do payload;
- eventIds duplicados (QoS 1 pode duplicar) e, com --expected-per-bike, mensagens em falta;
- ritmo de publicação por bicicleta (intervalo mediano entre mensagens);
- latência de trânsito = timestamp do registo Kafka - timestamp do payload (hora de publicação MQTT).

As mensagens da HeartBox (heartbox/...) são ignoradas. O simulador e o Kafka Connect correm na mesma
máquina, por isso os relógios são os mesmos e a diferença de timestamps é a latência real.

Uso (com a infraestrutura a correr, ver README "Validar o pipeline"):
    uv run tools/validate_ingestion.py --expected-per-bike 60
    uv run tools/validate_ingestion.py --since-seconds 120      # analisa os últimos 2 minutos
Sem uv: pip install confluent-kafka jsonschema && python3 tools/validate_ingestion.py ...
"""

import argparse
from collections import defaultdict
from dataclasses import dataclass, field
import json
from pathlib import Path
import statistics
import sys
import time
from uuid import uuid4

TOPIC = "telemetry.device.raw"
KEY_PREFIX = "kinetiq/bikes/"
KEY_SUFFIX = "/telemetry"
SCHEMA_PATH = Path(__file__).resolve().parent.parent / "docs" / "schemas" / "telemetry-schema.json"
DEFAULT_BOOTSTRAP = "localhost:9092"
DEFAULT_MAX_LATENCY_MS = 100.0
DEFAULT_INTERVAL_SECONDS = 1.0
DEFAULT_MIN_BIKES = 5
DEFAULT_DURATION_SECONDS = 90.0
DEFAULT_IDLE_SECONDS = 15.0
INTERVAL_TOLERANCE = 0.2  # ±20% do intervalo pedido
POLL_TIMEOUT_SECONDS = 0.5
MAX_ERRORS_SHOWN = 10


@dataclass
class Record:
    key: str | None
    value: bytes | None
    kafka_timestamp_ms: int | None


@dataclass
class Report:
    total: int = 0
    ignored: int = 0
    errors: list[str] = field(default_factory=list)
    duplicates: int = 0
    latencies_ms: list[float] = field(default_factory=list)
    per_bike: dict[str, list[int]] = field(default_factory=lambda: defaultdict(list))


def percentile(values, p):
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(p / 100 * (len(ordered) - 1))))
    return ordered[index]


def analyse(records, validator):
    """Analisa os registos e devolve um Report. Não depende do Kafka (testável à parte)."""
    report = Report()
    seen = set()
    for record in records:
        report.total += 1
        if not record.key or not record.key.startswith(KEY_PREFIX):
            report.ignored += 1
            continue
        try:
            event = json.loads(record.value)
        except (TypeError, ValueError, UnicodeDecodeError) as error:
            report.errors.append(f"{record.key}: JSON inválido ({error})")
            continue
        problems = [e.message for e in validator.iter_errors(event)]
        if problems:
            report.errors.append(f"{record.key}: fora do schema ({'; '.join(problems)})")
            continue
        expected_key = f"{KEY_PREFIX}{event['bikeId']}{KEY_SUFFIX}"
        if record.key != expected_key:
            report.errors.append(f"{record.key}: chave não corresponde ao bikeId {event['bikeId']}")
            continue
        if event["eventId"] in seen:
            report.duplicates += 1
            continue
        seen.add(event["eventId"])
        report.per_bike[event["bikeId"]].append(event["timestamp"])
        if record.kafka_timestamp_ms is not None:
            report.latencies_ms.append(record.kafka_timestamp_ms - event["timestamp"])
    return report


def evaluate(report, *, interval, max_latency_ms, min_bikes, expected_per_bike):
    """Devolve a lista de (critério, passou, detalhe)."""
    results = []
    valid = sum(len(t) for t in report.per_bike.values())

    results.append(("Payloads JSON íntegros e conformes ao schema", not report.errors,
                    f"{valid} válidas, {len(report.errors)} com erro"))

    bikes = len(report.per_bike)
    results.append((f"Pelo menos {min_bikes} bicicletas concorrentes", bikes >= min_bikes,
                    f"{bikes} bicicletas"))

    medians = {}
    for bike, stamps in report.per_bike.items():
        stamps = sorted(stamps)
        gaps = [(b - a) / 1000 for a, b in zip(stamps, stamps[1:])]
        if gaps:
            medians[bike] = statistics.median(gaps)
    off = {b: m for b, m in medians.items() if abs(m - interval) > interval * INTERVAL_TOLERANCE}
    results.append((f"Uma mensagem a cada {interval:g} s por bicicleta", bool(medians) and not off,
                    "intervalo mediano " + (", ".join(f"{b}={m:.2f}s" for b, m in sorted(off.items()))
                                            if off else f"{min(medians.values(), default=0):.2f}–"
                                            f"{max(medians.values(), default=0):.2f} s")))

    if report.latencies_ms:
        p50, p95, p99 = (percentile(report.latencies_ms, p) for p in (50, 95, 99))
        worst = max(report.latencies_ms)
        results.append((f"Latência MQTT -> Kafka < {max_latency_ms:g} ms (p95)", p95 < max_latency_ms,
                        f"p50={p50:.0f} ms, p95={p95:.0f} ms, p99={p99:.0f} ms, máx={worst:.0f} ms"))
    else:
        results.append((f"Latência MQTT -> Kafka < {max_latency_ms:g} ms (p95)", False, "sem dados"))

    if expected_per_bike is not None:
        short = {b: len(t) for b, t in report.per_bike.items() if len(t) < expected_per_bike}
        results.append((f"Sem perdas ({expected_per_bike} mensagens por bicicleta)", bikes > 0 and not short,
                        ", ".join(f"{b}: {n}/{expected_per_bike}" for b, n in sorted(short.items()))
                        or f"{valid} mensagens únicas"))

    results.append(("Duplicados (QoS 1, informativo)", True, f"{report.duplicates} eventIds repetidos"))
    return results


def consume(bootstrap, start_ms, duration, idle, stop_when_complete):
    """Lê o tópico a partir de start_ms; para ao fim de duration, de idle s sem mensagens,
    ou quando stop_when_complete(registos) for verdadeiro."""
    from confluent_kafka import Consumer, KafkaException, TopicPartition

    consumer = Consumer({
        "bootstrap.servers": bootstrap,
        "group.id": f"kiq44-validator-{uuid4().hex[:8]}",
        "enable.auto.commit": False,
        "auto.offset.reset": "latest",
    })

    def on_assign(c, partitions):
        wanted = [TopicPartition(p.topic, p.partition, start_ms) for p in partitions]
        c.assign(c.offsets_for_times(wanted, timeout=10))

    consumer.subscribe([TOPIC], on_assign=on_assign)
    records = []
    deadline = time.monotonic() + duration
    last_message = None  # o tempo de inatividade só conta depois da primeira mensagem
    try:
        while time.monotonic() < deadline and (last_message is None or time.monotonic() - last_message < idle):
            msg = consumer.poll(POLL_TIMEOUT_SECONDS)
            if msg is None:
                continue
            if msg.error():
                raise KafkaException(msg.error())
            last_message = time.monotonic()
            ts_type, ts = msg.timestamp()
            key = msg.key().decode("utf-8", errors="replace") if msg.key() else None
            records.append(Record(key, msg.value(), ts if ts_type else None))
            if stop_when_complete(records):
                break
    finally:
        consumer.close()
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bootstrap", default=DEFAULT_BOOTSTRAP, help="Kafka (por omissão localhost:9092)")
    parser.add_argument("--since-seconds", type=float, default=0,
                        help="começa N s antes de agora (0: só mensagens novas)")
    parser.add_argument("--duration", type=float, default=DEFAULT_DURATION_SECONDS,
                        help="tempo máximo de leitura em s")
    parser.add_argument("--idle", type=float, default=DEFAULT_IDLE_SECONDS,
                        help="termina após N s sem mensagens (depois de chegar a primeira)")
    parser.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_SECONDS,
                        help="intervalo esperado entre mensagens de cada bicicleta")
    parser.add_argument("--max-latency-ms", type=float, default=DEFAULT_MAX_LATENCY_MS)
    parser.add_argument("--min-bikes", type=int, default=DEFAULT_MIN_BIKES)
    parser.add_argument("--expected-per-bike", type=int,
                        help="mensagens esperadas por bicicleta (o mesmo N do --messages-per-bike)")
    args = parser.parse_args()

    from jsonschema import Draft7Validator, FormatChecker
    validator = Draft7Validator(json.loads(SCHEMA_PATH.read_text()), format_checker=FormatChecker())

    start_ms = int((time.time() - args.since_seconds) * 1000)

    # Payloads distintos por bicicleta (um duplicado QoS 1 traz os mesmos bytes)
    payloads = defaultdict(set)

    def complete(records):
        if args.expected_per_bike is None:
            return False
        last = records[-1]
        if last.key and last.key.startswith(KEY_PREFIX):
            payloads[last.key].add(last.value)
        return (len(payloads) >= args.min_bikes
                and all(len(v) >= args.expected_per_bike for v in payloads.values()))

    print(f"A ler {TOPIC} em {args.bootstrap} (Ctrl+C para terminar)...", flush=True)
    try:
        records = consume(args.bootstrap, start_ms, args.duration, args.idle, complete)
    except KeyboardInterrupt:
        print("Interrompido.")
        return 130
    report = analyse(records, validator)
    results = evaluate(report, interval=args.interval, max_latency_ms=args.max_latency_ms,
                       min_bikes=args.min_bikes, expected_per_bike=args.expected_per_bike)

    print(f"\n{report.total} registos lidos ({report.ignored} de outros dispositivos ignorados)\n")
    for bike, stamps in sorted(report.per_bike.items()):
        print(f"  {bike}: {len(stamps)} mensagens")
    print()
    for name, passed, detail in results:
        print(f"  [{'OK' if passed else 'FALHA'}] {name}: {detail}")
    for error in report.errors[:MAX_ERRORS_SHOWN]:
        print(f"    - {error}")
    if len(report.errors) > MAX_ERRORS_SHOWN:
        print(f"    ... e mais {len(report.errors) - MAX_ERRORS_SHOWN}")
    return 0 if all(passed for _, passed, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())