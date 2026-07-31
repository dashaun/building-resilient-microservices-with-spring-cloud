package com.bbqwednesday.results.event;

import java.time.Instant;

/** Must be structurally compatible with the event survey-service publishes. */
public record SurveyVoteEvent(
    String questionId,
    String answer,
    Instant timestamp,
    String voterId
) {
}
