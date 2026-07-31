package com.bbqwednesday.survey.event;

import java.time.Instant;

/**
 * Published to RabbitMQ (destination {@code bbq-votes}) every time a vote is
 * accepted. results-service consumes it to keep the live tally current.
 */
public record SurveyVoteEvent(
    String questionId,
    String answer,
    Instant timestamp,
    String voterId
) {
}
