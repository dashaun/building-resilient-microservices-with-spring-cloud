package com.bbqwednesday.results.repository;

import com.bbqwednesday.results.model.Tally;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TallyRepository extends JpaRepository<Tally, String> {
}
