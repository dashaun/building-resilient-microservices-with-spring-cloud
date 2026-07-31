package com.bbqwednesday.survey.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "survey_votes")
public class SurveyVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String questionId;

    @Column(nullable = false)
    private String answer;

    @Column(nullable = false)
    private Instant timestamp;

    private String voterId;

    public SurveyVote() {
        this.timestamp = Instant.now();
    }

    public SurveyVote(String questionId, String answer, String voterId) {
        this();
        this.questionId = questionId;
        this.answer = answer;
        this.voterId = voterId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getVoterId() { return voterId; }
    public void setVoterId(String voterId) { this.voterId = voterId; }
}
