"""Teste opt-in contra um broker MQTT real; ver README.md."""

from collections import defaultdict
import json
import os
from pathlib import Path
from queue import Queue
import subprocess
import sys
import threading
import time
import unittest

import paho.mqtt.client as mqtt

from publisher import Config
from test_telemetry import VALIDATOR


@unittest.skipUnless(os.getenv("RUN_MQTT_INTEGRATION") == "1", "requer RUN_MQTT_INTEGRATION=1 e broker")
class MqttIntegrationTests(unittest.TestCase):
    def test_five_bikes_publish_valid_moving_telemetry(self):
        config = Config.from_env()
        received = Queue()
        subscribed = threading.Event()
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        if config.username:
            client.username_pw_set(config.username, config.password)
        client.on_subscribe = lambda *args: subscribed.set()
        client.on_message = lambda client, userdata, message: received.put((message.topic, message.payload))
        client.connect(config.host, config.port)
        client.loop_start()
        try:
            client.subscribe(config.topic_template.format(bike_id="+"), qos=1)
            self.assertTrue(subscribed.wait(5), "Subscrição não confirmada")
            env = {**os.environ, "SIM_BIKE_COUNT": "5", "SIM_INTERVAL_SECONDS": "0.2",
                   "SIM_BIKE_YEAR": "2026", "SIM_SEED": "36"}
            started = time.time_ns() // 1_000_000
            result = subprocess.run(
                [sys.executable, str(Path(__file__).with_name("publisher.py")), "--messages-per-bike", "3"],
                env=env, capture_output=True, text=True, timeout=20,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            events = defaultdict(dict)
            deadline = time.monotonic() + 5
            while sum(len(items) for items in events.values()) < 15:
                topic, payload = received.get(timeout=max(0.01, deadline - time.monotonic()))
                sample = json.loads(payload)
                VALIDATOR.validate(sample)
                self.assertEqual(topic, config.topic_template.format(bike_id=sample["bikeId"]))
                self.assertGreaterEqual(sample["timestamp"], started)
                self.assertLessEqual(sample["timestamp"], time.time_ns() // 1_000_000)
                events[sample["bikeId"]][sample["eventId"]] = sample
            self.assertEqual(set(events), {f"MB-2026-{index:03d}" for index in range(1, 6)})
            for samples in events.values():
                ordered = sorted(samples.values(), key=lambda sample: sample["timestamp"])
                self.assertEqual(len(ordered), 3)
                for previous, current in zip(ordered, ordered[1:]):
                    self.assertGreater(current["timestamp"], previous["timestamp"])
                    self.assertGreater(current["metrics"]["odometer"], previous["metrics"]["odometer"])
                    self.assertNotEqual(current["location"], previous["location"])
            # Os intervalos de atividade das cinco bicicletas sobrepõem-se.
            first = [min(s["timestamp"] for s in samples.values()) for samples in events.values()]
            last = [max(s["timestamp"] for s in samples.values()) for samples in events.values()]
            self.assertLess(max(first), min(last))
        finally:
            client.disconnect()
            client.loop_stop()


if __name__ == "__main__":
    unittest.main()
