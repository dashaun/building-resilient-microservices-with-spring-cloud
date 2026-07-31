package com.bbqwednesday.survey.client;

import java.util.Map;

/** The results-service payload, as survey-service sees it. */
public record TallyView(
    String questionId,
    Map<String, Integer> answerCounts,
    int totalResponses
) {
    public static TallyView empty(String questionId) {
        return new TallyView(questionId, Map.of(), 0);
    }
}
