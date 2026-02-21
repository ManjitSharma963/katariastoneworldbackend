package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.DailyBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyBudgetRepository extends JpaRepository<DailyBudget, Long> {
    Optional<DailyBudget> findByLocation(String location);
}
