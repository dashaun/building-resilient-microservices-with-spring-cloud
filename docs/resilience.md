<!-- .slide: data-background-color="#c0392b" -->

# Module 3

## Fault Tolerance & Circuit Breakers

Resilience4j · failover · throttling · ~40 min

Notes:
Minute 90 (after the break).
This is the heart of "resilient." Discovery found the instances; now one of them gets slow and unreliable and we keep the system standing. Make sure eureka-server, config-server, survey-service, and results-service are running.

Likely questions
- Q: What must be running? A: Config, Eureka, both survey instances, results-service, and RabbitMQ.

---

## The Problem: One Slow Dependency

<svg class="dg" viewBox="0 0 1000 236" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-res" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker><marker id="a-e-res" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker></defs>
<rect class="n-app" x="80" y="30" width="300" height="96" rx="12"/>
<text class="t" x="230" y="70">survey-service</text>
<text class="sub" x="230" y="96">healthy &#8212; for now</text>
<rect class="n-bad" x="620" y="30" width="300" height="96" rx="12"/>
<text class="t" x="770" y="70">results-service</text>
<text class="lbl-e" x="770" y="96">slow / down</text>
<text class="mono" x="497" y="58">HTTP GET /tally</text>
<path class="flow" d="M386,72 H608" marker-end="url(#a-g-res)"/>
<text class="lbl-e" x="497" y="94">no timeout &#8212; the thread waits</text>
<path class="async" d="M230,126 V158" marker-end="url(#a-e-res)"/>
<rect class="n-msg" x="115" y="166" width="230" height="52" rx="10"/>
<text class="t-sm" x="230" y="198" style="fill:#c0392b">threads block</text>
<path class="async" d="M351,192 H379" marker-end="url(#a-e-res)"/>
<rect class="n-msg" x="385" y="166" width="230" height="52" rx="10"/>
<text class="t-sm" x="500" y="198" style="fill:#c0392b">pool exhausts</text>
<path class="async" d="M621,192 H649" marker-end="url(#a-e-res)"/>
<rect class="n-msg" x="655" y="166" width="230" height="52" rx="10"/>
<text class="t-sm" x="770" y="198" style="fill:#c0392b">survey-service dies</text>
</svg>

- A call with no timeout blocks a thread.
- Enough blocked threads → the **caller** falls over.
- Now survey-service is down because results-service was slow.

> Failure isn't the problem. **Cascading** failure is.

Notes:
Minute 90-93.
The classic outage: a downstream slowness climbs the call graph and takes out healthy services. The fix isn't "never fail" — it's to contain failure so a sick dependency can't drown its callers.

Likely questions
- Q: Won't a timeout alone fix it? A: It's necessary but not sufficient. Under load you also need to stop calling a known-bad dependency (circuit breaker) and to degrade gracefully (fallback).

---

## Four Patterns, One Library

| Pattern | Question it answers |
|---|---|
| **Retry** | Was that just a transient blip? |
| **Circuit Breaker** | Is this dependency broken right now? |
| **Fallback** | What do I return when it is? |
| **Rate Limiter** | Am I being asked to do too much? |

Resilience4j provides all four as **annotations**. We use every one today.

Notes:
Minute 93-96.
Resilience4j is the successor to Hystrix — lightweight, functional, no thread-pool baggage by default. Retry handles blips; the breaker handles sustained failure; fallback handles the user experience; the rate limiter protects the service itself. There's also TimeLimiter and Bulkhead — same model.

Likely questions
- Q: Order of operations? A: The lab explicitly sets `circuit-breaker-aspect-order: 1` and `retry-aspect-order: 2`, so retry sits inside the breaker: a call is retried, and only sustained failure across calls trips the breaker. Get the ordering wrong and retries hide the very failures the breaker needs to see.

---

## The Circuit Breaker's Three States

<svg class="dg" viewBox="0 0 1000 420" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-cb" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker><marker id="a-e-cb" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker><marker id="a-w-cb" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#8fa0a0"/></marker></defs>
<rect class="n-app" x="60" y="55" width="280" height="112" rx="14"/>
<text class="t-lg" x="200" y="106" style="fill:#59942f">CLOSED</text>
<text class="sub" x="200" y="134">calls flow &#183; failures counted</text>
<rect class="n-msg" x="660" y="55" width="280" height="112" rx="14"/>
<text class="t-lg" x="800" y="106" style="fill:#c0392b">OPEN</text>
<text class="sub" x="800" y="134">fail fast &#183; return the fallback</text>
<rect class="n-warn" x="360" y="268" width="280" height="112" rx="14"/>
<text class="t-lg" x="500" y="319" style="fill:#b3701f">HALF-OPEN</text>
<text class="sub" x="500" y="347">a few trial calls get through</text>
<text class="lbl-e" x="496" y="96">failure rate exceeds threshold</text>
<path class="async" d="M346,111 H646" marker-end="url(#a-e-cb)"/>
<path class="weak" d="M800,167 V296 Q800,324 772,324 H658" marker-end="url(#a-w-cb)"/>
<text class="lbl start" x="818" y="216">wait-duration</text>
<text class="lbl start" x="818" y="234">elapses</text>
<path class="flow" d="M354,324 H228 Q200,324 200,296 V179" marker-end="url(#a-g-cb)"/>
<text class="lbl-g end" x="182" y="216">trial calls</text>
<text class="lbl-g end" x="182" y="234">succeed</text>
<text class="lbl-e" x="500" y="407">a trial call fails &#8594; straight back to OPEN</text>
</svg>

- **Closed** — calls flow, failures counted.
- **Open** — stop calling; return the fallback immediately.
- **Half-open** — probe with a few calls; recover or re-open.

Notes:
Minute 96-99.
Draw the loop. Open state is the key insight: when a dependency is down, the kindest thing you can do is STOP calling it — that gives it room to recover and makes your own responses fast (fail fast, not fail slow). Half-open is the automatic recovery probe.

Likely questions
- Q: What counts as "failure"? A: Exceptions and, if configured, slow calls. Both the failure-rate and slow-call-rate thresholds can trip the breaker.

---

## Resilience4j on Spring Boot 4

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
<dependency>                          <!-- annotations need AspectJ -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

⚠️ `spring-boot-starter-aop` was **replaced** in Boot 4 — depend on
`spring-boot-starter-aspectj` instead.

Notes:
Minute 99-101.
A real Boot 4 gotcha worth calling out: the AOP starter is gone. The Resilience4j annotations are Spring AOP aspects, so AspectJ must be on the classpath — add `spring-boot-starter-aspectj`, unversioned, and let Boot manage it. This bit me building the lab; it'll bite them too.

Likely questions
- Q: Which Resilience4j version? A: Let the Spring Cloud BOM manage the circuit-breaker starter and its transitive Resilience4j dependencies; do not invent a Boot-4-named artifact.

---

## Making the Call Resilient

`survey-service` → `results-service`, all three patterns wrapped around
the declarative `TallyClient` from Module 2:

```java
@Component
public class ResultsClient {

    private final TallyClient tallyClient;      // @HttpExchange interface

    @CircuitBreaker(name = "resultsService", fallbackMethod = "emptyTally")
    @Retry(name = "resultsService")
    public TallyView currentTally(String questionId) {
        return tallyClient.tally(questionId);   // discovery + LB under the hood
    }

    // same params + the Throwable that tripped the fallback
    TallyView emptyTally(String questionId, Throwable t) {
        log.warn("results-service down ({}); serving empty tally", t.toString());
        return TallyView.empty(questionId);
    }
}
```

Notes:
Minute 101-105.
Clean separation of concerns: the declarative `TallyClient` owns the HTTP + discovery; `ResultsClient` owns the resilience policy. `@Retry` gives transient errors a few fast attempts; `@CircuitBreaker` opens after sustained failure and short-circuits to `emptyTally`. The fallback signature is the original parameters plus a `Throwable`. The HTTP service group sets 500 ms connect/read timeouts. The lab sets `spring.cloud.circuitbreaker.http-services.enabled: false` so `ResultsClient` owns the policy without an automatic inner wrapper; three attempts plus two 200 ms waits keep failures bounded. The user sees an empty tally, never a 500 — degrade, don't die.

Likely questions
- Q: Does the fallback swallow real bugs? A: It catches whatever you let it. Scope fallbacks narrowly and log the cause (we do). You can also list specific `retry-exceptions`/`ignore-exceptions`.
- Q: Reactive version? A: The gateway uses the reactive Resilience4j; the annotation model here is for imperative code.

---

## Tuning Lives in Config (Not Code)

`config-repo/survey-service.yml` — delivered by the Config Server:

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1
    instances:
      resultsService:
        failure-rate-threshold: 50        # % failures to open
        sliding-window-size: 10
        wait-duration-in-open-state: 10s
        minimum-number-of-calls: 5
  retry:
    retry-aspect-order: 2
    instances:
      resultsService:
        max-attempts: 3
        wait-duration: 200ms
```

Change thresholds centrally, then **restart clients** to rebuild the registries.
Question changes still support `/actuator/refresh`.

Notes:
Minute 105-107.
The behavior is annotations; the numbers are configuration — and because they live in config-repo, Module 1 lets you retune resilience without rebuilding the jar; existing Resilience4j registries require a client restart. That's the composition payoff: the person tuning the breaker at 2am is not the person who has to cut a release.

Likely questions
- Q: `name = "resultsService"` links what? A: The annotation's name selects this `instances.resultsService` config block. Multiple call sites can share or differ.

---

## Exercise 3a — Trip the Breaker (12 min)

Make results-service fail and watch survey-service shrug:

```bash
# 1. results-service is running; call the resilient path — works:
curl localhost:8081/tally/burnt-ends            # {answerCounts:{...}}

# 2. KILL results-service (Ctrl-C its terminal), then call again:
curl localhost:8081/tally/burnt-ends            # {"questionId":"burnt-ends","answerCounts":{},"totalResponses":0}

# 3. spam it, then inspect the breaker:
for i in $(seq 1 10); do curl -s localhost:8081/tally/burnt-ends >/dev/null; done
curl localhost:8081/actuator/circuitbreakers
```

Notes:
Minute 107-119.
The demo everyone remembers: kill a dependency and the caller keeps answering. First calls retry then fall back (a beat of latency); after `minimum-number-of-calls` failures cross the threshold, the breaker OPENS and fallbacks become instant. The actuator endpoint shows state = OPEN. Restart results-service and watch it go HALF_OPEN → CLOSED.

Likely questions
- Q: First few calls are slow, then fast? A: Exactly — retries add latency while CLOSED; once OPEN it fails fast. That latency drop is the breaker doing its job.
- Q: Still 200 OK with results-service dead? A: Yes. Fallback returns a valid empty tally. That's graceful degradation.

---

## Answer 3a — Fail Fast, Stay Up

```json
{ "circuitBreakers": {
    "resultsService": {
      "state": "OPEN",
      "failureRate": "100.0%",
      "bufferedCalls": 5,
      "failedCalls": 5 } } }
```

- survey-service returned **200** the entire time.
- With the breaker OPEN, results-service got **zero** traffic to recover in peace.
- Restart it → `HALF_OPEN` → `CLOSED`, automatically.

Notes:
Minute 119-121.
Recap the three wins: the caller stayed up, the user got a sane response, and the sick service got breathing room. No human paged, no cascade. Then pivot to protecting the service from the other direction — too much load.

Likely questions
- Q: Why five buffered failures? A: minimum-number-of-calls is five; later requests are rejected while OPEN.

---

## Throttling: Rate Limiter

Same library, opposite direction — cap the work accepted:

```java
@RateLimiter(name = "submitVote", fallbackMethod = "tooManyVotes")
@PostMapping("/submit")
public ResponseEntity<SurveyVote> submit(@RequestBody SurveyVote vote) {
    return ResponseEntity.ok(surveyService.recordVote(vote));
}

ResponseEntity<SurveyVote> tooManyVotes(SurveyVote vote, RequestNotPermitted t) {
    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Slow down — BBQ Wednesday is popular");
}
```

```yaml
resilience4j.ratelimiter.instances.submitVote:
  limit-for-period: 5          # 5 votes …
  limit-refresh-period: 1s     # … per second
  timeout-duration: 0          # over that? fail fast (429)
```

Notes:
Minute 121-124.
The breaker protects you from a bad dependency; the rate limiter protects you from too many callers. Ballot-stuffing at BBQ Wednesday is the friendly example; payment endpoints and expensive queries are the serious ones. Note this is a per-instance limit — the gateway module adds a distributed, Redis-backed limiter at the edge.

Likely questions
- Q: Rate limit here or at the gateway? A: Both, for different reasons. The gateway throttles per-client at the edge; this protects the service even from internal callers. Defense in depth.

---

## Exercise 3b — Hit the Limit (5 min)

Vote faster than the smoker can handle:

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " \
    -X POST localhost:8081/submit \
    -H 'Content-Type: application/json' \
    -d '{"questionId":"burnt-ends","answer":"Q39","voterId":"loadtest"}'
done ; echo
```

Expect the first few `200`s, then `429`s as the bucket drains.

Notes:
Minute 124-127.
Twelve rapid votes against a 5-per-second bucket: roughly five succeed, the rest get 429 Too Many Requests from the fallback. This is Resilience4j protecting survey-service's own capacity. Warm up with one vote first, then wait a second: the first publish creates its binding and can be slower. The fallback catches only `RequestNotPermitted`; database/broker failures must not be mislabeled as throttling.

Likely questions
- Q: All 200? A: Your loop is slower than 5/sec — tighten it or lower `limit-for-period` to 2 to see it clearly.

---

## Answer 3b + Module 3 Checkpoint

```text
200 200 200 200 200 429 429 429 429 429 429 429
```

You can now:

- retry **transient** failures and **circuit-break** sustained ones;
- **degrade gracefully** with a typed fallback (200, not 500);
- keep a sick dependency from **cascading**;
- **throttle** load with a rate limiter;
- **tune thresholds** from Config Server — restart clients, no rebuild.

**Next: services shouldn't have to call each other synchronously at all.**

Notes:
Minute 127-130.
Land it: resilience is annotations for behavior + config for numbers, composed with the earlier modules. The teaser for Stream: the tally itself is kept fresh by EVENTS, not by synchronous calls — the most resilient call is the one you don't make.

Likely questions
- Q: Why does the sequence vary? A: The limiter resets each second and request timing varies.
