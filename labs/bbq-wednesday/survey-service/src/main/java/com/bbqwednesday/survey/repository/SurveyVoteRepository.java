package com.bbqwednesday.survey.repository;

import com.bbqwednesday.survey.model.SurveyVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyVoteRepository extends JpaRepository<SurveyVote, Long> {

    List<SurveyVote> findByQuestionId(String questionId);

}
