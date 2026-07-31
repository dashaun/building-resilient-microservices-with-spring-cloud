package com.bbqwednesday.results.controller;

import com.bbqwednesday.results.model.Tally;
import com.bbqwednesday.results.service.ResultsAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class ResultsController {

    private final ResultsAggregator aggregator;

    public ResultsController(ResultsAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/{questionId}")
    public Tally getTally(@PathVariable String questionId) {
        return aggregator.getTally(questionId)
                .orElseGet(() -> new Tally(questionId));
    }
}
