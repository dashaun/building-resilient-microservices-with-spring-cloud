# Workshop validation — 2026-09-07

The six-service reference builds on Java 21 with Spring Boot 4.1.1 and Spring Cloud
2025.1.2. The corrected walkthrough and live application passed the checks below.
Versions were preserved.

## Verified

| Area | Result |
|---|---|
| Maven reactor | All six services build; five regression tests pass |
| Configuration | Config Server serves questions; single-instance refresh changes only that instance; Bus refresh reaches the second instance |
| Discovery | Two survey instances register; gateway and declarative HTTP client resolve service IDs |
| Resilience | Real HTTP timeouts retry three times; paused results service falls back in about 1.96 s; breaker opens and recovers through half-open to closed |
| Messaging | Votes reach RabbitMQ and update results; two votes queued during consumer downtime are consumed after restart |
| Security | Missing and invalid JWTs return 401; valid JWT permits a vote |
| Rate limiting | Concurrent requests produce edge 429s with exhausted Redis rate-limit headers |
| Tracing | A fresh trace ID includes gateway, survey-service, and RabbitMQ consumption in results-service, retrieved from Tempo |
| Browser | Vote submission, live charts, zero totals after H2 reset, and unavailable/recovery status exercised |
| Deck | 83 slides load; every slide has timing and Q&A notes; exercise/answer adjacency and Bash syntax checked; navigation and representative rendering inspected |
| Launcher | Shell syntax checked; readiness checks replace fixed startup sleeps; individual services launched successfully |

## Corrections

- Use Boot 4's OpenTelemetry starter and OTLP property namespace; preserve Boot's
  instrumentation when customizing RestClient builders.
- Set 500 ms connect/read timeouts on the declarative tally client. Disable the
  automatic HTTP-group breaker so the explicit retry/breaker policy receives the
  actual network exceptions. Set aspect ordering explicitly.
- Limit the vote-throttling fallback to `RequestNotPermitted`; unrelated failures
  must not masquerade as 429. Check StreamBridge's publication result.
- Disable Eureka client recreation during config refresh: the pinned train produced
  a null-instance-ID failure and HTTP 500. Question refresh still works; registry
  settings require a restart.
- Add bounded gateway HTTP calls and fallbacks for results and UI; use explicit routes.
- Renew the dev token before each vote; support voter IDs on HTTP LAN addresses.
  Clear charts when counts reset, retain zero-vote options, and show polling failures.
- Bundle Chart.js 4.5.1 and its license locally for offline charts.
- Fix the discovery exercise's invalid environment-assignment syntax, start its
  gateway dependency explicitly, add the missing gateway answer, and correct the
  `jwebserver` command to use an absolute directory.
- Correct claims about persistent config caching, live resilience retuning, H2
  restart behavior, replica storage, automatic log export, and infrastructure needs.

## Reproduce

From the repository root, start `docker compose up -d --wait`. Then build with
`cd labs/bbq-wednesday && ./mvnw verify` and launch with `./start-all.sh` on macOS,
or run each module in its own terminal as described in the README. Choose the
launcher or the module-by-module walkthrough; do not launch duplicate instances.

After startup, run `python3 scripts/smoke-test.py` from the repository root.
It checks the running system and adds test votes. The Maven tests include an actual
HTTP server that times out twice before succeeding, plus breaker/retry/fallback checks.

## Scope and environment

Validation used a workspace copy, then copied the verified source changes back to
this repository. The machine's configured Maven mirror at `juice:8081` was unreachable;
a temporary empty Maven settings file selected Maven Central without changing user settings.
Existing Redis/Grafana containers occupied the default ports, so test containers used
16379 (Redis), 13000 (Grafana), and 14318 (OTLP). Existing project containers were not modified.

The macOS Terminal launcher itself was syntax-checked, not exercised through Terminal
windows; services were launched as managed Java processes for the live checks. Every
slide was checked structurally, not pixel-by-pixel at every screen size. Encryption
and OAuth2 login/SSO remain concept-only as intended. The lab still uses H2, shared-secret
dev tokens, and separate database-save/message-publish operations; publisher confirms,
idempotency, persistent shared storage, and outbox handling remain production upgrades.
A harmless native macOS Netty DNS fallback warning appeared during gateway startup.

## Framework references

- [Spring Cloud supported Boot versions](https://spring.io/projects/spring-cloud/)
- [Boot 4 tracing starter, export settings, and client instrumentation](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Cloud HTTP service group load balancing](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)
