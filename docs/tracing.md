<!-- .slide: data-background-color="#191e1e" -->

# Module 6

## Distributed Tracing

Micrometer Tracing → OpenTelemetry → Grafana · ~20 min

Notes:
Minute 215.
We built a system where a request touches the gateway, survey-service, results-service, and a broker hop. When it's slow, "check the logs" means four log files and no timeline. Tracing gives one picture. Start the stack: `docker compose up -d grafana-lgtm` (Grafana at :3000).

Likely questions
- Q: Which apps emit traces? A: Gateway, survey-service, and results-service.

---

## The Problem: Where Did the Time Go?

```text
browser ─▶ gateway ─▶ survey-service ─▶ results-service
                           └─▶ [ bbq-votes ] ─▶ results-service
```

A 900ms request. **Which hop** was slow?

- Four services, four clocks, four log files.
- No shared request id → no way to stitch them together.

> You can't fix latency you can't attribute.

Notes:
Minute 215-218.
Distributed systems turn "why is this slow" into a forensic exercise. Without correlation you're grepping timestamps across services and guessing. Tracing assigns one id to the whole request and times every hop.

Likely questions
- Q: Aren't metrics enough? A: Metrics tell you the system is slow; traces tell you WHERE. Logs tell you WHY at that span. You want all three — and the same stack gives them.

---

## Traces and Spans

<svg class="dg" viewBox="0 0 1000 262" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-tr1" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker></defs>
<text class="t-sm start" x="20" y="30">Trace</text>
<text class="lbl end" x="980" y="30">one trace-id, end to end</text>
<path d="M20,44 H980" stroke="#e6ecec" stroke-width="2" fill="none"/>
<path class="thin" d="M30,88 V125 H44"/>
<path class="thin" d="M30,125 V225 H44"/>
<path class="thin" d="M58,138 V175 H72"/>
<rect class="track" x="450" y="62" width="450" height="26" rx="4"/>
<rect x="450" y="62" width="450" height="26" rx="4" fill="#6db33f" opacity="1"/>
<text class="start" x="20" y="80" font-size="15" font-weight="700" fill="#191e1e">gateway<tspan font-weight="400" font-size="13" fill="#93a2a2"> </tspan></text>
<text class="mono end" x="980" y="80" style="fill:#4a5a5a">120ms</text>
<rect class="track" x="450" y="112" width="450" height="26" rx="4"/>
<rect x="525" y="112" width="300" height="26" rx="4" fill="#6db33f" opacity=".8"/>
<text class="start" x="48" y="130" font-size="15" font-weight="700" fill="#191e1e">survey-service<tspan font-weight="400" font-size="13" fill="#93a2a2"> </tspan></text>
<text class="mono end" x="980" y="130" style="fill:#4a5a5a">80ms</text>
<rect class="track" x="450" y="162" width="450" height="26" rx="4"/>
<rect x="600" y="162" width="113" height="26" rx="4" fill="#6db33f" opacity=".62"/>
<text class="start" x="76" y="180" font-size="15" font-weight="700" fill="#191e1e">results-service<tspan font-weight="400" font-size="13" fill="#93a2a2"> &#183; HTTP tally</tspan></text>
<text class="mono end" x="980" y="180" style="fill:#4a5a5a">30ms</text>
<rect class="track" x="450" y="212" width="450" height="26" rx="4"/>
<rect x="825" y="212" width="8" height="26" rx="4" fill="#c0392b" opacity="1"/>
<text class="start" x="48" y="230" font-size="15" font-weight="700" fill="#c0392b">publish<tspan font-weight="400" font-size="13" fill="#b45a4d"> &#8594; bbq-votes</tspan></text>
<text class="mono end" x="980" y="230" style="fill:#4a5a5a">2ms</text>
</svg>

- **Trace** — the whole request, one id end to end.
- **Span** — one unit of work, with parent, timing, tags.
- **Context propagation** — the id rides along every hop.

Notes:
Minute 218-221.
A trace is a tree of spans sharing a trace-id. Each span knows its parent, so you get a waterfall. The magic is propagation: the trace-id travels in HTTP headers (W3C `traceparent`) and in message headers, so spans created in different services join the same trace.

Likely questions
- Q: Is an async span inside the HTTP duration? A: Not necessarily; consumption can finish after the HTTP response.

---

## Micrometer Tracing

```text
   your code / Spring          Micrometer            OpenTelemetry
   ──────────────────    ───────────────────    ──────────────────
   auto-instrumented ──▶ Observation API   ──▶  bridge-otel ──▶ OTLP ──▶ Grafana
   (web, client, rabbit)  (one timing model)     (exporter)          (Tempo)
```

- Spring auto-instruments web, RestClient, and the Rabbit binder.
- **Micrometer Observation** is the vendor-neutral timing API.
- The **OTel bridge** exports spans over OTLP.

Notes:
Minute 221-224.
One Observation both times an operation (metric) AND emits a span (trace) — measure once, get both. You rarely write tracing code; the framework instruments the entry and exit points. We export via OpenTelemetry's OTLP to Grafana's Tempo. Vendor-neutral top to bottom — swap Grafana for Honeycomb or Jaeger with a config change.

Likely questions
- Q: Do I annotate methods? A: Usually no — boundaries are auto-instrumented. Add `@Observed` or a manual span only for custom in-process work you want on the timeline.
- Q: Sleuth? A: Micrometer Tracing is its successor. Same idea, OpenTelemetry-native.

---

## Boot 4: One Starter, Two Properties

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0        # trace EVERYTHING (demos & low volume)
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
```

Notes:
Minute 224-227.
The bridge routes Micrometer spans to OpenTelemetry; the OTLP exporter ships them to the collector at :4318 (Grafana LGTM bundles the collector + Tempo). Sampling 1.0 means every request is traced — perfect for a workshop, too expensive at scale, where you'd sample a few percent or use tail-based sampling.

Likely questions
- Q: 1.0 in production? A: No — sample a small fraction, or tail-sample to keep the interesting (slow/errored) traces. Sampling is the cost dial.
- Q: Does actuator show trace ids in logs? A: Yes — the trace/span id is added to the log MDC, so logs correlate to traces.

---

## Propagation Comes for Free

```text
HTTP:   gateway ──▶ survey-service
        header: traceparent: 00-<trace-id>-<span-id>-01

RABBIT: survey-service ──▶ [ bbq-votes ] ──▶ results-service
        the trace-id rides in the message headers
```

The async consume in results-service joins the **same** trace as the
HTTP request that published it. No code changes.

Notes:
Minute 227-229.
This is the part that surprises people: the trace survives the jump through RabbitMQ. The binder propagates context in message headers, so the consumer span links back to the producer's trace. You see the synchronous chain AND the async hop in one timeline.

Likely questions
- Q: Across the broker automatically? A: Yes, the Spring Cloud Stream Rabbit binder is instrumented to carry and restore trace context.

---

## Exercise 6 — See the Trace (8 min)

Generate a request through the whole chain, then look:

```bash
# a vote goes gateway → survey-service → (rabbit) → results-service
TOKEN=$(curl -s localhost:8080/token | sed 's/.*"access_token":"//;s/".*//')
curl -X POST localhost:8080/survey-service/submit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"questionId":"burnt-ends","answer":"Q39","voterId":"me"}'

# a tally read fans out too
curl localhost:8080/survey-service/tally/burnt-ends
```

Open **http://localhost:3000** → **Explore** → **Tempo** →
*Search* → pick a recent trace.

Notes:
Minute 227-233.
Grafana LGTM ships with Tempo wired into Explore. Search returns recent traces; open one and you get the waterfall across gateway, survey-service, and results-service, including the RabbitMQ publish/consume. Point out the total duration and which span owns it — that's the whole value proposition in one screen.

Likely questions
- Q: No traces in Tempo? A: Confirm the OTLP endpoint (:4318) and that grafana-lgtm is up. Sampling is 1.0, so traffic should appear within seconds.
- Q: Only the gateway span? A: The downstream services also need the tracing deps + endpoint — they're already in the lab; confirm each service started cleanly.

---

## Answer 6 — One Request, One Timeline

<svg class="dg" viewBox="0 0 1000 262" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-tr2" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker></defs>
<text class="t-sm start" x="20" y="30">Trace 4f3c&#8230;</text>
<text class="lbl end" x="980" y="30">total 142ms</text>
<path d="M20,44 H980" stroke="#e6ecec" stroke-width="2" fill="none"/>
<path class="thin" d="M30,88 V125 H44"/>
<path class="thin" d="M58,138 V175 H72"/>
<path class="thin" d="M86,188 V225 H100"/>
<rect class="track" x="450" y="62" width="450" height="26" rx="4"/>
<rect x="450" y="62" width="450" height="26" rx="4" fill="#6db33f" opacity="1"/>
<text class="start" x="20" y="80" font-size="15" font-weight="700" fill="#191e1e">gateway<tspan font-weight="400" font-size="13" fill="#93a2a2"> POST /survey-service/submit</tspan></text>
<text class="mono end" x="980" y="80" style="fill:#4a5a5a">142ms</text>
<rect class="track" x="450" y="112" width="450" height="26" rx="4"/>
<rect x="488" y="112" width="311" height="26" rx="4" fill="#6db33f" opacity=".8"/>
<text class="start" x="48" y="130" font-size="15" font-weight="700" fill="#191e1e">survey-service<tspan font-weight="400" font-size="13" fill="#93a2a2"> POST /submit</tspan></text>
<text class="mono end" x="980" y="130" style="fill:#4a5a5a">98ms</text>
<rect class="track" x="450" y="162" width="450" height="26" rx="4"/>
<rect x="751" y="162" width="10" height="26" rx="4" fill="#c0392b" opacity="1"/>
<text class="start" x="76" y="180" font-size="15" font-weight="700" fill="#c0392b">StreamBridge<tspan font-weight="400" font-size="13" fill="#b45a4d"> publish &#8594; bbq-votes</tspan></text>
<text class="mono end" x="980" y="180" style="fill:#4a5a5a">3ms</text>
<rect class="track" x="450" y="212" width="450" height="26" rx="4"/>
<rect x="767" y="212" width="130" height="26" rx="4" fill="#c0392b" opacity=".7"/>
<text class="start" x="104" y="230" font-size="15" font-weight="700" fill="#c0392b">results-service<tspan font-weight="400" font-size="13" fill="#b45a4d"> consume surveyVote-in-0</tspan></text>
<text class="mono end" x="980" y="230" style="fill:#4a5a5a">41ms</text>
</svg>

Illustrative timing: actual span names and durations vary. H2 method spans
require additional instrumentation and are not created by this lab.

Notes:
Minute 233-234.
The forensic exercise from the first slide is now a glance. This is observability's payoff: attribute latency, see the async hop inline, and find matching trace IDs in the service terminal logs. The lab exports traces; shipping logs to Loki and metrics to Prometheus needs additional configuration.

Likely questions
- Q: Will timings match these numbers? A: No; the diagram is illustrative. Verify shared trace IDs and service boundaries.

---

## Module 6 Checkpoint

You can now:

- add tracing with **one starter and two properties**;
- get **auto-instrumented** spans for web, clients, and the broker;
- follow one **trace-id** across services **and** across RabbitMQ;
- read a **waterfall** in Grafana Tempo to find the slow hop;
- correlate **logs → traces** by shared id.

**That's the full toolbox. Let's put it together.**

Notes:
Minute 234-235.
Every abstract bullet is now built and running. Move Right to the wrap: the production blueprint and a pre-ship checklist that ties all six modules together.

Likely questions
- Q: Do logs automatically appear in Loki? A: No; this lab exports traces and correlates terminal logs by trace ID.
