<!-- .slide: data-background-color="#6db33f" -->

# Building Resilient Microservices

### with Spring Cloud

DaShaun Carter | Spring Developer Advocate | [DaShaun.com](https://dashaun.com) <!-- .element: style="color: white" -->

Spencer Gibb | Spring Cloud Cofounder | [gibb.tech](https://gibb.tech) <!-- .element: style="color: white" -->

Notes:
Minute 0-1.
Good morning. I'm DaShaun Carter, a Spring Developer Advocate, and for the next four hours we are going to build a small fleet of microservices that actually survives production.

Pre-flight (run before going on stage)
- JDK 21+ active (`sdk env` in the repo, or `java -version` shows 21).
- `docker compose up -d` from the repo root — RabbitMQ, Redis, and Grafana LGTM.
- `cd labs/bbq-wednesday && ./mvnw verify` so every jar is cached.
- IDE open at labs/bbq-wednesday.

Running this deck
- `jwebserver -d "$PWD/docs" -p 8000` (JDK 21+ built-in static server), open http://localhost:8000.
- Press **S** for speaker view (notes + timer + next slide).

Reveal.js navigation
- **Right / Left** — jump between modules (Intro, Config, Discovery, Resilience, Stream, Gateway, Tracing, Wrap).
- **Down / Up** — move through the slides inside a module.
- **Esc** — overview; **F** fullscreen; **B** black screen.
- The two-dimensional layout means you can SKIP a module by pressing Right — the workshop still flows.

Likely questions
- Q: Do I need all the infrastructure running the whole time? A: No. The standalone Config and Eureka servers need only the JVM. The complete survey/results reference already includes Bus and Stream, so RabbitMQ is needed from setup; Redis is needed for gateway routing exercises.
- Q: Can I catch up if I fall behind? A: Every exercise is followed by an answer slide, and labs/bbq-wednesday is a complete, compiling reference.

---

## Now We Are Connected

### [DaShaun.com](https://dashaun.com)

### [gibb.tech](https://gibb.tech)

Slides, the code, upcoming events, and every social link live there.

Notes:
Minute 1-2.
Twenty seconds. This is the durable place to find the materials after today.

Likely questions
- Q: Where does the finished code live? A: labs/bbq-wednesday in this repo is the reference implementation; it compiles and runs as-is.

---

![Spring Office Hours](images/spring-office-hours-blank.png)

### [SpringOfficeHours.io](https://springofficehours.io)

Notes:
Minute 2-3.
Brief personal connection. Do not spend workshop time on the show — a sentence, then move on.

Likely questions
- Q: Is the show required preparation? A: No; it is an optional follow-up resource.

---

## What We're Building: BBQ Wednesday

A live audience survey about **Kansas City barbecue**.

- Burnt ends, sauce, sides — vote from your laptop at localhost:8080.
- Phone access needs a reachable host address on the same network.
- Every vote flows through the same production patterns real systems use.
- By lunch, the room is driving a distributed system we built together.

> The domain is fun on purpose. The architecture is dead serious.

Notes:
Minute 3-5.
Set the tone. The survey is a reskin of Ryan Baxter's spring-survey-app; the point is that a "toy" domain still needs discovery, config, resilience, messaging, a gateway, security, and tracing — exactly like the serious systems in the room.

Likely questions
- Q: Is this real Kansas City BBQ opinion? A: The questions are real. The burnt-ends debate is the only genuinely dangerous part of this workshop.

---

## Microservices: the good and the bill

**The good:** independent deploys, independent scaling, team autonomy, fault isolation.

**The bill you didn't expect:**

- Where is `results-service` right now? (**discovery**)
- How do I change a setting without a rebuild? (**config**)
- What happens when one service is down or slow? (**resilience**)
- How do services talk without being wired together? (**messaging**)
- One front door, one place for security & throttling? (**gateway**)
- A request touched six services — where did the time go? (**tracing**)

Notes:
Minute 5-8.
This slide IS the workshop outline. Each pain point is a module. Spring Cloud is not a framework you adopt wholesale; it is a toolbox where each tool answers one of these questions.

Likely questions
- Q: Do I need all of Spring Cloud? A: No. Adopt one capability at a time. That's exactly how we'll build it.
- Q: Is a monolith wrong? A: Not at all. These same patterns (config, resilience, tracing) help a modular monolith too. Distribution just makes them mandatory.

---

## The Target Architecture

<svg class="dg" viewBox="0 0 1000 510" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-arc" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker><marker id="a-e-arc" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker><marker id="a-w-arc" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#8fa0a0"/></marker></defs>
<rect class="band" x="60" y="406" width="880" height="100" rx="16"/>
<text class="lbl" x="500" y="427">every service above registers here and loads its config at startup</text>
<rect class="n-infra" x="90" y="438" width="300" height="60" rx="12"/>
<text class="t-sm" x="240" y="464" style="font-size:19px;fill:#4a5a5a">eureka-server</text>
<text class="sub" x="240" y="485">:8761 &#183; service registry</text>
<rect class="n-infra" x="610" y="438" width="300" height="60" rx="12"/>
<text class="t-sm" x="760" y="464" style="font-size:19px;fill:#4a5a5a">config-server</text>
<text class="sub" x="760" y="485">:8888 &#183; centralized config</text>
<path class="weak" d="M500,384 V400" style="stroke-width:2;stroke-dasharray:5 5" marker-end="url(#a-w-arc)"/>
<rect class="n-plain" x="370" y="4" width="260" height="58" rx="12"/>
<text class="t" x="500" y="30">survey-ui</text>
<text class="sub" x="500" y="51">browser &#183; Chart.js</text>
<path class="flow" d="M500,62 V96" marker-end="url(#a-g-arc)"/>
<rect class="n-fill" x="310" y="106" width="380" height="72" rx="12"/>
<text class="t-on" x="500" y="137">gateway</text>
<text class="sub-on" x="500" y="161">:8080 &#183; security &#183; rate limit &#183; routing</text>
<path class="flow" d="M500,178 V196 Q500,206 490,206 H185 Q175,206 175,216 V228" marker-end="url(#a-g-arc)"/>
<path class="flow" d="M500,178 V196 Q500,206 510,206 H815 Q825,206 825,216 V228" marker-end="url(#a-g-arc)"/>
<rect class="n-app" x="40" y="236" width="270" height="86" rx="12"/>
<text class="t" x="175" y="266">survey-service</text>
<text class="port" x="175" y="288">:8081 &#183; :8082</text>
<text class="sub" x="175" y="309">accepts votes &#183; publishes events</text>
<rect class="n-app" x="690" y="236" width="270" height="86" rx="12"/>
<text class="t" x="825" y="266">results-service</text>
<text class="port" x="825" y="288">:8083</text>
<text class="sub" x="825" y="309">consumes events &#183; live tally</text>
<path class="async" d="M316,279 H387" marker-end="url(#a-e-arc)"/>
<path class="async" d="M609,279 H682" marker-end="url(#a-e-arc)"/>
<rect class="n-msg" x="395" y="252" width="210" height="54" rx="27"/>
<text class="t-sm" x="500" y="276" style="fill:#c0392b">RabbitMQ</text>
<text class="mono" x="500" y="295" style="fill:#b45a4d">bbq-votes</text>
<text class="lbl" x="500" y="360">HTTP tally read &#183; the Resilience4j lab</text>
<path class="weak" d="M175,322 V364 Q175,374 185,374 H815 Q825,374 825,364 V332" marker-end="url(#a-w-arc)"/>
</svg>

One public door. Two services that talk twice — once by event, once by HTTP.

Notes:
Minute 8-12.
Walk the diagram once. survey-service accepts votes and PUBLISHES events; results-service CONSUMES them and aggregates; the gateway is the only public door; eureka and config-server are the plumbing every service depends on. Traces (not drawn) thread through all of it.

Likely questions
- Q: Why two survey-service instances? A: To show client-side load balancing and discovery doing real work.
- Q: Why both an event AND an HTTP call between the two services? A: Deliberately. The event drives the tally (async, resilient by nature); the HTTP tally read is our lab subject for Resilience4j.

---

## The Version Contract

| Technology | Version | Role |
|---|---:|---|
| Spring Boot | `4.1.1` | application runtime |
| Spring Cloud | `2025.1.2` | Oakwood — the Boot 4 train |
| Java | `21` | language level |
| RabbitMQ | `4.x` | Spring Cloud Stream binder |
| Redis | `7.x` | gateway rate limiter |
| Grafana LGTM | latest | traces, metrics, logs |

The versions are fixed. The BBQ opinions are yours.

Notes:
Minute 12-14.
Spring Cloud tracks Spring Boot through named release trains. "Oakwood" (2025.1.x) is the train built for Spring Boot 4 and Spring Framework 7. Pin the train, let it manage every `spring-cloud-*` version — never set those by hand.

Likely questions
- Q: Why not the newest Boot patch? A: 2025.1.2 supports Boot 4.0.x and 4.1.x; we pin a known-good pair rather than chase patches during a workshop.
- Q: Where do I set the train? A: `spring-cloud.version` property + the `spring-cloud-dependencies` BOM import in the root pom.

---

## The Repository

```text
building-resilient-microservices-with-spring-cloud/
├── docker-compose.yml         # RabbitMQ · Redis · Grafana LGTM
├── docs/                      # this deck
└── labs/bbq-wednesday/
    ├── config-repo/           # externalized config (served by config-server)
    ├── eureka-server/         # :8761  service registry
    ├── config-server/         # :8888  centralized configuration
    ├── gateway/               # :8080  routing · security · rate limiting
    ├── survey-service/        # :8081  questions · votes · publishes events
    ├── results-service/       # :8083  consumes events · live tally
    └── survey-ui/             # :8091  Chart.js front end
```

Notes:
Minute 14-16.
One multi-module Maven build. Each module is a real Spring Boot app. We add one Spring Cloud capability per module of the workshop, and the finished tree is exactly what's on screen.

Likely questions
- Q: Is config-repo a separate git repo? A: In production, yes. Here it's a folder the Config Server reads in "native" mode so the lab stays offline. We'll show the git-backed version too.

---

## Lab Setup — 6 Minutes

```bash
# from the repo root
docker compose up -d           # rabbit, redis, lgtm (needed later)

cd labs/bbq-wednesday
sdk env                        # JDK 21 (or ensure java -version is 21+)
./mvnw verify  # build all six services
```

You should see `BUILD SUCCESS` (six services plus the parent project).

Notes:
Minute 16-20.
Kick this off now; Maven can resolve while we start the Config module. Docker images pull in the background. Pair up if someone's build is unhappy.

Likely questions
- Q: package fails on JDK 17? A: This build targets Java 21. `sdk env` selects it from .sdkmanrc, or install any JDK 21+.
- Q: Do I need Docker right now? A: Start it now: the reference already includes Bus and Stream even while we teach Config. Only the standalone Config/Eureka servers run without it.
- Q: Windows? A: Use `mvnw.cmd`. Everything else is identical.
