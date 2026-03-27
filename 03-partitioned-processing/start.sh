#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

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
echo "  Kafka Playbook — 03 Partitioned Processing"
echo "=========================================="
echo ""

# Stop and remove containers, volumes, and networks from a previous run
echo ">>> Tearing down previous run..."
docker compose down -v --remove-orphans 2>/dev/null || true

# Remove any orphaned containers with conflicting names (from other compose projects)
for name in kafka order-producer na-consumer eu-consumer apac-consumer akhq; do
  docker rm -f "$name" 2>/dev/null || true
done

# Build (reuses cached base images and Maven dependency layers) and start
echo ">>> Building and starting services (first run downloads deps — be patient)..."
docker compose up --build -d

# Wait for services
wait_for_health "Producer"      "http://localhost:8080/actuator/health" 180
wait_for_health "NA Consumer"   "http://localhost:8081/actuator/health" 180
wait_for_health "EU Consumer"   "http://localhost:8082/actuator/health" 180
wait_for_health "APAC Consumer" "http://localhost:8083/actuator/health" 180
wait_for_health "AKHQ"          "http://localhost:8888" 60

# Open AKHQ in browser — topic detail view shows live messages
echo ">>> Opening AKHQ in browser..."
open_browser "http://localhost:8888/ui/kafka-playbook/topic/regional-orders/data?sort=NEWEST&partition=All"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "  Kafka:         localhost:9092"
echo "  Producer:      http://localhost:8080"
echo "  NA Consumer:   http://localhost:8081"
echo "  EU Consumer:   http://localhost:8082"
echo "  APAC Consumer: http://localhost:8083"
echo "  AKHQ:          http://localhost:8888"
echo ""
echo "  AKHQ Views:"
echo "    Topics:          http://localhost:8888/ui/kafka-playbook/topic"
echo "    Live Messages:   http://localhost:8888/ui/kafka-playbook/topic/regional-orders/data?sort=NEWEST&partition=All"
echo "    Consumer Groups: http://localhost:8888/ui/kafka-playbook/group"
echo ""
echo "  Orders are being auto-generated every 10 seconds."
echo ""
echo "  Try:"
echo "    docker compose logs -f na-consumer       # watch North America orders"
echo "    docker compose logs -f eu-consumer       # watch Europe orders"
echo "    docker compose logs -f apac-consumer     # watch Asia-Pacific orders"
echo "    curl -s localhost:8080/api/orders/sample  # send a manual order"
echo ""
echo "  See LESSON.md for the full exercise guide."
echo "=========================================="
echo ""

# Tail all consumer logs so the user sees events flowing live
echo ">>> Tailing consumer logs (Ctrl+C to stop)..."
docker compose logs -f --tail=50 na-consumer eu-consumer apac-consumer
