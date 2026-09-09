# Building Resilient Microservices with Spring Cloud

A hands-on, **4-hour** workshop. You build **BBQ Wednesday** — a live audience survey
about Kansas City barbecue — and along the way add every Spring Cloud capability a
production microservices system needs: centralized configuration, service discovery,
fault tolerance, message-driven services, an API gateway with security, and distributed
tracing.

The domain is a reskin of Ryan Baxter's excellent
[`spring-survey-app`](https://github.com/ryanjbaxter/spring-survey-app). The architecture
is the real thing.

## Version Contract

| Technology | Version |
|---|---:|
| Spring Boot | `4.1.1` |
| Spring Cloud | `2025.1.2` (Oakwood) |
| Java | `21` |
| RabbitMQ | `4.x` (Spring Cloud Stream binder) |
| Redis | `7.x` (gateway rate limiter) |
| Grafana LGTM | latest (traces / metrics / logs) |

Spring Cloud tracks Spring Boot through named release trains; **Oakwood (2025.1.x)** is the
train for Spring Boot 4 / Spring Framework 7. Pin the train in the root `pom.xml` and let it
manage every `spring-cloud-*` version.

> **Boot 4 note:** `spring-boot-starter-aop` was removed. The Resilience4j annotations need
> AspectJ, so `survey-service` uses the Boot-managed `spring-boot-starter-aspectj`.

## The System

| Service | Port | Role |
|---|---:|---|
| `eureka-server` | 8761 | Service discovery registry |
| `config-server` | 8888 | Centralized configuration (native backend → `config-repo/`) |
| `gateway` | 8080 | Front door: routing, JWT security, rate limiting |
| `survey-service` | 8081 / 8082 | Serves questions, accepts votes, **publishes** vote events |
| `results-service` | 8083 | **Consumes** vote events, serves the live tally |
| `survey-ui` | 8091 | HTML/JS + Chart.js, served through the gateway |

```text
survey-ui ─▶ gateway ─▶ survey-service ──"bbq-votes" (RabbitMQ)──▶ results-service
                             │  ◀── HTTP tally read (Resilience4j) ──┘
        config clients: survey-service + results-service; traces: gateway + both services
```

## Running the Workshop Deck

The slides are a Reveal.js deck in [`docs/`](docs/).

```bash
jwebserver -d "$PWD/docs" -p 8000     # JDK 21+ built-in static server
# open http://localhost:8000  — press S for speaker notes
```

Navigation: **Right/Left** moves between the eight modules, **Down/Up** within a module,
**Esc** for overview. Each exercise is immediately followed by its answer slide.

## Running the Code

```bash
# 1. backing services (the complete reference includes Bus/Stream from startup)
docker compose up -d --wait          # RabbitMQ · Redis · Grafana LGTM

# 2. build everything
cd labs/bbq-wednesday
sdk env                              # JDK 21 (or ensure java -version is 21+)
./mvnw verify

# 3. start the services (each in its own terminal), in this order:
#    eureka-server → config-server → gateway → survey-service → results-service → survey-ui
./start-all.sh                       # macOS: opens a terminal per service
```

Then open:

- **http://localhost:8080** — BBQ Wednesday (vote + live results)
- **http://localhost:8761** — Eureka dashboard
- **http://localhost:15672** — RabbitMQ management (guest/guest)
- **http://localhost:3000** — Grafana (Explore → Tempo for traces)

## Schedule (4 hours, two 15-min breaks)

| Time | Module |
|---:|---|
| 0:00–0:20 | Intro — the problem, the architecture, setup |
| 0:20–0:50 | **Config** — Spring Cloud Config Server & Client |
| 0:50–1:15 | **Discovery** — Netflix Eureka + LoadBalancer |
| 1:15–1:30 | ☕ Break |
| 1:30–2:10 | **Resilience** — Resilience4j: breaker, retry, fallback, rate limiter |
| 2:10–2:40 | **Stream** — Spring Cloud Stream + RabbitMQ |
| 2:40–2:55 | ☕ Break |
| 2:55–3:35 | **Gateway & Security** — Spring Cloud Gateway + Spring Security (JWT) |
| 3:35–3:55 | **Tracing** — Micrometer Tracing → Grafana |
| 3:55–4:00 | Wrap — production blueprint |

## Credits

Domain adapted from [ryanjbaxter/spring-survey-app](https://github.com/ryanjbaxter/spring-survey-app).

## Verification and recovery

Run `./mvnw verify` from `labs/bbq-wednesday` for the regression tests.
After starting all services and allowing Eureka caches to settle, run
`python3 scripts/smoke-test.py` from the repository root. It adds test votes and
checks config, discovery, JWT security, broker delivery, tally reads, and edge limiting.

The initial Maven build and Docker image pull require internet. Chart.js is bundled
locally; the deck's optional Google Fonts fall back to installed fonts when offline.
The shell examples use Bash; Windows users can use Git Bash plus `mvnw.cmd`.

- Port already allocated: inspect `docker ps` and local listeners before starting;
  another project's Redis or Grafana may own the workshop ports.
- Empty questions: wait for `:8888/survey-service/default`, then restart survey-service.
  `optional:configserver:` does not persist a last-known-good cache across restarts.
- Initial gateway 503: allow up to a minute for Eureka's registry caches to settle.
- Refresh: questions rebind live; resilience thresholds and Eureka settings require
  a client restart. Eureka refresh is disabled to keep registration stable.
- Results restart: H2 loses consumed tallies. The durable RabbitMQ queue retains
  unconsumed votes; use persistent shared storage and idempotency for production.
- Production messaging also needs publisher confirms/outbox handling; saving a vote
  and publishing its event are separate operations in this teaching reference.
