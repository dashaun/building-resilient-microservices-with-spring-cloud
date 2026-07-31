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
| Spring Boot | `4.0.7` |
| Spring Cloud | `2025.1.2` (Oakwood) |
| Java | `21` |
| RabbitMQ | `4.x` (Spring Cloud Stream binder) |
| Redis | `7.x` (gateway rate limiter) |
| Grafana LGTM | latest (traces / metrics / logs) |

Spring Cloud tracks Spring Boot through named release trains; **Oakwood (2025.1.x)** is the
train for Spring Boot 4 / Spring Framework 7. Pin the train in the root `pom.xml` and let it
manage every `spring-cloud-*` version.

> **Boot 4 note:** `spring-boot-starter-aop` was removed. The Resilience4j annotations need
> AspectJ, so `survey-service` depends on `org.aspectj:aspectjweaver` directly (version
> managed by the Boot BOM).

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
        every service: registers with eureka-server, reads config-server, emits traces
```

## Running the Workshop Deck

The slides are a Reveal.js deck in [`docs/`](docs/).

```bash
jwebserver -d docs -p 8000     # JDK 21+ built-in static server
# open http://localhost:8000  — press S for speaker notes
```

Navigation: **Right/Left** moves between the eight modules, **Down/Up** within a module,
**Esc** for overview. Each exercise is immediately followed by its answer slide.

## Running the Code

```bash
# 1. backing services (needed from the Resilience/Stream modules onward)
docker compose up -d                 # RabbitMQ · Redis · Grafana LGTM

# 2. build everything
cd labs/bbq-wednesday
sdk env                              # JDK 21 (or ensure java -version is 21+)
./mvnw -q -DskipTests package

# 3. start the services (each in its own terminal), in this order:
#    eureka-server → config-server → gateway → survey-service → results-service → survey-ui
./start-all.sh                       # macOS: opens a tab per service
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
Workshop by [DaShaun Carter](https://dashaun.com).
