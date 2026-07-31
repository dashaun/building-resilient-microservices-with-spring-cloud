package com.bbqwednesday.survey.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bound from the {@code survey.questions[*]} properties the Config Server
 * delivers. Change a question in config-repo, POST /actuator/refresh, done.
 */
@Component
@ConfigurationProperties(prefix = "survey")
public class SurveyQuestions {

    private List<Question> questions;

    public static class Question {
        private String id;
        private String text;
        private List<String> answers;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public List<String> getAnswers() { return answers; }
        public void setAnswers(List<String> answers) { this.answers = answers; }
    }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}
