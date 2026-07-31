#!/bin/bash
# BBQ Wednesday — start every service in its own macOS Terminal tab.
# Backing services (RabbitMQ, Redis, Grafana LGTM) come from ../../docker-compose.yml.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "🔥 BBQ Wednesday — starting the pit..."

open_tab() {  # name  module  [env]
  local name="$1" module="$2" env="$3"
  osascript -e "tell application \"Terminal\" to do script \
    \"cd '$ROOT/$module' && echo '🟢 $name' && $env '$ROOT/mvnw' spring-boot:run\"" >/dev/null
}

# Order matters: discovery + config first, then the rest.
open_tab "eureka-server"  eureka-server;   sleep 8
open_tab "config-server"  config-server;   sleep 8
open_tab "gateway"        gateway;         sleep 3
open_tab "survey-service (8081)" survey-service; sleep 2
open_tab "survey-service (8082)" survey-service "SERVER_PORT=8082"; sleep 2
open_tab "results-service" results-service; sleep 2
open_tab "survey-ui"      survey-ui

cat <<'EOF'

✅ Services launching. Give them ~30s.

   Front door (UI):     http://localhost:8080
   Eureka dashboard:    http://localhost:8761
   RabbitMQ mgmt:       http://localhost:15672   (guest/guest)
   Grafana (traces):    http://localhost:3000
EOF
