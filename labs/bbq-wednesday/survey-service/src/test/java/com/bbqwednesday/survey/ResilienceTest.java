package com.bbqwednesday.survey;

import com.bbqwednesday.survey.client.ResultsClient;
import com.bbqwednesday.survey.client.TallyClient;
import com.bbqwednesday.survey.client.TallyView;
import com.bbqwednesday.survey.controller.SurveyController;
import com.bbqwednesday.survey.model.SurveyVote;
import com.bbqwednesday.survey.service.SurveyService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.bus.enabled=false",
    "spring.config.import=optional:file:../config-repo/survey-service.yml"
})
class ResilienceTest {
    @MockitoBean TallyClient tallyClient;
    @MockitoBean SurveyService surveyService;
    @Autowired ResultsClient resultsClient;
    @Autowired SurveyController controller;
    @Autowired CircuitBreakerRegistry breakers;

    @BeforeEach void resetBreaker() {
        breakers.circuitBreaker("resultsService").reset();
    }

    @Test void retriesTransientErrorsBeforeReturningTheRealTally() {
        var tally = new TallyView("sauce", Map.of("Spicy", 1), 1);
        when(tallyClient.tally("sauce"))
                .thenThrow(new ResourceAccessException("temporary"))
                .thenThrow(new ResourceAccessException("temporary"))
                .thenReturn(tally);
        assertThat(resultsClient.currentTally("sauce")).isEqualTo(tally);
        verify(tallyClient, times(3)).tally("sauce");
        assertThat(breakers.circuitBreaker("resultsService").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test void repeatedFailuresOpenBreakerAndStopRemoteTraffic() {
        when(tallyClient.tally("sauce")).thenThrow(new ResourceAccessException("down"));
        for (int i = 0; i < 6; i++) {
            assertThat(resultsClient.currentTally("sauce")).isEqualTo(TallyView.empty("sauce"));
        }
        verify(tallyClient, times(15)).tally("sauce"); // five failed calls, three attempts each
        assertThat(breakers.circuitBreaker("resultsService").getState().name()).isEqualTo("OPEN");
    }

    @Test void nonRetryableFailuresFallBackWithoutRetrying() {
        when(tallyClient.tally("sauce")).thenThrow(new IllegalArgumentException("bad request"));
        assertThat(resultsClient.currentTally("sauce")).isEqualTo(TallyView.empty("sauce"));
        verify(tallyClient).tally("sauce");
    }

    @Test void storageFailureIsNotMisreportedAsRateLimiting() {
        var vote = new SurveyVote("sauce", "Spicy", "test");
        when(surveyService.recordVote(vote)).thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> controller.submit(vote))
                .isInstanceOf(IllegalStateException.class).hasMessage("storage unavailable");
    }
}
