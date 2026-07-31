package com.bbqwednesday.survey.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the current tally from results-service and makes that call resilient.
 *
 * The HTTP mechanics live in the declarative {@link TallyClient}; this class
 * adds the resilience policy around it:
 *   @Retry         — transient blips get a few quick retries
 *   @CircuitBreaker— repeated failures OPEN the breaker and stop hammering it
 *   fallbackMethod — callers still get an answer (an empty tally) instead of a 500
 */
@Component
public class ResultsClient {

    private static final Logger log = LoggerFactory.getLogger(ResultsClient.class);

    private final TallyClient tallyClient;

    public ResultsClient(TallyClient tallyClient) {
        this.tallyClient = tallyClient;
    }

    @CircuitBreaker(name = "resultsService", fallbackMethod = "emptyTally")
    @Retry(name = "resultsService")
    public TallyView currentTally(String questionId) {
        log.debug("Calling results-service for tally of '{}'", questionId);
        return tallyClient.tally(questionId);
    }

    // Signature = original params + the Throwable that tripped the fallback.
    TallyView emptyTally(String questionId, Throwable t) {
        log.warn("results-service unavailable for '{}' ({}). Serving empty tally.",
                questionId, t.toString());
        return TallyView.empty(questionId);
    }
}
