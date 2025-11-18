package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByLocation(String location);
    List<Expense> findByLocationAndDateBetween(String location, LocalDate startDate, LocalDate endDate);
    List<Expense> findByLocationAndType(String location, String type);
    List<Expense> findByLocationAndCategory(String location, String category);
}

