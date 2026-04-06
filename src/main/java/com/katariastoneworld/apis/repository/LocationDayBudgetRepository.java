package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.LocationDayBudget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface LocationDayBudgetRepository extends JpaRepository<LocationDayBudget, Long> {

    Optional<LocationDayBudget> findByBudgetDateAndLocation(LocalDate budgetDate, String location);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM LocationDayBudget d WHERE d.budgetDate = :date AND d.location = :location")
    Optional<LocationDayBudget> findByBudgetDateAndLocationForUpdate(@Param("date") LocalDate date,
            @Param("location") String location);

    Optional<LocationDayBudget> findFirstByLocationAndBudgetDateBeforeOrderByBudgetDateDesc(String location,
            LocalDate before);
}
