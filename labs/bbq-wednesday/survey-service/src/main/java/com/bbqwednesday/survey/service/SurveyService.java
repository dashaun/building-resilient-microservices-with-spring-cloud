package com.bbqwednesday.survey.service;

import com.bbqwednesday.survey.event.SurveyVoteEvent;
import com.bbqwednesday.survey.model.SurveyQuestions;
import com.bbqwednesday.survey.model.SurveyVote;
import com.bbqwednesday.survey.repository.SurveyVoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    // Convention: <functionName>-out-0. Bound to destination bbq-votes in yaml.
    private static final String VOTE_OUT = "surveyVote-out-0";

    private final SurveyVoteRepository repository;
    private final StreamBridge streamBridge;
    private final SurveyQuestions surveyQuestions;

    public SurveyService(SurveyVoteRepository repository,
                         StreamBridge streamBridge,
                         SurveyQuestions surveyQuestions) {
        this.repository = repository;
        this.streamBridge = streamBridge;
        this.surveyQuestions = surveyQuestions;
    }

    public SurveyVote recordVote(SurveyVote vote) {
        SurveyVote saved = repository.save(vote);

        // Fire-and-forget: the vote is persisted, the tally updates asynchronously.
        SurveyVoteEvent event = new SurveyVoteEvent(
            saved.getQuestionId(),
            saved.getAnswer(),
            saved.getTimestamp(),
            saved.getVoterId());
        if (!streamBridge.send(VOTE_OUT, event)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Vote publication failed; tally was not updated");
        }
        log.info("Published SurveyVoteEvent to '{}': {} -> {}",
                VOTE_OUT, event.questionId(), event.answer());

        return saved;
    }

    public List<SurveyQuestions.Question> getQuestions() {
        return surveyQuestions.getQuestions();
    }

    public List<SurveyVote> getVotes(String questionId) {
        return repository.findByQuestionId(questionId);
    }
}
