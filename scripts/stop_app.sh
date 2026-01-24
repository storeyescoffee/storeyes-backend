#!/bin/bash
docker stop storeyes-backend || true
docker rm storeyes-backend || true

docker stop mediamtx || true
docker rm mediamtx || true

docker compose down || true