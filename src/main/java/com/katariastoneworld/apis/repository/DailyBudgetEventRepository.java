package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.DailyBudgetEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyBudgetEventRepository extends JpaRepository<DailyBudgetEvent, Long> {

    List<DailyBudgetEvent> findByLocationAndDateBetweenOrderByCreatedAtDesc(
            String location,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );
}

