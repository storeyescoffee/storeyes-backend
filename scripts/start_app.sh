#!/bin/bash
set -e

cd /opt/storeyes

mkdir -p /opt/storeyes/certs
if [ ! -f /opt/storeyes/certs/global-bundle.pem ]; then
  cp /opt/storeyes/certs_artifact/global-bundle.pem /opt/storeyes/certs/global-bundle.pem 2>/dev/null || \
  curl -o /opt/storeyes/certs/global-bundle.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem
fi

docker rm -f storeyes-backend || true

docker compose \
  --env-file /opt/storeyes/.env \
  up -d --force-recreate
