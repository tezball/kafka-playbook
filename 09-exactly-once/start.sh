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
echo "  Kafka Playbook — 09 Exactly-Once"
echo "=========================================="
echo ""

if [ "$SKIP_TESTS" = false ]; then
  # Run tests before starting services
  echo ">>> Running tests (requires Docker for Testcontainers)..."
  run_tests() {
    local service_dir="$1"
    local service_name="$2"
    echo "    Testing ${service_name}..."
    docker run --rm \
      -v "${PWD}/${service_dir}":/app \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -w /app \
      --network host \
      maven:3.9-eclipse-temurin-21 \
      mvn test -B -q 2>&1 | tail -5
    if [ ${PIPESTATUS[0]} -ne 0 ]; then
      echo "ERROR: Tests failed for ${service_name}"
      exit 1
    fi
    echo "    ✓ ${service_name} tests passed"
  }

  run_tests "processor" "processor"
  echo ""
else
  echo ">>> Skipping tests (--skip-tests flag)"
fi

# Stop and remove containers, volumes, and networks from a previous run
echo ">>> Tearing down previous run..."
docker compose down -v --remove-orphans 2>/dev/null || true

# Remove any orphaned containers with conflicting names (from other compose projects)
for name in kafka transfer-producer transfer-processor akhq; do
  docker rm -f "$name" 2>/dev/null || true
done

# Build (reuses cached base images and Maven dependency layers) and start
echo ">>> Building and starting services (first run downloads deps — be patient)..."
docker compose up --build -d

# Wait for services
wait_for_health "Producer" "http://localhost:8080/actuator/health" 180
wait_for_health "Processor" "http://localhost:8081/actuator/health" 180
wait_for_health "AKHQ" "http://localhost:8888" 60

# Open AKHQ in browser — topic detail view shows live messages
echo ">>> Opening AKHQ in browser..."
open_browser "http://localhost:8888/ui/kafka-playbook/topic/transfer-requests/data?sort=NEWEST&partition=All"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "  Kafka:     localhost:9092"
echo "  Producer:  http://localhost:8080"
echo "  Processor: http://localhost:8081"
echo "  AKHQ:      http://localhost:8888"
echo ""
echo "  AKHQ Views:"
echo "    Topics:            http://localhost:8888/ui/kafka-playbook/topic"
echo "    Transfer Requests: http://localhost:8888/ui/kafka-playbook/topic/transfer-requests/data?sort=NEWEST&partition=All"
echo "    Account Debits:    http://localhost:8888/ui/kafka-playbook/topic/account-debits/data?sort=NEWEST&partition=All"
echo "    Account Credits:   http://localhost:8888/ui/kafka-playbook/topic/account-credits/data?sort=NEWEST&partition=All"
echo "    Consumer Groups:   http://localhost:8888/ui/kafka-playbook/group"
echo ""
echo "  Transfers are being auto-generated every 10 seconds."
echo ""
echo "  Try:"
echo "    docker compose logs -f processor             # watch atomic transfers"
echo "    curl -s localhost:8080/api/transfers/sample   # send a manual transfer"
echo ""
echo "  See LESSON.md for the full exercise guide."
echo "=========================================="
echo ""

# Tail producer and processor logs so the user sees events flowing live
echo ">>> Tailing producer & processor logs (Ctrl+C to stop)..."
docker compose logs -f --tail=50 producer processor
