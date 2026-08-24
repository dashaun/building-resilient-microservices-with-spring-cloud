<!-- .slide: data-background-color="#6db33f" -->

## What We Built

```text
survey-ui ─▶ gateway ─▶ survey-service ─▶ results-service
             (auth,     (config, discovery,   (config, discovery,
              rate,      resilience, publish)   consume, tally)
              breaker)          │
                         [ RabbitMQ: bbq-votes ]
          eureka-server · config-server · Grafana LGTM (traces)
```

Six services. Six Spring Cloud capabilities. One BBQ argument.

Notes:
Minute 235-236.
Recap the whole board. Every arrow on this diagram was an exercise: config, discovery, resilience, messaging, gateway+security, tracing. The domain was fun; the architecture is exactly what production needs.

Likely questions
- Q: Is this production-ready as-is? A: The patterns are. The next slide is the checklist that separates a workshop from a deploy.

---

## The Six Answers

| The pain | The Spring Cloud answer |
|---|---|
| Where is that service? | **Eureka** discovery + LoadBalancer |
| Change config, no rebuild | **Config Server** + client, `/refresh` |
| A dependency failed | **Resilience4j** breaker · retry · fallback |
| Too much load | Rate limiter (service **and** gateway) |
| Services coupled in time | **Cloud Stream** + RabbitMQ events |
| One secure front door | **Gateway** + Spring Security (JWT) |
| Where did the time go? | **Micrometer Tracing** → Grafana |

Notes:
Minute 236-237.
This is the slide to photograph. It maps each recurring microservices pain to the specific tool we used. Adopt them one at a time, in roughly this order — each is independently valuable.

---

## Your Production Blueprint

```text
externalize config   →  git-backed Config Server + Vault for secrets
find by name         →  discovery (Eureka or your platform's)
assume failure       →  timeouts + breakers + fallbacks everywhere
decouple in time     →  events for anything that can be async
one guarded door     →  gateway: real IdP (jwk-set-uri), TLS, limits
see everything       →  traces + metrics + logs, sampled sanely
```

Start with **one** service pair and add capabilities as they hurt.

Notes:
Minute 237-238.
The honest guidance: don't adopt all of Spring Cloud on day one. Externalize config and add tracing early — they pay off immediately. Add discovery when you scale past one instance, resilience when a dependency first burns you, messaging when synchronous coupling hurts.

Likely questions
- Q: What did the lab simplify? A: Native (not git) config, an HMAC (not IdP) token, H2 in-memory stores, sampling at 100%. Each has a one-line production upgrade we named in its module.

---

## Before You Ship

- every remote call has a **timeout**, a **breaker**, and a **fallback**;
- config is **externalized**; secrets are in a **vault**, not a file;
- the gateway validates tokens against a **real IdP** (`jwk-set-uri`);
- rate limits are **distributed** (Redis), tuned per client;
- events use **durable consumer groups**; consider a transactional outbox;
- tracing is on, **sampled** sanely, with logs correlated by trace-id;
- Eureka / gateway run as **clusters**, not singletons.

Notes:
Minute 238-239.
The pre-flight checklist. Each line is something the lab hinted at and production demands. This is the difference between "it works on my laptop" and "it survives Black Friday."

Likely questions
- Q: Anything missing? A: Plenty — CI/CD, blue-green, capacity planning, chaos testing, security review. This is the Spring Cloud slice; the operational slice is its own workshop.

---

<!-- .slide: data-background-color="#c0392b" -->

## Stop Wiring Hosts

# Start Building Resilient Systems

DaShaun Carter | Spring Developer Advocate

[DaShaun.com](https://dashaun.com)

Spencer Gibb | Spring Cloud Cofounder

[gibb.tech](https://gibb.tech)

> Which capability will you add to YOUR system first — and what will it stop hurting?

Notes:
Minute 239-240.
End on the question and wait. The best close is a participant naming the one pain — config drift, a cascading failure, an unowned front door — that this workshop just gave them the tool to fix. Thank the room. Point to DaShaun.com for the code and slides.

Likely questions
- Q: Where do I go next? A: The Spring Cloud reference docs, this repo's README, and Spring Office Hours. Take BBQ Wednesday, swap in your domain, and keep the patterns.
- Q: Can I get burnt ends recommendations? A: That's the one question with no fallback. Come find me after.
