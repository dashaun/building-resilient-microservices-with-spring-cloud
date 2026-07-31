package com.bbqwednesday.survey.controller;

import com.bbqwednesday.survey.client.ResultsClient;
import com.bbqwednesday.survey.client.TallyView;
import com.bbqwednesday.survey.model.SurveyQuestions;
import com.bbqwednesday.survey.model.SurveyVote;
import com.bbqwednesday.survey.service.SurveyService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/")
public class SurveyController {

    private static final Logger log = LoggerFactory.getLogger(SurveyController.class);

    private final SurveyService surveyService;
    private final ResultsClient resultsClient;

    @Value("${server.port}")
    private int serverPort;

    public SurveyController(SurveyService surveyService, ResultsClient resultsClient) {
        this.surveyService = surveyService;
        this.resultsClient = resultsClient;
    }

    @GetMapping("/questions")
    public List<SurveyQuestions.Question> questions() {
        log.info("[survey-service:{}] serving questions", serverPort);
        return surveyService.getQuestions();
    }

    // Throttled: Resilience4j RateLimiter caps accepted votes per second.
    @RateLimiter(name = "submitVote", fallbackMethod = "tooManyVotes")
    @PostMapping("/submit")
    public ResponseEntity<SurveyVote> submit(@RequestBody SurveyVote vote) {
        log.info("[survey-service:{}] vote {} -> {}", serverPort,
                vote.getQuestionId(), vote.getAnswer());
        return ResponseEntity.ok(surveyService.recordVote(vote));
    }

    ResponseEntity<SurveyVote> tooManyVotes(SurveyVote vote, Throwable t) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Slow down — BBQ Wednesday is popular");
    }

    // Resilient synchronous read: never fails even if results-service is down.
    @GetMapping("/tally/{questionId}")
    public TallyView tally(@PathVariable String questionId) {
        return resultsClient.currentTally(questionId);
    }
}
