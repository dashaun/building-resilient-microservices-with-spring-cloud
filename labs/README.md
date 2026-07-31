# BBQ Wednesday — Hands-On Lab

One multi-module Maven project, [`bbq-wednesday`](bbq-wednesday), builds the whole system.
It is the **complete, compiling reference implementation**: the slide exercises walk you
through adding each Spring Cloud capability, and every exercise is followed by its answer
slide, so you can copy the answer and catch up without switching branches.

```bash
cd bbq-wednesday
sdk env                             # JDK 21
./mvnw -q -DskipTests package       # builds all six services
```

## Modules

| Module | Port | Adds (workshop module) |
|---|---:|---|
| `eureka-server` | 8761 | Discovery |
| `config-server` | 8888 | Config (native backend → `config-repo/`) |
| `gateway` | 8080 | Gateway + Security + edge rate limiting |
| `survey-service` | 8081/8082 | Resilience (breaker/retry/fallback/limiter) + Stream producer |
| `results-service` | 8083 | Stream consumer + live tally |
| `survey-ui` | 8091 | Chart.js front end |

## Start Order

Discovery and config first, then the rest:

```text
eureka-server → config-server → gateway → survey-service → results-service → survey-ui
```

`./bbq-wednesday/start-all.sh` opens one terminal tab per service on macOS.

## Backing Services

From the **repo root**: `docker compose up -d` starts RabbitMQ (Stream), Redis (gateway rate
limiter), and Grafana LGTM (tracing). Config and Discovery need none of it — the JVM is enough
until the Resilience/Stream modules.
