package com.bbqwednesday.results.service;

import com.bbqwednesday.results.event.SurveyVoteEvent;
import com.bbqwednesday.results.model.Tally;
import com.bbqwednesday.results.repository.TallyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Consumer;

@Service
public class ResultsAggregator {

    private static final Logger log = LoggerFactory.getLogger(ResultsAggregator.class);

    private final TallyRepository repository;

    public ResultsAggregator(TallyRepository repository) {
        this.repository = repository;
    }

    /**
     * A java.util.function.Consumer bean IS the message handler. Spring Cloud
     * Stream binds it to surveyVote-in-0 (see application.yaml). No broker API,
     * no @RabbitListener — just a function.
     */
    @Bean
    public Consumer<SurveyVoteEvent> surveyVote() {
        return event -> {
            log.info("Received SurveyVoteEvent: {} -> {}", event.questionId(), event.answer());

            Tally tally = repository.findById(event.questionId())
                    .orElseGet(() -> new Tally(event.questionId()));
            tally.incrementAnswer(event.answer());

            Tally saved = repository.save(tally);
            log.info("Tally for {}: {} total votes", saved.getQuestionId(), saved.getTotalResponses());
        };
    }

    public Optional<Tally> getTally(String questionId) {
        return repository.findById(questionId);
    }
}
