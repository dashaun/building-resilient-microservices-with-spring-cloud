<!-- .slide: data-background-color="#191e1e" -->

# Module 4

## Message-Driven Services

Spring Cloud Stream + RabbitMQ · ~30 min

Notes:
Minute 130.
The most resilient call is the one you never make synchronously. results-service keeps its tally current by consuming EVENTS, not by being called. Make sure RabbitMQ is up: `docker compose up -d rabbitmq` (management UI at :15672, guest/guest).

Likely questions
- Q: Does the producer wait for aggregation? A: No; the consumer updates the tally asynchronously.

---

## Sync vs. Async

```text
SYNC   survey-service ──HTTP──▶ results-service
       (both must be up, at the same time, right now)

ASYNC  survey-service ──▶ [ bbq-votes ] ──▶ results-service
       (publish and move on; consumer catches up on its own time)
```

Async **decouples in time**: the broker can queue votes while results-service is stopped. The lab does not implement publisher confirms or a transactional outbox.

Notes:
Minute 130-133.
Contrast the two arrows. Synchronous coupling means a failure in one is a failure in both, at the same moment. A broker in the middle buffers: the producer doesn't know or care whether a consumer is up. That's temporal decoupling, and it's why the tally is naturally resilient.

Likely questions
- Q: Why keep the sync /tally call from Module 3 then? A: Different jobs. The event keeps the tally CURRENT; the sync read was our lab for circuit breakers. Real systems mix both deliberately.
- Q: Kafka or Rabbit? A: Spring Cloud Stream abstracts the broker. We use the RabbitMQ binder; swapping to Kafka is a dependency change, not a code change.

---

## The Spring Cloud Stream Model

<svg class="dg" viewBox="0 0 1000 296" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-e-scs" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker></defs>
<text class="lbl-e end" x="68" y="121" letter-spacing="1.3">PUBLISH</text>
<text class="lbl-e end" x="68" y="205" letter-spacing="1.3">CONSUME</text>
<rect class="band" x="80" y="40" width="265" height="210" rx="14"/>
<text class="lbl" x="212" y="65" letter-spacing="1.3">JAVA.UTIL.FUNCTION</text>
<rect class="n-app" x="96" y="82" width="233" height="68" rx="12"/>
<text class="mono" x="212" y="110" style="font-size:15px;font-weight:700;fill:#191e1e">Supplier&lt;T&gt;</text>
<text class="sub" x="212" y="132">you write this</text>
<rect class="n-app" x="96" y="166" width="233" height="68" rx="12"/>
<text class="mono" x="212" y="194" style="font-size:15px;font-weight:700;fill:#191e1e">Consumer&lt;T&gt;</text>
<text class="sub" x="212" y="216">you write this too</text>
<path class="async" d="M351,116 H394" marker-end="url(#a-e-scs)"/>
<path class="async" d="M394,200 H351" marker-end="url(#a-e-scs)"/>
<rect class="band" x="400" y="40" width="265" height="210" rx="14"/>
<text class="lbl" x="532" y="65" letter-spacing="1.3">BINDING</text>
<rect class="n-plain" x="416" y="82" width="233" height="68" rx="12"/>
<text class="mono" x="532" y="110" style="font-size:15px;font-weight:700;fill:#191e1e">surveyVote-out-0</text>
<text class="sub" x="532" y="132">out-0 &#183; outbound</text>
<rect class="n-plain" x="416" y="166" width="233" height="68" rx="12"/>
<text class="mono" x="532" y="194" style="font-size:15px;font-weight:700;fill:#191e1e">surveyVote-in-0</text>
<text class="sub" x="532" y="216">in-0 &#183; inbound</text>
<path class="async" d="M671,116 H714" marker-end="url(#a-e-scs)"/>
<path class="async" d="M714,200 H671" marker-end="url(#a-e-scs)"/>
<rect class="band" x="720" y="40" width="265" height="210" rx="14"/>
<text class="lbl" x="852" y="65" letter-spacing="1.3">DESTINATION</text>
<rect class="n-msg" x="736" y="82" width="233" height="68" rx="12"/>
<text class="mono" x="852" y="110" style="font-size:15px;font-weight:700;fill:#c0392b">bbq-votes</text>
<text class="sub" x="852" y="132">the exchange</text>
<rect class="n-msg" x="736" y="166" width="233" height="68" rx="12"/>
<text class="mono" x="852" y="194" style="font-size:15px;font-weight:700;fill:#c0392b">bbq-votes</text>
<text class="sub" x="852" y="216">a queue bound to it</text>
<text class="lbl-g" x="532" y="278">you write the two functions &#8212; the binder generates everything to their right</text>
</svg>

- Your code is **plain functions** — no broker API.
- A **binder** maps functions ↔ the broker (RabbitMQ here).
- **Bindings** (`<name>-out-0` / `-in-0`) connect to **destinations**.

Notes:
Minute 133-136.
The big idea: messaging as functions. You write `Supplier`/`Function`/`Consumer`; Spring Cloud Stream + the binder handle exchanges, queues, serialization, acks, retries. The naming convention `<function>-in-0` / `-out-0` is the glue between your bean and a destination.

Likely questions
- Q: Where do @RabbitListener / RabbitTemplate go? A: You don't need them. The functional model is broker-agnostic; drop to native APIs only for broker-specific features.
- Q: What's the `-0`? A: The input/output index — functions can have multiple, but one is the common case.

---

## Publishing a Vote

`survey-service` sends an event when a vote is recorded:

```java
private static final String VOTE_OUT = "surveyVote-out-0";

public SurveyVote recordVote(SurveyVote vote) {
    SurveyVote saved = repository.save(vote);

    var event = new SurveyVoteEvent(saved.getQuestionId(), saved.getAnswer(),
            saved.getTimestamp(), saved.getVoterId());
    if (!streamBridge.send(VOTE_OUT, event)) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Vote publication failed; tally was not updated");
    }

    return saved;                 // persisted + published, then move on
}
```

`StreamBridge` publishes to a binding on demand — perfect for
"send in reaction to an HTTP request."

Notes:
Minute 136-139.
`StreamBridge` is the imperative producer: publish from inside a request handler without declaring a `Supplier`. Save first, then publish. The event is a plain record. The service is done the moment it returns — no waiting on a consumer.

Likely questions
- Q: StreamBridge vs Supplier? A: Supplier is for stream-driven/polled sources; StreamBridge is for "publish because something just happened," which fits an HTTP-triggered vote.
- Q: What about the DB-then-publish gap? A: Real systems close it with the transactional outbox pattern; out of scope here, worth naming.

---

## Consuming a Vote

`results-service` — a `Consumer` bean **is** the message handler:

```java
@Bean
public Consumer<SurveyVoteEvent> surveyVote() {
    return event -> {
        Tally tally = repository.findById(event.questionId())
                .orElseGet(() -> new Tally(event.questionId()));
        tally.incrementAnswer(event.answer());
        repository.save(tally);
    };
}
```

No queues, no acks, no broker imports. Just a function.

Notes:
Minute 139-142.
This is the whole consumer. The bean NAME (`surveyVote`) is what Spring Cloud Function binds to `surveyVote-in-0`. Every vote event increments the tally. Compare to the synchronous world: results-service never exposes an endpoint to be hammered; it consumes at its own pace.

Likely questions
- Q: How does Spring know this bean is the handler? A: `spring.cloud.function.definition: surveyVote` names it. The binding `surveyVote-in-0` wires it to the destination.
- Q: Exceptions in the consumer? A: Configurable retry, then dead-letter. Rabbit binder supports DLQ out of the box.

---

## Wiring the Bindings

Producer — `survey-service/application.yaml`:

```yaml
spring:
  cloud:
    stream:
      bindings:
        surveyVote-out-0:
          destination: bbq-votes
```

Consumer — `results-service/application.yaml`:

```yaml
spring:
  cloud:
    function:
      definition: surveyVote
    stream:
      bindings:
        surveyVote-in-0:
          destination: bbq-votes
          group: results-service     # durable, load-balanced delivery
```

Notes:
Minute 142-145.
Same `destination: bbq-votes` on both sides is what connects them. The `group` is critical: it gives results-service a durable, named queue so events survive a restart and are load-balanced (not duplicated) across replicas. Without a group, each instance is a separate subscriber.

Likely questions
- Q: Two results-service instances without a group? A: Both get every event — double counting. With a group, instances share one queue, with at-least-once delivery. This lab runs ONE results-service because each H2 store is private; replicas need a shared durable store and idempotent processing.
- Q: Where's the queue created? A: The binder declares the exchange and group queue on startup.

---

## Exercise 4 — Watch It Flow (12 min)

Vote, and watch the tally update through the broker:

```bash
# make sure results-service is running and RabbitMQ is up
curl -X POST localhost:8081/submit -H 'Content-Type: application/json' \
  -d '{"questionId":"burnt-ends","answer":"Joe'\''s Kansas City","voterId":"me"}'

# the consumer updated the tally (a different service!):
curl localhost:8083/burnt-ends
```

Then open **http://localhost:8080** — the chart moves as votes land.
And **http://localhost:15672** (guest/guest) → Exchanges → `bbq-votes`.

Notes:
Minute 145-157.
The event crosses a service boundary: survey-service published, results-service consumed and aggregated, and you read the result from results-service directly. In the RabbitMQ UI they can watch message rates on the `bbq-votes` exchange and the `bbq-votes.results-service` queue. Bonus: kill results-service, cast three votes, restart it — the grouped queue held the new messages. H2 loses previously consumed tallies on restart, so the restarted tally contains only the queued votes. Persistent storage is the production upgrade.

Likely questions
- Q: Tally still 0? A: Check results-service consumed (its log prints "Received SurveyVoteEvent"), and that both sides share `destination: bbq-votes`.
- Q: Votes cast while the consumer was down? A: With a durable group they wait in the queue and process on restart. Show it — it's the punchline.

---

## Answer 4 — Events, Not Calls

<svg class="dg" viewBox="0 0 1000 378" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-str" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker><marker id="a-e-str" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker><marker id="a-w-str" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#8fa0a0"/></marker></defs>
<rect class="n-plain" x="20" y="60" width="180" height="60" rx="12"/>
<text class="mono" x="110" y="95" style="font-size:13px;fill:#4a5a5a">POST /submit</text>
<path class="flow" d="M206,90 H238" marker-end="url(#a-g-str)"/>
<rect class="n-app" x="250" y="46" width="230" height="92" rx="12"/>
<text class="t-sm" x="365" y="77">survey-service</text>
<text class="sub" x="365" y="101">saves the vote (H2)</text>
<text class="sub" x="365" y="123">publishes the event</text>
<path class="async" d="M486,90 H518" marker-end="url(#a-e-str)"/>
<rect class="n-msg" x="530" y="62" width="180" height="56" rx="28"/>
<text class="t-sm" x="620" y="87" style="font-size:15px;fill:#c0392b">RabbitMQ</text>
<text class="mono" x="620" y="107" style="fill:#b45a4d">bbq-votes</text>
<path class="async" d="M716,90 H748" marker-end="url(#a-e-str)"/>
<rect class="n-app" x="760" y="46" width="220" height="92" rx="12"/>
<text class="t-sm" x="870" y="77">results-service</text>
<text class="sub" x="870" y="101">consumes the event</text>
<text class="sub" x="870" y="123">increments the Tally</text>
<path class="flow" d="M310,138 V180 Q310,190 300,190 H120 Q110,190 110,180 V134" marker-end="url(#a-g-str)"/>
<text class="lbl" x="250" y="212">200 OK &#8212; returned after the publish, not after the tally</text>
<path class="weak" d="M870,138 V262" marker-end="url(#a-w-str)"/>
<text class="lbl end" x="856" y="205">from the tally</text>
<rect class="n-plain" x="20" y="286" width="230" height="58" rx="12"/>
<text class="mono" x="135" y="321" style="font-size:13px;fill:#4a5a5a">GET /burnt-ends</text>
<path class="flow" d="M256,315 H320" marker-end="url(#a-g-str)"/>
<rect class="n-plain" x="332" y="268" width="648" height="94" rx="12"/>
<text class="mono start" x="356" y="298" style="font-size:13px;fill:#4a5a5a">{ "questionId": "burnt-ends",</text>
<text class="mono start" x="372" y="320" style="font-size:13px;fill:#4a5a5a">"answerCounts": { "Joe's Kansas City": 1 },</text>
<text class="mono start" x="372" y="342" style="font-size:13px;fill:#4a5a5a">"totalResponses": 1 }</text>
</svg>

survey-service never waited for results-service. The broker carried the news.

Notes:
Minute 157-159.
Reinforce temporal decoupling one more time and connect back: this is why the live-results page updates even under load, and why queued events survive a consumer restart. Previously consumed H2 tallies do not survive; distinguish message durability from database durability. The most resilient integration is asynchronous.

Likely questions
- Q: Why is the first tally read sometimes zero? A: The consumer may not have processed the vote yet. Poll again.

---

## Module 4 Checkpoint

You can now:

- publish domain **events** with `StreamBridge`;
- consume them with a `Consumer<T>` **bean** — no broker API;
- connect producers and consumers via **destinations**;
- get **durable, load-balanced** delivery with a consumer **group**;
- swap RabbitMQ for Kafka by changing the binder dependency **and broker configuration**; domain functions stay the same.

**Break — 15 minutes. Then: one secure front door for the whole system.**

Notes:
Minute 159-160, then BREAK (160-175).
Over the break, start Redis for the next module: `docker compose up -d redis`. When we return, the gateway becomes the single public entry point with routing, JWT security, and rate limiting.

Likely questions
- Q: Can I add another results replica? A: Only after moving the tally to a shared durable store and handling duplicate delivery.
