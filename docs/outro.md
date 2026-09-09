<!-- .slide: data-background-color="#6db33f" -->

## What We Built

<svg class="dg inv" viewBox="0 0 1000 340" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-wh-out" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#ffffff"/></marker><marker id="a-we-out" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#ffe2da"/></marker></defs>
<rect class="n-plain" x="15" y="40" width="210" height="96" rx="12"/>
<text class="t-sm" x="120" y="80">survey-ui</text>
<text class="sub" x="120" y="104">Chart.js in the browser</text>
<path class="flow" d="M231,88 H262" marker-end="url(#a-wh-out)"/>
<rect class="n-app" x="268" y="40" width="210" height="96" rx="12"/>
<text class="t-sm" x="373" y="73">gateway</text>
<text class="sub" x="373" y="97">auth &#183; rate limit</text>
<text class="sub" x="373" y="117">edge breaker</text>
<path class="flow" d="M484,88 H515" marker-end="url(#a-wh-out)"/>
<rect class="n-app" x="521" y="40" width="210" height="96" rx="12"/>
<text class="t-sm" x="626" y="73">survey-service</text>
<text class="sub" x="626" y="97">config &#183; discovery</text>
<text class="sub" x="626" y="117">resilience &#183; publish</text>
<path class="flow" d="M737,88 H768" marker-end="url(#a-wh-out)"/>
<rect class="n-app" x="774" y="40" width="210" height="96" rx="12"/>
<text class="t-sm" x="879" y="73">results-service</text>
<text class="sub" x="879" y="97">config &#183; discovery</text>
<text class="sub" x="879" y="117">consume &#183; tally</text>
<path class="async" d="M626,136 V200 Q626,210 636,210 H653" marker-end="url(#a-we-out)"/>
<rect class="n-msg" x="665" y="186" width="170" height="48" rx="24"/>
<text class="t-sm" x="750" y="207" style="font-size:15px">RabbitMQ</text>
<text class="mono" x="750" y="226">bbq-votes</text>
<path class="async" d="M835,210 H869 Q879,210 879,200 V150" marker-end="url(#a-we-out)"/>
<rect class="band" x="15" y="256" width="969" height="70" rx="16"/>
<text class="lbl" x="500" y="281">the plumbing under all of it</text>
<text class="t-sm" x="180" y="309" style="font-size:15px">eureka-server :8761</text>
<text class="t-sm" x="500" y="309" style="font-size:15px">config-server :8888</text>
<text class="t-sm" x="820" y="309" style="font-size:15px">Grafana LGTM &#183; traces</text>
</svg>

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

Likely questions
- Q: Where are the checks? A: Run Maven verify, then scripts/smoke-test.py against the running stack.

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
