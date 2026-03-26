package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.CustomerAdvance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomerAdvanceRepository extends JpaRepository<CustomerAdvance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CustomerAdvance a WHERE a.customer.id = :customerId AND a.remainingAmount > :zero ORDER BY a.createdAt ASC, a.id ASC")
    List<CustomerAdvance> findEligibleForApplyLocked(@Param("customerId") Long customerId, @Param("zero") BigDecimal zero);

    List<CustomerAdvance> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    /** Sum of new advance/token amounts recorded in [start, end) for customers at this location. */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM CustomerAdvance a WHERE a.customer.location = :loc "
            + "AND a.createdAt >= :start AND a.createdAt < :end")
    BigDecimal sumDepositsForLocationAndCreatedAtRange(@Param("loc") String loc, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT a FROM CustomerAdvance a WHERE a.customer.location = :loc "
            + "AND a.createdAt >= :start AND a.createdAt < :end")
    List<CustomerAdvance> findDepositsForLocationAndCreatedAtRange(@Param("loc") String loc,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
