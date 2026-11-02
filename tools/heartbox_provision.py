#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["bleak>=0.22"]
# ///
"""Aponta as placas ESP32 do Mobile HeartBox (PECI g7) para o Mosquitto do KinetiQ.

Grava por Bluetooth (BLE), em cada placa, o SSID e a password do Wi-Fi e o IP do broker MQTT.
É o mesmo que a app Flutter do PECI faz, mas a app tem fixo no código o IP de um servidor Azure.
Ao receber o IP, cada placa reinicia ao fim de ~5 s e passa a publicar em heartbox/... na porta 1883.

Uso:
    uv run tools/heartbox_provision.py --scan
    uv run tools/heartbox_provision.py --ssid "MeuHotspot" [--password ...] [--broker 172.20.10.2]
Sem uv: pip install bleak && python3 tools/heartbox_provision.py ...
"""

import argparse
import asyncio
import getpass
import socket
import sys

from bleak import BleakClient, BleakScanner

# Características de configuração (iguais nas três placas)
CHAR_SSID = "ab35e54e-fde4-4f83-902a-07785de547b9"
CHAR_PASSWORD = "c1c4b63b-bf3b-4e35-9077-d5426226c710"
CHAR_BROKER_IP = "0c954d7e-9249-456d-b949-cc079205d393"

# Serviço BLE anunciado por cada placa
BOARD_SERVICES = {
    "0a3b6985-dad6-4759-8852-dcb266d3a59e": "ESP32-S3 (BPM, GPS, quedas)",
    "7e408544-2ab3-4581-b541-1188318e8df5": "ESP32-S3-Zero (proximidade)",
    "f4b82d49-43c2-48df-b3f5-7ba9e0231908": "ESP32-CAM (temperatura)",
}

NOT_FOUND_HELP = """Nenhuma placa encontrada. As placas só aceitam configuração em modo BLE:
  - a ESP32-S3 está sempre em modo BLE;
  - a S3-Zero e a CAM só entram em modo BLE se não tiverem Wi-Fi guardado ou se não
    conseguirem ligar-se a ele: desliga a rede que elas conhecem e reinicia-as (~30 s)."""


def default_broker_ip() -> str:
    """IP do portátil na rede por onde sai o tráfego (o connect UDP não envia pacotes)."""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]


async def find_boards(timeout: float):
    found = await BleakScanner.discover(timeout=timeout, return_adv=True)
    boards = []
    for device, adv in found.values():
        uuids = [u.lower() for u in adv.service_uuids]
        label = next((BOARD_SERVICES[u] for u in uuids if u in BOARD_SERVICES), None)
        name = adv.local_name or device.name or ""
        if label or name.endswith("Heart_Box"):
            boards.append((device, label or name))
    return boards


async def provision(device, label: str, values, attempts: int = 3) -> bool:
    for attempt in range(1, attempts + 1):
        try:
            async with BleakClient(device, timeout=20) as client:
                # O IP vai em último: ao recebê-lo, a placa grava tudo e reinicia
                for char_uuid, value in values:
                    await client.write_gatt_char(char_uuid, value.encode(), response=True)
                    await asyncio.sleep(0.3)
            print(f"  OK     {label} [{device.address}]")
            return True
        except Exception as e:  # BLE no ESP32 falha com frequência à primeira
            print(f"  falhou {label}: tentativa {attempt}/{attempts} ({e})")
            await asyncio.sleep(2 * attempt)
    return False


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--scan", action="store_true", help="só listar as placas encontradas")
    parser.add_argument("--ssid", help="rede Wi-Fi para as placas (a mesma do portátil)")
    parser.add_argument("--password", help="password do Wi-Fi (pedida se omitida)")
    parser.add_argument("--broker", help="IP do portátil que corre o Docker (por omissão: detetado)")
    parser.add_argument("--timeout", type=float, default=10, help="segundos de scan BLE (10)")
    args = parser.parse_args()

    if not args.scan and not args.ssid:
        parser.error("indica --ssid (ou --scan para só procurar as placas)")

    broker = args.broker
    if not args.scan and not broker:
        try:
            broker = default_broker_ip()
        except OSError:
            parser.error("não consegui detetar o IP do portátil: indica --broker")

    print(f"A procurar placas HeartBox ({args.timeout:.0f} s)...")
    boards = await find_boards(args.timeout)
    if not boards:
        print(NOT_FOUND_HELP)
        return 1
    for device, label in boards:
        print(f"  {label} [{device.address}]")
    if args.scan:
        return 0

    # O firmware ignora valores vazios, por isso a rede tem de ter password
    password = args.password or getpass.getpass(f"Password do Wi-Fi '{args.ssid}': ")
    if not password:
        parser.error("o firmware não aceita redes sem password")

    print(f"A configurar Wi-Fi '{args.ssid}' e broker {broker}:1883...")
    values = [(CHAR_SSID, args.ssid), (CHAR_PASSWORD, password), (CHAR_BROKER_IP, broker)]
    ok = [await provision(device, label, values) for device, label in boards]

    print(f"\n{sum(ok)}/{len(ok)} placas configuradas. Reiniciam em ~5 s e ligam-se ao broker.")
    print("Confirmar:  docker compose exec mosquitto mosquitto_sub -v -t 'heartbox/#' -T 'heartbox/cam/#'")
    return 0 if all(ok) else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
