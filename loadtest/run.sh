#!/usr/bin/env bash
# Runs the k6 load test from the grafana/k6 Docker image against the docker compose stack.
# Usage (from the repo root, with `docker compose up -d` running):
#   loadtest/run.sh no_contention
#   loadtest/run.sh contention
set -euo pipefail
SCENARIO="${1:-no_contention}"
cd "$(dirname "$0")"
NETWORK="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$(docker compose ps -q app)")"
docker run --rm --network "$NETWORK" -v "$PWD:/scripts" \
  -e BASE_URL=http://app:8080 -e SCENARIO="$SCENARIO" \
  grafana/k6:1.3.0 run --quiet --summary-export="/scripts/results/$SCENARIO.json" /scripts/booking.js
