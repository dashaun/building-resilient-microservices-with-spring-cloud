<!-- .slide: data-background-color="#191e1e" -->

# Module 5

## API Gateway & Security

Spring Cloud Gateway + Spring Security · ~40 min

Notes:
Minute 175 (after the break).
Everything so far exposed its own port. That's fine inside the cluster and wrong at the edge. The gateway is the single public front door — and the one place to solve routing, security, and rate limiting once. Start Redis if you haven't: `docker compose up -d redis`.

Likely questions
- Q: Is gateway already running? A: Yes, from Exercise 2; this module explains and exercises its filters.

---

## The Problem: N Services, N Doors

```text
browser ─▶ survey-service:8081 ?  results-service:8083 ?  survey-ui:8091 ?
           each with its own CORS, auth, TLS, rate limits …
```

- Clients must know every host and port.
- Security, throttling, CORS re-implemented **per service**.
- No single place for cross-cutting concerns.

> One front door. Cross-cutting concerns solved **once**, at the edge.

Notes:
Minute 175-178.
The anti-pattern: every service is publicly reachable and each re-invents auth and throttling slightly differently. The gateway collapses that to one address and one place to enforce policy. Downstream services get simpler because the edge did the hard part.

Likely questions
- Q: Isn't the gateway a single point of failure / bottleneck? A: Run several behind a load balancer. It's stateless (Redis holds rate-limit state), so it scales horizontally.

---

## Spring Cloud Gateway: Routes

A route = **predicate** (match) + **filters** (transform) + **uri** (target):

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: results-service
              uri: lb://results-service      # ← discovery + load balance
              predicates:
                - Path=/results-service/**
              filters:
                - StripPrefix=1              # drop /results-service
```

`GET /results-service/burnt-ends` → `results-service` `GET /burnt-ends`.

Notes:
Minute 178-182.
Note the Boot 4 / 2025.1 namespace: `spring.cloud.gateway.server.webflux.*`. `lb://` reuses everything from the Discovery module — the gateway load-balances across instances via Eureka. Predicates can match path, method, header, host, time; filters can strip, add headers, rewrite, rate-limit, circuit-break.

Likely questions
- Q: `lb://` vs a URL? A: `lb://` resolves through Eureka + LoadBalancer. Same client-side balancing as Module 2, now at the edge.
- Q: WebFlux? A: The reactive gateway is non-blocking — built for high-fanout edge traffic.

---

## Cross-Cutting #1: Circuit Breaker at the Edge

Wrap a whole route in a breaker — degrade before the request even lands:

```yaml
- id: survey-service
  uri: lb://survey-service
  predicates:
    - Path=/survey-service/**
  filters:
    - StripPrefix=1
    - name: CircuitBreaker
      args:
        name: surveyService
        fallbackUri: forward:/fallback/survey
```

```java
@RequestMapping("/fallback/survey")
ResponseEntity<?> surveyFallback() {
    return ResponseEntity.status(503).body(Map.of(
        "status", 503,
        "message", "The pit is backed up — survey-service is unavailable. Try again shortly."));
}
```

Notes:
Minute 182-185.
Resilience4j again, now reactive and at the edge (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`). If survey-service is unhealthy, the gateway short-circuits to a local fallback controller — the user gets a friendly 503 instead of a hang. Defense in depth: breakers in the service AND at the gateway.

Likely questions
- Q: Duplicate of Module 3's breaker? A: Complementary. The edge breaker protects clients from a dead service wholesale; the in-service breaker protects survey-service from its own dependency.

---

## Cross-Cutting #2: Security Belongs Here

<svg class="dg" viewBox="0 0 1000 405" xmlns="http://www.w3.org/2000/svg">
<defs><marker id="a-g-gw" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="#6db33f"/></marker></defs>
<rect class="n-plain" x="370" y="6" width="260" height="56" rx="12"/>
<text class="t-sm" x="500" y="31">client</text>
<text class="mono" x="500" y="51">Authorization: Bearer &#8230;</text>
<path class="flow" d="M500,62 V96" marker-end="url(#a-g-gw)"/>
<rect class="n-fill" x="200" y="102" width="600" height="96" rx="14"/>
<text class="t-on" x="500" y="143">gateway</text>
<text class="sub-on" x="500" y="171">:8080 &#183; validates the JWT once, at the edge</text>
<path class="flow" d="M500,198 V242" marker-end="url(#a-g-gw)"/>
<text class="lbl start" x="520" y="226">below this line, nobody re-validates</text>
<rect class="band" x="60" y="252" width="880" height="140" rx="16"/>
<text class="lbl" x="500" y="279">trusted network &#8212; downstream services stay simple</text>
<rect class="n-plain" x="90" y="296" width="260" height="72" rx="12"/>
<text class="t-sm" x="220" y="329">survey-service</text>
<text class="port" x="220" y="353">:8081 &#183; :8082</text>
<rect class="n-plain" x="370" y="296" width="260" height="72" rx="12"/>
<text class="t-sm" x="500" y="329">results-service</text>
<text class="port" x="500" y="353">:8083</text>
<rect class="n-plain" x="650" y="296" width="260" height="72" rx="12"/>
<text class="t-sm" x="780" y="329">survey-ui</text>
<text class="port" x="780" y="353">:8091</text>
</svg>

Authenticate **once**, at the front door. Downstream services don't
each re-implement auth — they trust the perimeter.

Notes:
Minute 185-188.
This is the security addition. The gateway + Spring Security is the natural home for authentication as a cross-cutting concern. We'll require a valid token to CAST a vote, while reads and the UI stay open. In production the same filter also handles TLS termination and header hygiene.

Likely questions
- Q: Is edge-only auth enough? A: For many systems, with a trusted internal network, yes. Zero-trust adds service-to-service auth too (mTLS / token relay). The gateway is where it starts.

---

## OAuth2 Resource Server (JWT)

The gateway validates a **JWT bearer token**; only voting requires it:

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        http
          .csrf(CsrfSpec::disable)
          .authorizeExchange(ex -> ex
            .pathMatchers(HttpMethod.POST, "/survey-service/submit").authenticated()
            .anyExchange().permitAll())            // UI, questions, results: open
          .oauth2ResourceServer(o -> o.jwt(withDefaults()));
        return http.build();
    }
}
```

Notes:
Minute 188-191.
"Resource server" = validate tokens, don't issue them. The whole policy is one filter chain: POST /submit needs a valid JWT; everything else is public. The reactive Spring Security API (`ServerHttpSecurity`, `@EnableWebFluxSecurity`) matches the WebFlux gateway.

Likely questions
- Q: Why only protect submit? A: Teaching contrast — a public read path and a protected write path in one policy. Real apps lock down far more.
- Q: CSRF disabled? A: It's a token-based API, not cookie-session; CSRF protection targets the latter.

---

## Validating the Token

```java
@Bean
ReactiveJwtDecoder jwtDecoder(@Value("${bbq.jwt.secret}") String secret) {
    var key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
    return NimbusReactiveJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
}
```

- **Lab:** HS256 shared secret → fully offline.
- **Production:** delete this bean, set one property:

```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: https://idp/.well-known/jwks.json
```

Notes:
Minute 191-193.
The only reason we hand-build a decoder is to stay offline — a shared HMAC secret needs no identity provider. In production you point `jwk-set-uri` at your IdP (Okta, Entra, Keycloak, Cognito) and Spring fetches the public keys and validates RS256 automatically. The resource-server machinery is identical; only key management changes.

Likely questions
- Q: Is a shared secret production-grade? A: No — it's a lab shortcut. Asymmetric keys via `jwk-set-uri` are the real answer, and it's less code.

---

## A Dev Token (So We Can Vote)

Offline, we mint our own short-lived HS256 token:

```java
@GetMapping("/token")   // DEV ONLY — delete before production
Map<String,String> token() throws Exception {
    var claims = new JWTClaimsSet.Builder()
        .subject("bbq-fan").issuer("bbq-wednesday")
        .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
        .claim("scope", "vote").build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(secret));
    return Map.of("access_token", jwt.serialize(), "token_type", "Bearer");
}
```

Notes:
Minute 193-195.
In the real world your IdP issues tokens; here a tiny dev endpoint does, signed with the same secret the decoder trusts. Say it plainly: this belongs nowhere near production — it exists so 100 people can vote without standing up Keycloak.

Likely questions
- Q: The UI handles this how? A: vote.js fetches `/token`, then sends `Authorization: Bearer <token>` on submit. Reads need no token.

---

## Exercise 5a — Prove the Lock (10 min)

Start the gateway, then try to vote through it:

```bash
# gateway is already running from Exercise 2 (:8080)

# 1. no token → blocked:
curl -o /dev/null -w "%{http_code}\n" -X POST \
  localhost:8080/survey-service/submit -H 'Content-Type: application/json' \
  -d '{"questionId":"sauce","answer":"Spicy","voterId":"me"}'      # 401

# 2. get a token, vote again:
TOKEN=$(curl -s localhost:8080/token | sed 's/.*"access_token":"//;s/".*//')
curl -o /dev/null -w "%{http_code}\n" -X POST \
  localhost:8080/survey-service/submit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"questionId":"sauce","answer":"Spicy","voterId":"me"}'      # 200
```

Notes:
Minute 195-205.
The security demo: same request, blocked then allowed. Step 1 returns 401 because no bearer token. Step 2 fetches a token and the vote goes through the gateway to survey-service. Meanwhile reads (`/survey-service/questions`, `/results-service/**`) work with no token at all — show that too.

Likely questions
- Q: 200 even without a token? A: The route probably isn't matching the security matcher — confirm it's POST to exactly `/survey-service/submit`.
- Q: 500 on /token? A: The secret must be ≥ 32 bytes for HS256. Check `bbq.jwt.secret`.

---

## Answer 5a — One Door, One Policy

```text
POST /survey-service/submit           no token   → 401 Unauthorized
POST /survey-service/submit           Bearer JWT → 200 OK
GET  /survey-service/questions        (public)   → 200 OK
GET  /results-service/burnt-ends      (public)   → 200 OK
```

survey-service never checked a token. The **edge** did — once.

Notes:
Minute 205-207.
The payoff: authentication lives in exactly one place, and downstream code stays focused on BBQ, not JWTs. Adding a second protected service means one more matcher line — not another security stack.

Likely questions
- Q: Are service ports protected too? A: No. Edge-only authentication is a lab simplification; restrict direct access in production.

---

## Two Faces of Gateway Security

| | **Resource Server** (today) | **Login / SSO** |
|---|---|---|
| Role | *validates* tokens | *obtains* tokens |
| Client sends | a bearer JWT | nothing — gets redirected |
| Config | `oauth2ResourceServer().jwt()` | `oauth2Login()` + TokenRelay |
| Fits | APIs, SPAs, mobile | browser apps, human login |

```java
// SSO flavor: the gateway performs the login and relays the token
http.oauth2Login(withDefaults());        // redirect to the IdP
// + TokenRelay filter forwards the access token to downstream services
```

Notes:
Minute 207-209 (optional — concept only).
Two legitimate gateway security postures. We built a **resource server**: clients already hold a token and the gateway validates it — perfect for APIs and our JS front end. The other posture is **login/SSO**: the gateway is an OAuth2 *client*, it runs the redirect-based login against a real identity provider, establishes a session, and the **TokenRelay** filter forwards the access token to downstream services. We don't wire it in the lab because it needs a live IdP (Okta, Keycloak, Entra, Cognito) — which would break "fully offline" — but it's one `oauth2Login()` away.

Likely questions
- Q: Which do I want? A: A machine/API front door → resource server. A browser app where users log in → login/SSO, often both (login at the edge, resource-server checks deeper in).
- Q: What's TokenRelay? A: A built-in gateway filter that copies the user's access token onto proxied requests, so downstream services can act as resource servers too.

---

## Cross-Cutting #3: Rate Limiting at the Edge

Redis-backed token bucket, per caller — throttle before work begins:

```yaml
- id: survey-submit
  uri: lb://survey-service
  predicates:
    - Path=/survey-service/submit
    - Method=POST
  filters:
    - StripPrefix=1
    - name: RequestRateLimiter
      args:
        key-resolver: "#{@voterKeyResolver}"
        redis-rate-limiter.replenishRate: 5      # tokens/sec
        redis-rate-limiter.burstCapacity: 10
```

```java
@Bean
KeyResolver voterKeyResolver() {         // one bucket per client IP
    return ex -> Mono.just(ex.getRequest().getRemoteAddress()
                    .getAddress().getHostAddress());
}
```

Notes:
Minute 207-210.
Two rate limiters now, deliberately: Module 3's per-instance Resilience4j limiter, and this distributed one at the edge. Redis holds the buckets so the limit is enforced across every gateway replica. The `KeyResolver` decides the bucket key — per IP here, often per API key or per user in production.

Likely questions
- Q: Why Redis? A: Shared state. With three gateways, an in-memory limit would be 3× too loose. Redis makes "5/sec" mean 5/sec globally.
- Q: 429 body? A: Gateway returns 429 with `X-RateLimit-*` headers; customizable.

---

## Exercise 5b — Throttle at the Door (6 min)

```bash
TOKEN=$(curl -s localhost:8080/token | sed 's/.*"access_token":"//;s/".*//')
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "%{http_code} " -X POST \
    localhost:8080/survey-service/submit -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"questionId":"best-side","answer":"Cheesy corn","voterId":"me"}'
done ; echo
```

Expect `200`s and `429`s. Inspect `X-RateLimit-Remaining` response headers
(`curl -i`) to distinguish Redis edge limiting from the service limiter.

Notes:
Minute 210-213.
Warm up with one vote, wait two seconds, then run the loop. The exact sequence varies with timing and both service instances’ local limiters; an edge rejection has exhausted Redis rate-limit headers. This is the edge protecting the whole system, independent of any single service's own limiter. Redis must be running.

Likely questions
- Q: No 429s? A: Redis isn't reachable, or the loop is slow. Confirm `docker compose ps redis` and lower `replenishRate` to 2.

---

## Answer 5b — Identify the Limiter

```text
HTTP/1.1 429 Too Many Requests
X-RateLimit-Remaining: 0
X-RateLimit-Replenish-Rate: 5
X-RateLimit-Burst-Capacity: 10
```

These headers identify the Redis gateway bucket. A service-level `429`
can occur while the gateway still has tokens; both limits are active.
Exact counts depend on request timing and replenishment.

Notes:
Minute 213-214.
Inspect a rejected response with `curl -i`; status alone does not identify the limiter.

Likely questions
- Q: Why fewer than ten successes? A: Each survey instance also limits votes to five per second.

---

## Module 5 Checkpoint

You can now:

- route by predicate/filter to `lb://` services through **one** door;
- **circuit-break** whole routes with a friendly fallback;
- enforce **JWT auth** once, at the edge (OAuth2 Resource Server);
- keep reads public and **writes protected** with one filter chain;
- **rate-limit** per client with a Redis-backed token bucket.

**Last question: a request crossed five services — where did the time go?**

Notes:
Minute 213-215.
The gateway ties the earlier modules together at the edge. The teaser for tracing: we now have a real call chain — browser → gateway → survey-service → results-service, plus an async hop over RabbitMQ — and no way yet to see it end to end. That's the final module.

Likely questions
- Q: How do I distinguish the two limiters? A: Inspect the gateway X-RateLimit response headers.
