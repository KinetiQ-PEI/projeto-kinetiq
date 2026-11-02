#!/bin/sh
# Gera os certificados do listener TLS (8883) do Mosquitto em /certs.
#   - ca.crt / ca.key         CA de desenvolvimento (criada uma vez)
#   - server.crt / server.key certificado do broker, assinado pela CA
# O certificado do broker é refeito quando MQTT_TLS_EXTRA_SAN muda.
# Certificados só para desenvolvimento: não usar em produção.
set -eu
cd /certs

SAN="DNS:localhost,DNS:mosquitto,IP:127.0.0.1"
if [ -n "${MQTT_TLS_EXTRA_SAN:-}" ]; then
  SAN="$SAN,$MQTT_TLS_EXTRA_SAN"
fi

if [ ! -f ca.key ] || [ ! -f ca.crt ]; then
  echo "A criar a CA de desenvolvimento..."
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout ca.key -out ca.crt -subj "/O=KinetiQ/CN=KinetiQ Dev CA"
  rm -f server.crt
fi

if [ -f server.crt ] && [ "$(cat server.san 2>/dev/null)" = "$SAN" ]; then
  echo "Certificados já existem (SAN: $SAN)"
  exit 0
fi

echo "A criar o certificado do broker (SAN: $SAN)..."
cat > /tmp/server.ext <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = $SAN
EOF
openssl req -newkey rsa:2048 -nodes -keyout server.key -out /tmp/server.csr -subj "/O=KinetiQ/CN=mosquitto"
openssl x509 -req -in /tmp/server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -extfile /tmp/server.ext -out server.crt
echo "$SAN" > server.san

# O Mosquitto corre como utilizador 1883 e tem de conseguir ler a chave
chmod 644 ca.crt server.crt server.key
chmod 600 ca.key
