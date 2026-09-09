<!-- .slide: data-background-color="#191e1e" -->

# Module 1

## Centralized Configuration

Spring Cloud Config Server & Client · ~30 min

Notes:
Minute 20.
We start with config because every other service depends on it and it needs zero infrastructure — just the JVM. Press Down to walk the module; Right skips ahead if you're ever short on time.

Likely questions
- Q: Why config first? A: It's the lowest-friction win and the thing that quietly ruins microservices when done badly (config drift across environments).

---

## The Problem: Config in the Jar

<svg class="dg" viewBox="0 0 1000 250" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-e-jar" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#c0392b"/></marker></defs>
<rect class="n-plain" x="30" y="24" width="430" height="210" rx="12"/>
<text class="mono" x="245" y="56" style="font-size:15px;font-weight:700;fill:#191e1e">survey-service.jar</text>
<rect x="54" y="72" width="382" height="140" rx="10" fill="#f4f8f8" stroke="#cfd8d8" stroke-width="2"/>
<text class="mono" x="245" y="98" style="font-size:14px;fill:#4a5a5a">application.yaml</text>
<rect class="n-plain" x="72" y="112" width="168" height="34" rx="8"/>
<text class="sub" x="156" y="134" style="font-size:13px;fill:#4a5a5a">the BBQ questions</text>
<rect class="n-plain" x="252" y="112" width="168" height="34" rx="8"/>
<text class="sub" x="336" y="134" style="font-size:13px;fill:#4a5a5a">breaker thresholds</text>
<rect class="n-plain" x="72" y="156" width="168" height="34" rx="8"/>
<text class="sub" x="156" y="178" style="font-size:13px;fill:#4a5a5a">service endpoints</text>
<rect class="n-plain" x="252" y="156" width="168" height="34" rx="8"/>
<text class="sub" x="336" y="178" style="font-size:13px;fill:#4a5a5a">secrets</text>
<text class="lbl-e" x="500" y="115">1-line edit</text>
<path class="async" d="M466,129 H534" marker-end="url(#a-e-jar)"/>
<rect class="n-bad" x="540" y="24" width="430" height="210" rx="12"/>
<text class="t-sm" x="755" y="57" style="fill:#c0392b">&#8230; costs a full release</text>
<rect class="n-msg" x="565" y="76" width="380" height="42" rx="8"/>
<text class="t-sm" x="755" y="103" style="font-size:15px">rebuild the jar</text>
<rect class="n-msg" x="565" y="128" width="380" height="42" rx="8"/>
<text class="t-sm" x="755" y="155" style="font-size:15px">redeploy it</text>
<rect class="n-msg" x="565" y="180" width="380" height="42" rx="8"/>
<text class="t-sm" x="755" y="207" style="font-size:15px">restart every instance</text>
</svg>

- A new BBQ question → **rebuild + redeploy**.
- Prod vs staging differ → **a different jar per environment**.
- A secret rotates → **rebuild**.
- Ten services × three environments → **thirty places to drift**.

> Configuration changes far more often than code. Stop shipping it inside the code.

Notes:
Minute 20-23.
Name the pain everyone has felt: a one-line property change triggering a full release. The goal is to move configuration OUT of the deployable and behind an API.

Likely questions
- Q: Isn't an env var enough? A: For a handful, yes. It falls apart at scale: no history, no per-service inheritance, no refresh, secrets sprawled across platforms.

---

## Spring Cloud Config: Two Halves

<svg class="dg" viewBox="0 0 1000 320" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-cfg" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker></defs>
<text class="lbl start" x="12" y="48" letter-spacing="1.5">SERVER</text>
<text class="lbl start" x="12" y="244" letter-spacing="1.5">CLIENTS</text>
<rect class="n-app" x="290" y="8" width="420" height="88" rx="12"/>
<text class="t" x="500" y="46">config-server</text>
<text class="sub" x="500" y="71">:8888 &#183; reads a backend: git or the filesystem</text>
<path class="flow" d="M500,96 V158"/>
<rect x="320" y="112" width="360" height="34" rx="17" fill="#ffffff" stroke="#e2e9e9" stroke-width="2"/>
<text class="mono" x="500" y="134">GET /survey-service/default</text>
<path class="flow" d="M500,158 Q500,168 490,168 H310 Q300,168 300,178 V196" marker-end="url(#a-g-cfg)"/>
<path class="flow" d="M500,158 Q500,168 510,168 H700 Q710,168 710,178 V196" marker-end="url(#a-g-cfg)"/>
<rect class="n-app" x="150" y="204" width="300" height="76" rx="12"/>
<text class="t" x="300" y="238">survey-service</text>
<text class="port" x="300" y="263">:8081 &#183; :8082</text>
<rect class="n-app" x="560" y="204" width="300" height="76" rx="12"/>
<text class="t" x="710" y="238">results-service</text>
<text class="port" x="710" y="263">:8083</text>
<text class="lbl" x="500" y="306">each client asks by spring.application.name + active profile</text>
</svg>

- **Server** — one endpoint, backed by a versioned source of truth.
- **Client** — asks for its config *by application name* at startup.

Notes:
Minute 23-26.
The server is a tiny Spring Boot app. The clients don't hardcode values; they fetch them keyed on `spring.application.name` and the active profile. Git backing gives you audit + rollback for free.

Likely questions
- Q: Where do secrets go? A: Not here in plaintext for real systems — back the server with Vault, or use a secrets manager. Config Server has a Vault backend.
- Q: What if the server is down? A: Running clients retain their current values. `optional:` allows a new process to start without remote config, but does not persist a last-known-good cache; this lab then has no questions. Config-first-boot ordering matters — we'll cover it.

---

## The Config Server

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

That annotation is the whole server. The backend is configuration:

```yaml
spring:
  profiles:
    active: native            # filesystem instead of git (offline lab)
  cloud:
    config:
      server:
        native:
          search-locations: ${CONFIG_REPO:file:../config-repo}
server:
  port: 8888
```

Notes:
Minute 26-29.
`@EnableConfigServer` + one dependency = a Config Server. We use the `native` (filesystem) backend so the workshop is offline. In production you'd delete the `native` profile and set `spring.cloud.config.server.git.uri` — the client contract is identical either way.

Likely questions
- Q: git vs native? A: Same client behavior. Git adds versioning, PR review, and rollback. Native is perfect for a laptop.
- Q: Can it serve YAML and properties? A: Both, plus profile- and label-specific files.

Config Server Configuration
- either set CONFIG_REPO to absolute path to `config-repo` or run app with working directory of `config-server`

---

## The Backend: config-repo/

```text
config-repo/
├── application.yml          # shared by both config clients
├── survey-service.yml       # the BBQ questions live here
└── results-service.yml
```

`survey-service.yml`:

```yaml
survey:
  questions:
    - id: burnt-ends
      text: "Who has the best burnt ends in KC?"
      answers: ["Arthur Bryant's", "Gates Bar-B-Q",
                "Joe's Kansas City", "LC's Bar-B-Q", "Q39"]
```

Notes:
Minute 29-31.
File name = application name. `application.yml` is inherited by all; a service-named file overrides it. The BBQ questions are pure configuration — that's why a new question needs no rebuild.

Likely questions
- Q: How does inheritance resolve? A: `application.yml` first, then `{service}.yml`, then `{service}-{profile}.yml`, most specific wins.

---

## The Client Side

One property turns any Spring Boot app into a config client:

```yaml
spring:
  application:
    name: survey-service       # ← the filename the server looks up
  config:
    import: optional:configserver:http://localhost:8888
```

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Notes:
Minute 31-34.
`spring.config.import=configserver:...` is the modern client (no more bootstrap.yml). `optional:` means "boot even if the server is unreachable" — important so a config outage doesn't cascade into a total outage. The application NAME is the lookup key.

Likely questions
- Q: Why `optional:`? A: Resilience. Drop it and the app refuses to start without the server. It does not provide a persistent config cache. Start Config Server first; production clients may prefer a required import plus retry.
- Q: bootstrap.yml? A: Gone. `spring.config.import` replaced it years ago.

---

## Config Becomes Typed Objects

The delivered `survey.*` properties bind to a class:

```java
@Component
@ConfigurationProperties(prefix = "survey")
public class SurveyQuestions {
    private List<Question> questions;
    public static class Question {
        private String id;
        private String text;
        private List<String> answers;
        // getters / setters
    }
    // getters / setters
}
```

Remote config → compiler-checked Java. No `@Value` string soup.

Notes:
Minute 34-36.
This is the payoff: configuration arrives as a real object graph. survey-service didn't ship the questions — it received them and bound them to types.

Likely questions
- Q: Relaxed binding? A: Yes — `survey.questions[0].id` maps to nested records/POJOs, kebab or camel case.

---

## Exercise 1 — Wire the Client (10 min)

With RabbitMQ running from setup, start config, then discovery, then survey-service:

```bash
cd labs/bbq-wednesday
# Config first. ENCRYPT_KEY now, so the secrets demo at minute 48 needs no restart:
(cd config-server && ENCRYPT_KEY=bbq-workshop-key ../mvnw spring-boot:run) &   # :8888
(cd eureka-server && ../mvnw spring-boot:run) &                                # :8761
# Wait for config before starting its client:
until curl -fsS localhost:8888/survey-service/default >/dev/null; do sleep 1; done
(cd survey-service && ../mvnw spring-boot:run) &   # :8081
```

1. Confirm the server serves config:
   `curl localhost:8888/survey-service/default`
2. Confirm the client received the questions:
   `curl localhost:8081/questions`

Do it first. The answer is next.

Notes:
Minute 36-46.
Goal: see config flow server → client. The `/survey-service/default` endpoint returns the raw property sources the server resolved. `/questions` proves survey-service bound them. If `/questions` is empty, the client didn't import config — check `spring.config.import` and the dependency.

Likely questions
- Q: `/questions` returns null/empty? A: The import is missing or the server isn't up yet. Restart survey-service after config-server is listening.
- Q: Order matters? A: Start config-server before its clients so first-boot resolution succeeds. `optional:` keeps a late start from crashing.

---

## Answer 1 — Config Flowing

`curl localhost:8888/survey-service/default` →

```json
{ "name": "survey-service", "profiles": ["default"],
  "propertySources": [
    { "name": ".../survey-service.yml",
      "source": { "survey.questions[0].id": "burnt-ends", ... } }
  ] }
```

`curl localhost:8081/questions` →

```json
[ { "id": "burnt-ends",
    "text": "Who has the best burnt ends in KC?",
    "answers": ["Arthur Bryant's", "Gates Bar-B-Q", ...] } ]
```

The jar never contained a single BBQ question.

Notes:
Minute 46-48.
Land the point: two services, and the questions existed only in config-repo. Change the file, and every survey-service instance can pick it up — which is the next slide.

Likely questions
- Q: Why are my questions empty? A: Check the Config Server response and restart the client after config is available.

---

## Secrets: Don't Store Them in the Clear

Config Server can **encrypt** values so the backend holds ciphertext:

```bash
# 1. the key was set back in Exercise 1. No key = every /encrypt call 500s.
ENCRYPT_KEY=bbq-workshop-key ./mvnw spring-boot:run

# 2. confirm the key took — do this BEFORE you demo:
curl localhost:8888/encrypt/status
# → {"status":"OK"}

# 3. encrypt. text/plain matters: with curl's default form encoding,
#    a '+' inside a secret silently arrives as a space.
curl localhost:8888/encrypt -H 'Content-Type: text/plain' -d 's3cr3t-bbq-sauce'
# → f134a0a13622ce45...   (store THIS in config-repo)
```

```properties
# config-repo/survey-service.yml — ciphertext example, concept only
spring.rabbitmq.password: '{cipher}f134a0a13622ce45...'
```

The client receives the value **already decrypted**. For real secret
management, back the server with **HashiCorp Vault** instead of git.

Notes:
Minute 48-50 (optional — skip if pressed).
The 2015-era point still holds: never commit plaintext secrets. Config Server decrypts `{cipher}`-prefixed values on the way to the client, using a symmetric `encrypt.key` (env `ENCRYPT_KEY`) or an asymmetric keystore. The key is an env var, never a committed property. If you forget it, `/encrypt` answers a bare `500` with no explanation on this Boot 4 / Config Server 5 pair — not the documented `NO_KEY` body — so `/encrypt/status` is the check worth teaching. Vault is the production answer — Config Server has a first-class Vault backend, so the client contract doesn't change.

Likely questions
- Q: Symmetric or asymmetric? A: Symmetric `encrypt.key` is simplest; an RSA keystore lets you encrypt anywhere and decrypt only on the server. Both use the same `{cipher}` marker.
- Q: Is `{cipher}` decrypted before the client sees it? A: By default yes, server-side. You can also ship ciphertext and decrypt on the client.
- Q: I get a 500 from `/encrypt`. A: The server has no key. Restart it with `ENCRYPT_KEY` set and check `/encrypt/status` returns `{"status":"OK"}`.
- Q: My decrypted secret is wrong. A: `curl -d` sends form-urlencoded, which turns `+` into a space. Add `-H 'Content-Type: text/plain'`. It fails silently — valid ciphertext for the wrong plaintext.
- Q: Why doesn't my ciphertext match the slide? A: It never will. The symmetric encryptor salts every call, so the same secret encrypts differently each time — all of them decrypt back to the same plaintext.

---

## Refresh Without Redeploy

Change config at runtime — no restart:

```bash
# 1. edit config-repo/survey-service.yml (add an answer)
# 2. tell the running service to re-read:
curl -X POST localhost:8081/actuator/refresh
```

`@ConfigurationProperties` beans (like `SurveyQuestions`) re-bind
**automatically** on refresh. Other beans opt in with `@RefreshScope`:

```java
@RefreshScope
@Component
class BreakerTuning {           // re-created on the next /actuator/refresh
    @Value("${survey.threshold}") int threshold;
}
```

`/actuator/refresh` returns the list of keys that changed.
The lab sets `eureka.client.refresh.enable: false` to keep registration stable;
restart clients when changing Eureka settings.

Notes:
Minute 48-50.
`@RefreshScope` beans are recreated on the next refresh so they pick up new values; `/actuator/refresh` is the actuator endpoint that triggers it. At fleet scale you'd fan this out with Spring Cloud Bus over RabbitMQ instead of curling each instance — nice foreshadowing of the messaging module.

Likely questions
- Q: Everything refreshable? A: `@ConfigurationProperties` re-binds automatically; other beans need `@RefreshScope`. Some things (ports, datasource) still want a restart.
- Q: Refresh 200 instances? A: Spring Cloud Bus broadcasts one `/actuator/busrefresh` over the broker. Same RabbitMQ we use for votes.

---

## Refresh the Whole Fleet: Spring Cloud Bus

`/actuator/refresh` hits **one** instance. Curling 200 of them is absurd.

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-bus-amqp</artifactId>
</dependency>
```

```bash
# hit ONE instance; every instance re-reads config:
curl -X POST localhost:8081/actuator/busrefresh
```

```text
POST /busrefresh ─▶ survey-service:8081
                      └─ RefreshRemoteApplicationEvent ─▶ [ RabbitMQ ]
                            ├─▶ survey-service:8082  (refreshes)
                            └─▶ results-service:8083 (refreshes)
```

Notes:
Minute 50 (optional bonus; needs RabbitMQ, up since setup).
Spring Cloud Bus links every instance over the same broker we use for votes. POST `/actuator/busrefresh` to any single node and it publishes a refresh event; every subscriber re-reads its configuration. This is the fleet-scale answer to the one-at-a-time refresh, and it's a nice preview of the messaging module — config management riding on the message bus.

Likely questions
- Q: Does the Config Server push changes automatically? A: Not by itself — Bus still needs a trigger (`/busrefresh`, or a `/monitor` webhook from your git host). The broadcast is the automatic part.
- Q: Same broker as votes? A: Yes, RabbitMQ. Bus and Stream can share it; in production you might separate them.

---

## Module 1 Checkpoint

You can now:

- run a **Config Server** with `@EnableConfigServer` + a backend;
- turn any app into a **client** with one `spring.config.import`;
- serve environment-specific values with **no rebuild**;
- bind remote config to **typed** `@ConfigurationProperties`;
- **refresh** one service with `/actuator/refresh`, or the **whole fleet** with Bus;
- keep secrets out of the clear with **`{cipher}`** encryption / Vault.

**Next: the services can read config — but how do they find each other?**

Notes:
Minute 50.
Hand off to Discovery. We've been hardcoding `localhost:8888` and `localhost:8761`; discovery removes the last hardcoded hosts. Keep eureka-server and config-server running.

Likely questions
- Q: Which clients refresh? A: The survey and results services import remote config; gateway and UI use local config.
