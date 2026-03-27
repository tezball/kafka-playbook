#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Preflight checks
for cmd in docker mvn; do
  command -v "$cmd" &>/dev/null || { echo "ERROR: $cmd is required but not installed."; exit 1; }
done

PASSED=()
FAILED=()
SKIPPED=()

run_tests() {
  local lesson_dir="$1"
  local service_dir="$2"
  local label="${lesson_dir}/${service_dir}"
  local full_path="${lesson_dir}/${service_dir}"

  if [ ! -f "${full_path}/pom.xml" ]; then
    echo "  SKIP  ${label} (no pom.xml)"
    SKIPPED+=("$label")
    return
  fi

  if [ ! -d "${full_path}/src/test" ]; then
    echo "  SKIP  ${label} (no tests)"
    SKIPPED+=("$label")
    return
  fi

  echo -n "  TEST  ${label} ... "
  if (cd "$full_path" && mvn test -B -q 2>&1) > "/tmp/kafka-playbook-test-${lesson_dir//\//-}-${service_dir}.log" 2>&1; then
    echo "PASSED"
    PASSED+=("$label")
  else
    echo "FAILED"
    FAILED+=("$label")
    echo "        Log: /tmp/kafka-playbook-test-${lesson_dir//\//-}-${service_dir}.log"
  fi
}

echo "=========================================="
echo "  Kafka Playbook — Test Suite"
echo "=========================================="
echo ""
START_TIME=$SECONDS

echo "[01] Simple Pub/Sub"
run_tests "01-simple-pub-sub" "producer"
run_tests "01-simple-pub-sub" "consumer"

echo "[02] Fan-Out"
run_tests "02-fan-out" "producer"

echo "[03] Partitioned Processing"
run_tests "03-partitioned-processing" "producer"

echo "[04] Event Sourcing"
run_tests "04-event-sourcing" "consumer"

echo "[05] CQRS"
run_tests "05-cqrs" "query-service"

echo "[06] Dead Letter Queue"
run_tests "06-dead-letter-queue" "processor"

echo "[07] Saga / Choreography"
run_tests "07-saga-choreography" "checkout-producer"

echo "[08] Stream Enrichment"
echo "  SKIP  (TopologyTestDriver — see LESSON.md)"
SKIPPED+=("08-stream-enrichment")

echo "[09] Exactly-Once Processing"
run_tests "09-exactly-once" "processor"

echo "[10] Windowed Aggregation"
run_tests "10-windowed-aggregation" "aggregator"

echo "[11] Change Data Capture"
echo "  SKIP  (CDC testing — see LESSON.md)"
SKIPPED+=("11-change-data-capture")

echo "[12] Schema Evolution"
run_tests "12-schema-evolution" "consumer"

echo "[13] Outbox Pattern"
run_tests "13-outbox-pattern" "order-service"

ELAPSED=$(( SECONDS - START_TIME ))

echo ""
echo "=========================================="
echo "  Results"
echo "=========================================="
echo ""
echo "  Passed:  ${#PASSED[@]}"
echo "  Failed:  ${#FAILED[@]}"
echo "  Skipped: ${#SKIPPED[@]}"
echo "  Time:    ${ELAPSED}s"
echo ""

if [ ${#PASSED[@]} -gt 0 ]; then
  for p in "${PASSED[@]}"; do echo "  ✓ $p"; done
  echo ""
fi

if [ ${#SKIPPED[@]} -gt 0 ]; then
  for s in "${SKIPPED[@]}"; do echo "  — $s"; done
  echo ""
fi

if [ ${#FAILED[@]} -gt 0 ]; then
  for f in "${FAILED[@]}"; do echo "  ✗ $f"; done
  echo ""
  echo "  Check logs in /tmp/kafka-playbook-test-*.log"
  exit 1
fi

echo "  All tests passed!"
