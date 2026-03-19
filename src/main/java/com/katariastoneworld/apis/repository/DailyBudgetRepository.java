package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.DailyBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyBudgetRepository extends JpaRepository<DailyBudget, Long> {
    /** Prefer location-scoped row (user_id NULL) so we use the same row as DB / UI. */
    Optional<DailyBudget> findFirstByLocationAndUserIdIsNull(String location);
    Optional<DailyBudget> findByLocation(String location);
    Optional<DailyBudget> findByUserIdAndLocation(Long userId, String location);
}
