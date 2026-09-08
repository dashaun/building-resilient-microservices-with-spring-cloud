#!/bin/bash
# Start the complete reference in separate macOS Terminal windows.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [[ "$(uname -s)" != Darwin ]]; then
  echo "Run ../mvnw spring-boot:run from each service directory; see labs/README.md." >&2
  exit 1
fi

open_service() {
  local module="$1" port="$2" command
  printf -v command 'cd %q && ' "$ROOT/$module"
  if [[ -n "${JAVA_HOME:-}" ]]; then
    printf -v command '%sexport JAVA_HOME=%q && ' "$command" "$JAVA_HOME"
  fi
  printf -v command '%sSERVER_PORT=%q %q spring-boot:run' "$command" "$port" "$ROOT/mvnw"
  osascript - "$command" <<'APPLESCRIPT' >/dev/null
on run argv
  tell application "Terminal" to do script (item 1 of argv)
end run
APPLESCRIPT
}

wait_for() {
  local url="$1"
  for ((attempt=0; attempt<90; attempt++)); do
    if curl --max-time 2 -fsS "$url" >/dev/null; then return; fi
    sleep 1
  done
  echo "Timed out waiting for $url. Check its service terminal before continuing." >&2
  exit 1
}

# Run docker compose up -d --wait from the repository root first.
open_service eureka-server 8761
wait_for http://localhost:8761
open_service config-server 8888
wait_for http://localhost:8888/survey-service/default
open_service gateway 8080
wait_for http://localhost:8080/actuator/health
open_service survey-service 8081
wait_for http://localhost:8081/questions
open_service survey-service 8082
wait_for http://localhost:8082/questions
open_service results-service 8083
wait_for http://localhost:8083/actuator/health
open_service survey-ui 8091
wait_for http://localhost:8091
# Allow Eureka's registry caches to catch up before declaring the front door ready.
wait_for http://localhost:8080/survey-service/questions
wait_for http://localhost:8080

echo "Ready: http://localhost:8080 — Eureka :8761 — RabbitMQ :15672 — Grafana :3000"
