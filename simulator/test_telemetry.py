"""Verificação do contrato KIQ-36 e da coerência física do gerador."""

from datetime import datetime, timedelta
import json
import math
import unittest

from telemetry import BikeSimulator


class TelemetryTests(unittest.TestCase):
    def test_json_contract_and_movement(self):
        bike = BikeSimulator("MB-2026-001", seed=36)
        previous = None
        for _ in range(1000):
            sample = json.loads(json.dumps(bike.next_sample(2), allow_nan=False))
            self.assertEqual(set(sample), {
                "bike_id", "timestamp", "latitude", "longitude", "speed",
                "battery_level", "odometer",
            })
            self.assertEqual(sample["bike_id"], "MB-2026-001")
            timestamp = datetime.fromisoformat(sample["timestamp"].replace("Z", "+00:00"))
            self.assertEqual(timestamp.utcoffset(), timedelta(0))
            self.assertTrue(40.63 < sample["latitude"] < 40.65)
            self.assertTrue(-8.67 < sample["longitude"] < -8.64)
            self.assertTrue(12 <= sample["speed"] <= 24)
            self.assertTrue(0 <= sample["battery_level"] <= 100)
            if previous:
                self.assertEqual(timestamp - previous_time, timedelta(seconds=2))
                distance = sample["odometer"] - previous["odometer"]
                self.assertAlmostEqual(distance, sample["speed"] * 2 / 3600, delta=0.000002)
                self.assertLess(sample["battery_level"], previous["battery_level"])
                # Distância GPS aproximada deve acompanhar a distância percorrida.
                north = math.radians(sample["latitude"] - previous["latitude"]) * 6371
                east = (math.radians(sample["longitude"] - previous["longitude"])
                        * 6371 * math.cos(math.radians(sample["latitude"])))
                self.assertAlmostEqual(math.hypot(north, east), distance, delta=0.00003)
            previous, previous_time = sample, timestamp

    def test_empty_battery_stops_movement(self):
        bike = BikeSimulator("MB-2026-002", seed=36)
        exhausted = bike.next_sample(24 * 3600)
        stopped = bike.next_sample(1)
        self.assertEqual(exhausted["battery_level"], 0)
        self.assertEqual(stopped["speed"], 0)
        for field in ("latitude", "longitude", "odometer", "battery_level"):
            self.assertEqual(exhausted[field], stopped[field])

    def test_invalid_input(self):
        for bike_id in ("", "   ", None):
            with self.subTest(bike_id=bike_id), self.assertRaises(ValueError):
                BikeSimulator(bike_id)
        bike = BikeSimulator("MB-2026-003")
        for interval in (0, -1, float("nan"), float("inf")):
            with self.subTest(interval=interval), self.assertRaises(ValueError):
                bike.next_sample(interval)


if __name__ == "__main__":
    unittest.main()
