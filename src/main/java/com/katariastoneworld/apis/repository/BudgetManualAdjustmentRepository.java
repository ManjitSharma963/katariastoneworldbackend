package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BudgetManualAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BudgetManualAdjustmentRepository extends JpaRepository<BudgetManualAdjustment, Long> {

    List<BudgetManualAdjustment> findByLocationAndBudgetDateOrderByCreatedAtAsc(String location, LocalDate budgetDate);
}
