#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SKIP_TESTS=false
if [[ "${1:-}" == "--skip-tests" ]]; then
  SKIP_TESTS=true
fi

# Detect OS for browser open command
open_browser() {
  local url="$1"
  if command -v xdg-open &>/dev/null; then
    xdg-open "$url" &>/dev/null &
  elif command -v open &>/dev/null; then
    open "$url"
  elif command -v wslview &>/dev/null; then
    wslview "$url"
  else
    echo "  Open manually: $url"
  fi
}

wait_for_health() {
  local name="$1" url="$2" timeout="$3"
  local elapsed=0
  echo ">>> Waiting for ${name}..."
  until curl -sf "$url" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "ERROR: ${name} did not become healthy within ${timeout}s"
      echo "Check logs: docker compose logs"
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    printf "\r    %ds / %ds" "$elapsed" "$timeout"
  done
  echo ""
}

# Preflight checks
for cmd in docker curl; do
  command -v "$cmd" &>/dev/null || { echo "ERROR: $cmd is required but not installed."; exit 1; }
done
docker compose version &>/dev/null || { echo "ERROR: docker compose plugin is required."; exit 1; }

echo "=========================================="
echo "  Kafka Playbook — 11 Change Data Capture"
echo "=========================================="
echo ""

if [ "$SKIP_TESTS" = false ]; then
  # Tests: see LESSON.md for testing guidance (TopologyTestDriver / CDC testing)
  echo ">>> Skipping tests (see LESSON.md for testing approach)"
  echo ""
else
  echo ">>> Skipping tests (--skip-tests flag)"
fi

# Stop and remove containers, volumes, and networks from a previous run
echo ">>> Tearing down previous run..."
docker compose down -v --remove-orphans 2>/dev/null || true

# Remove any orphaned containers with conflicting names (from other compose projects)
for name in kafka postgres kafka-connect data-seeder cdc-consumer akhq; do
  docker rm -f "$name" 2>/dev/null || true
done

# Build (reuses cached base images and Maven dependency layers) and start
echo ">>> Building and starting services (first run downloads deps — be patient)..."
docker compose up --build -d

# Wait for services
wait_for_health "Kafka"         "http://localhost:9092"                     60
wait_for_health "Postgres"      "http://localhost:5432"                     60
wait_for_health "Kafka Connect" "http://localhost:8083/connectors"          180
wait_for_health "Data Seeder"   "http://localhost:8080/actuator/health"     180
wait_for_health "CDC Consumer"  "http://localhost:8081/actuator/health"     180
wait_for_health "AKHQ"          "http://localhost:8888"                     60

# Register Debezium Postgres source connector
echo ">>> Registering Debezium Postgres source connector..."
curl -sf -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connectors/source-postgres.json
echo ""

# Wait a moment for connector to start and snapshot initial data
echo ">>> Waiting for connector to start..."
sleep 5

# Verify connector is running
echo ">>> Connector status:"
curl -s http://localhost:8083/connectors/postgres-source/status | python3 -m json.tool 2>/dev/null || \
  curl -s http://localhost:8083/connectors/postgres-source/status
echo ""

# Open AKHQ in browser — CDC topic view
echo ">>> Opening AKHQ in browser..."
open_browser "http://localhost:8888/ui/kafka-playbook/topic/dbserver1.public.products/data?sort=NEWEST&partition=All"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "  Kafka:          localhost:9092"
echo "  Postgres:       localhost:5432"
echo "  Kafka Connect:  http://localhost:8083"
echo "  Data Seeder:    http://localhost:8080"
echo "  CDC Consumer:   http://localhost:8081"
echo "  AKHQ:           http://localhost:8888"
echo ""
echo "  AKHQ Views:"
echo "    Topics:          http://localhost:8888/ui/kafka-playbook/topic"
echo "    CDC Messages:    http://localhost:8888/ui/kafka-playbook/topic/dbserver1.public.products/data?sort=NEWEST&partition=All"
echo "    Consumer Groups: http://localhost:8888/ui/kafka-playbook/group"
echo ""
echo "  Products are being modified every 10 seconds."
echo ""
echo "  Try:"
echo "    docker compose logs -f cdc-consumer         # watch CDC event diffs"
echo "    curl -s localhost:8080/api/products | jq     # list products"
echo "    curl -X POST localhost:8080/api/products/sample  # trigger a random change"
echo "    curl -s localhost:8083/connectors/postgres-source/status | jq"
echo ""
echo "  See LESSON.md for the full exercise guide."
echo "=========================================="
echo ""

# Tail data-seeder and cdc-consumer logs so the user sees events flowing live
echo ">>> Tailing data-seeder & cdc-consumer logs (Ctrl+C to stop)..."
docker compose logs -f --tail=50 data-seeder cdc-consumer
