<!-- .slide: data-background-color="#191e1e" -->

# Module 2

## Service Discovery

Spring Cloud Netflix Eureka · ~25 min

Notes:
Minute 50.
Config removed values from the jar. Discovery removes the last hardcoded thing: the location of other services. Keep eureka-server and config-server running from the previous module.

Likely questions
- Q: Does discovery replace config? A: No; it resolves instances while config supplies settings.

---

## The Problem: Where Is results-service?

```java
// Yesterday's answer:
restClient.get().uri("http://192.168.1.42:8083/burnt-ends")...
```

- Instances come and go (autoscaling, crashes, deploys).
- IPs change every restart in the cloud.
- Two survey-service instances — which one gets the call?
- Hardcoding a host breaks the moment you scale past one.

> You should call a service by **name**, not by address.

Notes:
Minute 50-53.
Everyone has hardcoded a host and paid for it. In a dynamic environment the address is the least stable thing about a service. We want to call `results-service` and let something else resolve "which instance, right now."

Likely questions
- Q: Isn't this what DNS/Kubernetes does? A: Yes — k8s Services are discovery. Eureka is the Spring-native, platform-agnostic version, and it also carries per-instance metadata and health. On k8s you might use its DNS instead; the client code we write is identical.

---

## Eureka: A Live Phone Book

```text
   register (I'm survey-service @ 10.0.0.7:8081)
        │                    ┌────────────────┐
   ┌────▼─────┐   heartbeat  │  eureka-server │  :8761
   │ services │─────────────▶│    registry    │
   └────▲─────┘              └────────────────┘
        │  fetch registry (who is results-service?)
```

- Services **register** on startup and **heartbeat** to stay listed.
- Clients **fetch** the registry and cache it.
- Miss heartbeats → evicted. No config change needed.

Notes:
Minute 53-56.
Two flows: registration (server-ward) and discovery (client-ward, cached locally). The heartbeat is the liveness signal. This is eventually-consistent on purpose — clients hold a local cache so a registry blip doesn't stop traffic.

Likely questions
- Q: Is Eureka a single point of failure? A: Run a peer-aware cluster in prod. And because clients cache, a brief registry outage doesn't halt calls.
- Q: How fast is eviction? A: Tunable via heartbeat/lease intervals. Defaults favor stability over instant removal.

---

## The Eureka Server

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false   # the registry doesn't register with itself
    fetch-registry: false
```

Dashboard: **http://localhost:8761**

Notes:
Minute 56-58.
Same shape as the Config Server: one annotation, one dependency. It turns those two client flags off because the registry is not itself a client. The dashboard is genuinely useful on stage — it's the live picture of the system.

Likely questions
- Q: Why disable self-registration? A: This standalone registry does not need to register itself.

---

## Registering a Client

Add the dependency:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Point it at the registry (delivered centrally by the Config Server!):

```yaml
# config-repo/application.yml — inherited by both config clients
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true
```

Notes:
Minute 58-61.
The dependency is enough to auto-register — no `@EnableDiscoveryClient` needed on modern Spring Cloud. Notice where the Eureka URL lives: in `config-repo/application.yml`, so the Config Server hands survey-service and results-service their registry location. Gateway, UI, and Config Server configure their registry URL locally. The two Spring Cloud patterns already compose.

Likely questions
- Q: Do I still need `@EnableDiscoveryClient`? A: No, it's implied by the starter. You'll still see it in older code; harmless, just redundant.
- Q: `prefer-ip-address`? A: Register by IP instead of hostname — friendlier on laptops and containers where hostnames don't resolve.

---

## Discover + Load Balance

Call by **name** with a load-balanced client:

```java
@Bean
@LoadBalanced
RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
    return configurer.configure(RestClient.builder());
}

// for regular non-loadbalanced clients (such as eureka-client)
@Bean
@Primary
RestClient.Builder defaultRestClientBuilder(RestClientBuilderConfigurer configurer) {
   return configurer.configure(RestClient.builder());
}
```

```java
this.restClient = loadBalancedRestClientBuilder
        .baseUrl("http://results-service")   // ← a service id, not a host
        .build();
```

`@LoadBalanced` resolves `results-service` via Eureka and **round-robins** across its instances.

Notes:
Minute 61-64.
This is the client-side of discovery, and where Spring Cloud LoadBalancer enters. `@LoadBalanced` teaches the builder to treat the host segment as a Eureka service id. No separate load balancer box — the client picks an instance from its cached registry. `lb://results-service` is the same idea inside the gateway's route config.

Likely questions
- Q: Client-side vs server-side LB? A: This is client-side: the caller chooses the instance. No extra network hop, no central bottleneck.
- Q: Round-robin only? A: Default. Pluggable — you can weight, zone-prefer, or write your own.

---

## Declarative Clients (the modern Feign)

Prefer an interface over hand-written HTTP? Describe the API, let Spring implement it:

```java
@HttpExchange
public interface TallyClient {
    @GetExchange("/{questionId}")
    TallyView tally(@PathVariable String questionId);
}
```

```java
@SpringBootApplication
@ImportHttpServices(group = "results-service", types = TallyClient.class)
public class SurveyServiceApplication {
	// ... main, etc...
}
```

Notes:
Minute 63-65 (optional — skip if pressed for time).
This is the modern successor to Netflix Feign: Spring's `@HttpExchange` HTTP interface clients, built into Spring Framework 7 — no extra dependency. Spring Cloud derives the load-balanced destination from the HTTP service group name `results-service`. Boot configures that group’s client; the explicit builders on the previous slide demonstrate imperative clients. Use Boot’s builder configurer to preserve tracing. In the lab, `ResultsClient` uses exactly this `TallyClient`, and the next module wraps it in Resilience4j.

Likely questions
- Q: Is this Spring Cloud OpenFeign? A: No — OpenFeign still exists, but `@HttpExchange` is the framework-native, dependency-free option and the current recommendation. Same declarative feel.
- Q: Does load balancing still work through the interface? A: Yes — the interface is just a proxy over the load-balanced RestClient; `http://results-service` resolves through Eureka.

---

## Exercise 2 — Scale and Balance (12 min)

Run a **second** survey-service, then watch discovery work:

```bash
# instance #1 already runs on 8081; add a second on 8082
# from labs/bbq-wednesday
(cd survey-service && SERVER_PORT=8082 ../mvnw spring-boot:run) &
(cd results-service && ../mvnw spring-boot:run) &   # :8083
(cd gateway && ../mvnw spring-boot:run) &           # :8080; Redis must be running
```

1. Open **http://localhost:8761** — see `SURVEY-SERVICE` with **two** instances.
2. Hit the discovery-driven call repeatedly:
   `for i in 1 2 3 4; do curl -s localhost:8080/survey-service/tally/burnt-ends; echo; done`
3. Watch both survey-service logs — the calls alternate.

Notes:
Minute 64-72.
Two goals: see multiple instances in the registry, and see client-side load balancing spread traffic. The `/tally` call goes survey-service → results-service by name, so you're exercising registration AND discovery in one request. Start the gateway for this exercise; it is the load-balancing caller. Wait for both instances to appear in Eureka and for the gateway registry cache to refresh (up to about a minute). Direct calls to 8081/8082 do not demonstrate load balancing.

Likely questions
- Q: Only one instance shows? A: Give heartbeats a few seconds, and confirm the second used a different port. `prefer-ip-address` avoids hostname clashes.
- Q: Calls don't alternate? A: The client cache refreshes on an interval; wait a beat, then retry. Confirm both are UP in the dashboard.

---

## Answer 2 — Names, Not Addresses

The Eureka dashboard shows:

```text
Application        Instances
SURVEY-SERVICE     2   (…:8081, …:8082)   UP
RESULTS-SERVICE    1   (…:8083)           UP
CONFIG-SERVER      1                      UP
GATEWAY            1                      UP
```

Not one line of code named a host or a port. `survey-service`
asked for `results-service`; Eureka + LoadBalancer did the rest.

Notes:
Minute 72-74.
The whole system now refers to services by name. Instances can appear, disappear, and move; callers never change. This is the property that makes rolling deploys and autoscaling non-events.

Likely questions
- Q: What if results-service is down when survey-service calls? A: Great question — that's the entire next module. Discovery finds instances; it does nothing about a slow or failing one.

---

## You Have Alternatives

Eureka + Config Server are one implementation of two abstractions:

| Backend | Discovery | Config |
|---|:---:|:---:|
| **Netflix Eureka** + Config Server | ✅ | ✅ |
| **HashiCorp Consul** | ✅ | ✅ |
| **Apache Zookeeper** | ✅ | ✅ |
| **Kubernetes** (Services + ConfigMaps/Secrets) | ✅ | ✅ |

Your code barely changes — `DiscoveryClient`, `@LoadBalanced`, and
`spring.config.import` stay the same. **Swap the starter, not the app.**

Notes:
Minute 74 (optional bonus).
The point: Spring Cloud is a set of abstractions with pluggable backends. On a bare VM or a mixed environment, Eureka + Config Server are a great default. On Kubernetes you might drop Eureka for k8s-native Services and Config Server for ConfigMaps/Secrets via Spring Cloud Kubernetes — and your `DiscoveryClient`/`lb://` code is unchanged. Consul and Zookeeper cover both concerns if you prefer one system.

Likely questions
- Q: Eureka or Kubernetes discovery? A: On k8s, its native Services are the pragmatic choice. Off k8s, or when you want per-instance metadata and a simple story, Eureka shines. The client code is portable either way.
- Q: Why teach Eureka then? A: It's explicit and platform-agnostic — you SEE the registry. Once the concept lands, the k8s version is a config swap.

---

## Module 2 Checkpoint

You can now:

- run a **Eureka** registry with `@EnableEurekaServer`;
- **auto-register** a service with just the client starter;
- deliver the registry URL itself **through Config Server**;
- call services **by name** with a `@LoadBalanced` client;
- **scale** to N instances with zero caller changes.

**Break — 15 minutes. Then: what happens when a service fails?**

Notes:
Minute 74-75, then BREAK (75-90).
Leave eureka-server and config-server running over the break. When we return, results-service becomes unreliable on purpose and we make survey-service survive it. Before the break, make sure everyone sees two survey-service instances in the dashboard.

Likely questions
- Q: Can discovery hide downstream failure? A: No; the next module adds timeouts, retries, and fallback.
