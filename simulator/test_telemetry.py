"""Verificação do contrato KIQ-36 e da coerência física do gerador."""

import json
import math
from pathlib import Path
import unittest

from jsonschema import Draft7Validator, FormatChecker

from telemetry import BikeSimulator

SCHEMA_PATH = Path(__file__).resolve().parents[1] / "docs/schemas/telemetry-schema.json"
VALIDATOR = Draft7Validator(json.loads(SCHEMA_PATH.read_text(encoding="utf-8")),
                           format_checker=FormatChecker())


class TelemetryTests(unittest.TestCase):
    def test_json_contract_and_movement(self):
        bike = BikeSimulator("MB-2026-001", seed=36)
        previous = None
        event_ids = set()
        for _ in range(1000):
            sample = json.loads(json.dumps(bike.next_sample(2), allow_nan=False))
            VALIDATOR.validate(sample)
            self.assertNotIn(sample["eventId"], event_ids)
            event_ids.add(sample["eventId"])
            self.assertEqual(sample["bikeId"], "MB-2026-001")
            self.assertEqual(sample["status"], "IN_USE")
            location, metrics = sample["location"], sample["metrics"]
            self.assertTrue(40.63 < location["latitude"] < 40.65)
            self.assertTrue(-8.67 < location["longitude"] < -8.64)
            self.assertTrue(12 <= metrics["speed"] <= 24)
            if previous:
                self.assertEqual(sample["timestamp"] - previous["timestamp"], 2000)
                distance = metrics["odometer"] - previous["metrics"]["odometer"]
                self.assertAlmostEqual(distance, metrics["speed"] * 2 / 3600, delta=0.000002)
                self.assertLessEqual(metrics["batteryLevel"], previous["metrics"]["batteryLevel"])
                # Distância GPS aproximada deve acompanhar a distância percorrida.
                north = math.radians(location["latitude"] - previous["location"]["latitude"]) * 6371
                east = (math.radians(location["longitude"] - previous["location"]["longitude"])
                        * 6371 * math.cos(math.radians(location["latitude"])))
                self.assertAlmostEqual(math.hypot(north, east), distance, delta=0.00003)
            previous = sample
        self.assertLess(sample["metrics"]["batteryLevel"], 100)

    def test_empty_battery_stops_movement(self):
        bike = BikeSimulator("MB-2026-002", seed=36)
        exhausted = bike.next_sample(24 * 3600)
        stopped = bike.next_sample(1)
        VALIDATOR.validate(stopped)
        self.assertEqual(exhausted["metrics"]["batteryLevel"], 0)
        self.assertEqual(stopped["metrics"]["speed"], 0)
        self.assertEqual(stopped["status"], "IN_MAINTENANCE")
        self.assertEqual(exhausted["location"], stopped["location"])
        self.assertEqual(exhausted["metrics"]["odometer"], stopped["metrics"]["odometer"])

    def test_invalid_input(self):
        for bike_id in ("", "   ", None, "bike-1", "MB-2026-001/other"):
            with self.subTest(bike_id=bike_id), self.assertRaises(ValueError):
                BikeSimulator(bike_id)
        bike = BikeSimulator("MB-2026-003")
        for interval in (0, -1, float("nan"), float("inf")):
            with self.subTest(interval=interval), self.assertRaises(ValueError):
                bike.next_sample(interval)


if __name__ == "__main__":
    unittest.main()
