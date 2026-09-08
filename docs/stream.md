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

```text
   java.util.function          binding            destination
   ─────────────────    ──────────────────    ───────────────
   Supplier<T>   ──▶    surveyVote-out-0   ──▶   bbq-votes   (exchange)
   Consumer<T>   ◀──    surveyVote-in-0    ◀──   bbq-votes
```

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

Then open **http://localhost:15672** (guest/guest) → Exchanges → `bbq-votes`.

Notes:
Minute 145-157.
The event crosses a service boundary: survey-service published, results-service consumed and aggregated, and you read the result from results-service directly. In the RabbitMQ UI they can watch message rates on the `bbq-votes` exchange and the `bbq-votes.results-service` queue. Bonus: kill results-service, cast three votes, restart it — the grouped queue held the new messages. H2 loses previously consumed tallies on restart, so the restarted tally contains only the queued votes. Persistent storage is the production upgrade.

Likely questions
- Q: Tally still 0? A: Check results-service consumed (its log prints "Received SurveyVoteEvent"), and that both sides share `destination: bbq-votes`.
- Q: Votes cast while the consumer was down? A: With a durable group they wait in the queue and process on restart. Show it — it's the punchline.

---

## Answer 4 — Events, Not Calls

```text
POST /submit ─▶ survey-service
                   │ save vote (H2)
                   │ publish SurveyVoteEvent ─▶ [ bbq-votes ]
                   ▼                                  │
                200 OK (after publish)                  ▼
                                          results-service consumes
                                          increments Tally
GET /burnt-ends ─▶ results-service  ─▶  {questionId: "burnt-ends", answerCounts: {"Joe's Kansas City": 1}, totalResponses: 1}
```

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
