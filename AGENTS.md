# AGENTS.md

## Purpose

A 4-hour, hands-on workshop that teaches Spring Cloud by building **BBQ Wednesday**, a live
Kansas City barbecue survey. Optimize for a runnable workshop, teaching clarity, and quick
participant recovery — not production breadth. Each module of the deck adds exactly one
Spring Cloud capability, and each exercise is immediately followed by an answer slide.

## Version Contract

- Spring Boot `4.1.1`
- Spring Cloud `2025.1.2` (Oakwood release train)
- Java `21`
- RabbitMQ 4.x · Redis 7.x · Grafana LGTM

Do not silently change these. Spring Cloud versions come from the `spring-cloud-dependencies`
BOM — never pin individual `spring-cloud-*` artifacts.

**Boot 4 gotcha:** `spring-boot-starter-aop` no longer exists. Resilience4j annotations
require AspectJ via `org.aspectj:aspectjweaver` (Boot-managed version). Keep it.

## Structure

- `docs/` — Reveal.js deck. One `.md` per module (horizontal column); vertical slides via
  `---`. Separators: `^\f` horizontal, `^---$` vertical, `^Notes:` speaker notes. Modules, in
  order: `intro`, `config`, `discovery`, `resilience`, `stream`, `gateway`, `tracing`, `outro`.
- `labs/bbq-wednesday/` — one multi-module Maven build; the complete reference implementation.
- `labs/bbq-wednesday/config-repo/` — externalized configuration served by the Config Server
  in `native` mode.
- `docker-compose.yml` — RabbitMQ, Redis, Grafana LGTM.

## Commands

```bash
# deck
jwebserver -d docs -p 8000

# code
cd labs/bbq-wednesday
./mvnw -q -DskipTests package
./mvnw -pl survey-service spring-boot:run     # or per module

# infra
docker compose up -d
```

## Architecture

```text
survey-ui → gateway → survey-service → results-service
                            │ publishes SurveyVoteEvent → RabbitMQ "bbq-votes"
                            └ reads tally over HTTP (Resilience4j breaker/retry/fallback)
eureka-server (discovery) · config-server (config) · Grafana LGTM (tracing)
```

Packages are under `com.bbqwednesday.*`. The vote event
(`SurveyVoteEvent(questionId, answer, timestamp, voterId)`) is duplicated in the producer and
consumer on purpose — they are independently deployable services, not a shared library.

The survey→results call is a **declarative** `@HttpExchange` client (`TallyClient`, built via
`HttpServiceProxyFactory`/`RestClientAdapter` in `ClientConfig`); `ResultsClient` wraps it with
Resilience4j. Config clients also carry `spring-cloud-starter-bus-amqp` so `/actuator/busrefresh`
fans a refresh across instances over RabbitMQ. Config-encryption (`{cipher}`) and gateway
OAuth2 login/SSO are taught as **concept slides only** — not wired into the lab (SSO needs a live
IdP; encryption needs a runtime `ENCRYPT_KEY`), so the build stays offline. Keep them concept-only
unless the workshop gains a running IdP.

## Teaching Model & Modification Rules

- Keep the deck and the lab code **synchronized** — slide "answer" code must match the
  compiling source in `labs/bbq-wednesday`.
- Keep answer slides **immediately after** their exercise.
- Preserve DaShaun's direct, Spring-developer voice and the Spring green (`#6db33f`) /
  dark (`#191e1e`) / BBQ ember (`#c0392b`) visual system.
- Speaker notes carry a `Minute X-Y` marker and a `Likely questions` Q/A block.
- The lab is the **complete reference** (it runs); the slides drive participants through
  building each capability. Do not gut the reference into a broken starter.
- Lab simplifications and their production upgrade are named in each module: native (not git)
  config; HS256 shared-secret (not IdP `jwk-set-uri`) tokens; H2 in-memory stores; 100%
  trace sampling. Keep those honest.
- Never hardcode a host where a Eureka service id (`lb://`, `http://<service-id>`) belongs.
- Every remote call in the lab keeps a timeout + circuit breaker + fallback.
