"""Geração local de telemetria fictícia na zona de Aveiro (KIQ-36)."""

import argparse
from datetime import datetime, timedelta, timezone
import json
import math
import random
import re
from uuid import uuid4

SECONDS_PER_HOUR = 3600
MILLISECONDS_PER_SECOND = 1000
DEFAULT_SAMPLE_INTERVAL_SECONDS = 1.0
DEFAULT_SAMPLE_COUNT = 5
BIKE_ID_PATTERN = r"MB-[0-9]{4}-[0-9]{3}"
BIKE_ID_TEMPLATE = "MB-{year:04d}-{index:03d}"
MIN_BIKE_YEAR = 1000
MAX_BIKE_YEAR = 9999
MAX_BIKE_INDEX = 999
SCHEMA_VERSION = "1.0"


class BikeSimulator:
    """Uma bicicleta num circuito circular fictício, sem correspondência às ruas.

    Cada chamada a next_sample avança o relógio simulado e o movimento.
    Velocidade em km/h, odómetro em km e bateria em percentagem.
    """

    CENTER_LATITUDE = 40.6405
    CENTER_LONGITUDE = -8.6538
    EARTH_RADIUS_KM = 6371.0
    ROUTE_RADIUS_KM = 0.6
    BATTERY_PERCENT_PER_KM = 1.25
    INITIAL_BATTERY_PERCENT = 100.0
    MIN_SPEED_KMH = 12.0
    MAX_SPEED_KMH = 24.0
    GPS_DECIMAL_PLACES = 7
    SPEED_DECIMAL_PLACES = 3
    ODOMETER_DECIMAL_PLACES = 6

    def __init__(self, bike_id: str, *, seed: int | None = None):
        if not isinstance(bike_id, str) or not re.fullmatch(BIKE_ID_PATTERN, bike_id):
            raise ValueError("bike_id deve seguir o formato MB-AAAA-NNN")
        self.bike_id = bike_id
        self._random = random.Random(seed)
        self._angle = self._random.uniform(0, math.tau)
        self._timestamp = datetime.now(timezone.utc)
        self.odometer = 0.0
        self.battery_level = self.INITIAL_BATTERY_PERCENT

    def next_sample(self, elapsed_seconds: float = DEFAULT_SAMPLE_INTERVAL_SECONDS) -> dict:
        """Avança um intervalo e devolve um evento conforme o schema do projeto."""
        if not math.isfinite(elapsed_seconds) or elapsed_seconds <= 0:
            raise ValueError("elapsed_seconds deve ser finito e maior que zero")
        timestamp = self._timestamp + timedelta(seconds=elapsed_seconds)
        requested_speed = self._random.uniform(self.MIN_SPEED_KMH, self.MAX_SPEED_KMH)
        distance = min(
            requested_speed * elapsed_seconds / SECONDS_PER_HOUR,
            self.battery_level / self.BATTERY_PERCENT_PER_KM,
        )
        speed = distance * SECONDS_PER_HOUR / elapsed_seconds
        self.odometer += distance
        self.battery_level = max(
            0.0, self.battery_level - distance * self.BATTERY_PERCENT_PER_KM
        )
        self._angle = (self._angle + distance / self.ROUTE_RADIUS_KM) % math.tau
        self._timestamp = timestamp

        # Conversão local de deslocamentos em km para graus, adequada a este circuito.
        radius_degrees = math.degrees(self.ROUTE_RADIUS_KM / self.EARTH_RADIUS_KM)
        latitude = self.CENTER_LATITUDE + radius_degrees * math.cos(self._angle)
        longitude = self.CENTER_LONGITUDE + (
            radius_degrees * math.sin(self._angle)
            / math.cos(math.radians(self.CENTER_LATITUDE))
        )
        return {
            "schemaVersion": SCHEMA_VERSION,
            "eventId": str(uuid4()),
            "bikeId": self.bike_id,
            "deviceId": f"simulator-{self.bike_id}",
            "timestamp": int(timestamp.timestamp() * MILLISECONDS_PER_SECOND),
            "location": {
                "latitude": round(latitude, self.GPS_DECIMAL_PLACES),
                "longitude": round(longitude, self.GPS_DECIMAL_PLACES),
            },
            "metrics": {
                "speed": round(speed, self.SPEED_DECIMAL_PLACES),
                "batteryLevel": round(self.battery_level),
                "odometer": round(self.odometer, self.ODOMETER_DECIMAL_PLACES),
            },
            "status": "IN_USE" if self.battery_level > 0 else "IN_MAINTENANCE",
        }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bike-id", default=BIKE_ID_TEMPLATE.format(
        year=datetime.now(timezone.utc).year, index=1))
    parser.add_argument("--samples", type=int, default=DEFAULT_SAMPLE_COUNT)
    parser.add_argument("--interval", type=float, default=DEFAULT_SAMPLE_INTERVAL_SECONDS,
                        help="segundos simulados entre amostras; não espera em tempo real")
    parser.add_argument("--seed", type=int, help="semente para reproduzir o movimento")
    args = parser.parse_args()
    if args.samples < 1:
        parser.error("--samples deve ser maior que zero")
    if not math.isfinite(args.interval) or args.interval <= 0:
        parser.error("--interval deve ser finito e maior que zero")
    try:
        bike = BikeSimulator(args.bike_id, seed=args.seed)
    except ValueError as error:
        parser.error(str(error))
    for _ in range(args.samples):
        print(json.dumps(bike.next_sample(args.interval), allow_nan=False), flush=True)


if __name__ == "__main__":
    main()
