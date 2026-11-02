"""Configuração e propagação de falhas da publicação concorrente."""

from dataclasses import replace
import threading
import unittest
from unittest.mock import Mock, patch

import paho.mqtt.client as mqtt

from publisher import Config, publish_bike, wait_until


class PublisherTests(unittest.TestCase):
    def setUp(self):
        with patch.dict("os.environ", {}, clear=True):
            self.config = Config.from_env()

    def test_defaults_match_existing_topic(self):
        self.assertEqual(self.config.bike_count, 5)
        self.assertEqual(self.config.topic_template.format(bike_id="MB-2026-001"),
                         "kinetiq/bikes/MB-2026-001/telemetry")

    def test_invalid_configuration_is_rejected_before_connecting(self):
        for key, value in (
            ("SIM_BIKE_COUNT", "4"), ("SIM_BIKE_COUNT", "1000"),
            ("MQTT_PORT", "0"), ("MQTT_HOST", " "), ("SIM_BIKE_YEAR", "26"),
            ("SIM_INTERVAL_SECONDS", "nan"), ("SIM_INTERVAL_SECONDS", "0"),
            ("MQTT_TIMEOUT_SECONDS", "inf"), ("MQTT_PASSWORD", "secret"),
            ("MQTT_TOPIC_TEMPLATE", "telemetry/#/{bike_id}"),
            ("MQTT_TOPIC_TEMPLATE", "telemetry/{bike_id}/{unknown}"),
            ("MQTT_TOPIC_TEMPLATE", "telemetry/fixed"),
        ):
            with self.subTest(key=key, value=value), patch.dict("os.environ", {key: value}, clear=True):
                with self.assertRaises(ValueError):
                    Config.from_env()

    def test_missing_publish_confirmation_fails(self):
        client = Mock()
        client.is_connected.return_value = True
        client.publish.return_value.rc = mqtt.MQTT_ERR_SUCCESS
        client.publish.return_value.is_published.return_value = False
        config = replace(self.config, interval=0.001, timeout=0.01)
        with self.assertRaisesRegex(TimeoutError, "confirmação MQTT"):
            publish_bike(client, config, 1, threading.Event(), 1)

    def test_stop_interrupts_wait(self):
        stop = threading.Event()
        stop.set()
        self.assertFalse(wait_until(lambda: False, stop, 10, "timeout"))


if __name__ == "__main__":
    unittest.main()
