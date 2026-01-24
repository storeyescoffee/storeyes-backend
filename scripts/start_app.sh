#!/bin/bash
set -e

cd /opt/storeyes

docker rm -f storeyes-backend || true

docker compose \
  --env-file /opt/storeyes/.env \
  up -d --force-recreate
