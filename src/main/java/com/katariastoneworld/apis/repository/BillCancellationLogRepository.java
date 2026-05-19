package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillCancellationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BillCancellationLogRepository extends JpaRepository<BillCancellationLog, Long> {

    List<BillCancellationLog> findByLocationAndBillDateBetweenOrderByCancelledAtDesc(
            String location, LocalDate billDateFrom, LocalDate billDateTo);

    /**
     * Bills whose invoice date OR cancellation timestamp falls in the period (inclusive days).
     */
    @Query("""
            SELECT l FROM BillCancellationLog l
            WHERE l.location = :location
              AND (
                    (l.billDate >= :from AND l.billDate <= :to)
                 OR (l.cancelledAt >= :cancelledFrom AND l.cancelledAt < :cancelledToExclusive)
              )
            ORDER BY l.cancelledAt DESC
            """)
    List<BillCancellationLog> findForPeriodByBillDateOrCancelledAt(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("cancelledFrom") LocalDateTime cancelledFrom,
            @Param("cancelledToExclusive") LocalDateTime cancelledToExclusive);
}
