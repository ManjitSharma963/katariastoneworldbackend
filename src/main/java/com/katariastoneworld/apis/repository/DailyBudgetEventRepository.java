package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.DailyBudgetEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyBudgetEventRepository extends JpaRepository<DailyBudgetEvent, Long> {

    List<DailyBudgetEvent> findByLocationAndDateBetweenOrderByCreatedAtDesc(
            String location,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    Optional<DailyBudgetEvent> findFirstByLocationAndDateOrderByCreatedAtDesc(String location, LocalDate date);

    /** Earliest event that day (for start-of-day opening balance). */
    Optional<DailyBudgetEvent> findFirstByLocationAndDateOrderByCreatedAtAsc(String location, LocalDate date);

    @Query("SELECT COALESCE(SUM(e.spentAmount), 0) FROM DailyBudgetEvent e WHERE e.location = :location "
            + "AND e.date >= :fromDate AND e.date <= :toDate "
            + "AND e.eventType IN ('EXPENSE_DEBIT', 'EXPENSE_CREDIT')")
    BigDecimal sumExpenseSpentFromEvents(
            @Param("location") String location,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}

