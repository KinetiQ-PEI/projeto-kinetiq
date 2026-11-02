"""Geração local de telemetria fictícia na zona de Aveiro (KIQ-36)."""

import argparse
from datetime import datetime, timedelta, timezone
import json
import math
import random


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

    def __init__(self, bike_id: str, *, seed: int | None = None):
        if not isinstance(bike_id, str) or not bike_id.strip():
            raise ValueError("bike_id deve ser uma string não vazia")
        self.bike_id = bike_id
        self._random = random.Random(seed)
        self._angle = self._random.uniform(0, math.tau)
        self._timestamp = datetime.now(timezone.utc)
        self.odometer = 0.0
        self.battery_level = 100.0

    def next_sample(self, elapsed_seconds: float = 1.0) -> dict:
        """Avança um intervalo positivo e devolve os sete campos da KIQ-36."""
        if not math.isfinite(elapsed_seconds) or elapsed_seconds <= 0:
            raise ValueError("elapsed_seconds deve ser finito e maior que zero")
        timestamp = self._timestamp + timedelta(seconds=elapsed_seconds)
        requested_speed = self._random.uniform(12.0, 24.0)
        distance = min(
            requested_speed * elapsed_seconds / 3600,
            self.battery_level / self.BATTERY_PERCENT_PER_KM,
        )
        speed = distance * 3600 / elapsed_seconds
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
            "bike_id": self.bike_id,
            "timestamp": timestamp.isoformat(timespec="microseconds").replace("+00:00", "Z"),
            "latitude": round(latitude, 7),
            "longitude": round(longitude, 7),
            "speed": round(speed, 3),
            "battery_level": round(self.battery_level, 6),
            "odometer": round(self.odometer, 6),
        }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bike-id", default="MB-2026-001")
    parser.add_argument("--samples", type=int, default=5)
    parser.add_argument("--interval", type=float, default=1.0,
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
