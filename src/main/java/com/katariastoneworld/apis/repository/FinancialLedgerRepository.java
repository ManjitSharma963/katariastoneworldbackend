package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialLedgerRepository extends JpaRepository<FinancialLedgerEntry, Long> {

    Optional<FinancialLedgerEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to")
    BigDecimal sumAmountByLocationAndDateRange(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(l.inHandAmount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to")
    BigDecimal sumInHandByLocationAndDateRange(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.paymentMode = :mode")
    BigDecimal sumAmountByLocationDateRangeAndMode(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("mode") BillPaymentMode mode);

    List<FinancialLedgerEntry> findByLocationAndEventDateBetween(String location, LocalDate from, LocalDate to);

    /** In-hand portion of bill payments still present in ledger (deleted bill payments remove their row). */
    @Query("SELECT COALESCE(SUM(l.inHandAmount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventType = :eventType "
            + "AND l.eventDate >= :from AND l.eventDate <= :to")
    BigDecimal sumInHandBillPaymentsByLocationAndDateRange(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("eventType") FinancialLedgerEntry.EventType eventType);
}
