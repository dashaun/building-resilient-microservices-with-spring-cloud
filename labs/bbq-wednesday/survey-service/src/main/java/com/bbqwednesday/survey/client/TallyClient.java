package com.bbqwednesday.survey.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative HTTP client — the modern successor to Netflix Feign.
 *
 * You describe the remote API as a Java interface; Spring generates the
 * implementation. Backed by a @LoadBalanced RestClient, the base URL is a
 * Eureka service id ("results-service"), so calls are discovered + balanced.
 * ResultsClient wraps this with Resilience4j.
 */
@HttpExchange
public interface TallyClient {

    @GetExchange("/{questionId}")
    TallyView tally(@PathVariable String questionId);
}
