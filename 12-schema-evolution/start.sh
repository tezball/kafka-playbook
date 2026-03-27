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
echo "  Kafka Playbook — 12 Schema Evolution"
echo "=========================================="
echo ""

# Stop and remove containers, volumes, and networks from a previous run
echo ">>> Tearing down previous run..."
docker compose down -v --remove-orphans 2>/dev/null || true

# Remove any orphaned containers with conflicting names (from other compose projects)
for name in kafka schema-registry order-producer-v1 order-producer-v2 versioned-consumer akhq; do
  docker rm -f "$name" 2>/dev/null || true
done

# Build (reuses cached base images and Maven dependency layers) and start
echo ">>> Building and starting services (first run downloads deps — be patient)..."
docker compose up --build -d

# Wait for services
wait_for_health "Kafka"           "http://localhost:9092"                  60
wait_for_health "Schema Registry" "http://localhost:8085/subjects"         90
wait_for_health "Producer V1"     "http://localhost:8080/actuator/health"  180
wait_for_health "Producer V2"     "http://localhost:8082/actuator/health"  180
wait_for_health "Consumer"        "http://localhost:8081/actuator/health"  180
wait_for_health "AKHQ"            "http://localhost:8888"                  60

# Open AKHQ in browser — topic detail view shows live messages
echo ">>> Opening AKHQ in browser..."
open_browser "http://localhost:8888/ui/kafka-playbook/topic/versioned-events/data?sort=NEWEST&partition=All"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "  Kafka:           localhost:9092"
echo "  Schema Registry: http://localhost:8085"
echo "  Producer V1:     http://localhost:8080"
echo "  Producer V2:     http://localhost:8082"
echo "  Consumer:        http://localhost:8081"
echo "  AKHQ:            http://localhost:8888"
echo ""
echo "  AKHQ Views:"
echo "    Topics:          http://localhost:8888/ui/kafka-playbook/topic"
echo "    Live Messages:   http://localhost:8888/ui/kafka-playbook/topic/versioned-events/data?sort=NEWEST&partition=All"
echo "    Consumer Groups: http://localhost:8888/ui/kafka-playbook/group"
echo ""
echo "  Schema Registry API:"
echo "    Subjects:        curl -s http://localhost:8085/subjects"
echo "    Schema versions: curl -s http://localhost:8085/subjects/versioned-events-value/versions"
echo ""
echo "  Both producers auto-generate events every 10 seconds."
echo "  V1 events have 4 fields; V2 events add shippingAddress and loyaltyTier."
echo ""
echo "  Try:"
echo "    docker compose logs -f consumer              # watch schema-aware processing"
echo "    docker compose logs -f producer-v1 producer-v2  # watch both producers"
echo "    curl -s localhost:8080/api/orders/sample      # send a manual V1 order"
echo "    curl -s localhost:8082/api/orders/sample      # send a manual V2 order"
echo ""
echo "  See LESSON.md for the full exercise guide."
echo "=========================================="
echo ""

# Tail producer and consumer logs so the user sees events flowing live
echo ">>> Tailing producer & consumer logs (Ctrl+C to stop)..."
docker compose logs -f --tail=50 producer-v1 producer-v2 consumer
