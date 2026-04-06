package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialLedgerRepository extends JpaRepository<FinancialLedgerEntry, Long>,
        JpaSpecificationExecutor<FinancialLedgerEntry> {

    Optional<FinancialLedgerEntry> findBySourceTypeAndSourceIdAndIsDeletedFalse(String sourceType, String sourceId);

    /** Includes soft-deleted rows (for diagnostics). */
    List<FinancialLedgerEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "AND l.entryType = :entryType")
    BigDecimal sumAmountByLocationDateRangeAndEntryType(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("entryType") LedgerEntryType entryType);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.DEBIT "
            + "AND l.sourceType = :sourceType")
    BigDecimal sumDebitByLocationDateRangeAndSourceType(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("sourceType") String sourceType);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate = :d AND l.isDeleted = false AND l.entryType = :entryType")
    BigDecimal sumAmountByLocationAndDateAndEntryType(@Param("loc") String location, @Param("d") LocalDate date,
            @Param("entryType") LedgerEntryType entryType);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT "
            + "AND (l.paymentMode = com.katariastoneworld.apis.entity.BillPaymentMode.CASH "
            + "OR l.paymentMode = com.katariastoneworld.apis.entity.BillPaymentMode.UPI)")
    BigDecimal sumInHandCreditByLocationAndDateRange(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT "
            + "AND l.paymentMode = :mode")
    BigDecimal sumCreditByLocationDateRangeAndMode(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("mode") BillPaymentMode mode);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.isDeleted = false "
            + "AND l.location = :loc AND l.eventDate >= :from AND l.eventDate <= :to "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT "
            + "AND l.sourceType = 'BILL_PAYMENT'")
    BigDecimal sumBillPaymentCreditsByLocationAndDateRange(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Bill payments taken in cash or UPI for one day (matches legacy "budget goes up" behaviour). */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.isDeleted = false "
            + "AND l.location = :loc AND l.eventDate = :d "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT "
            + "AND l.sourceType = 'BILL_PAYMENT' "
            + "AND (l.paymentMode = com.katariastoneworld.apis.entity.BillPaymentMode.CASH "
            + "OR l.paymentMode = com.katariastoneworld.apis.entity.BillPaymentMode.UPI)")
    BigDecimal sumBillPaymentCreditsCashUpiByLocationAndDate(@Param("loc") String location, @Param("d") LocalDate d);

    List<FinancialLedgerEntry> findByLocationAndEventDateBetweenAndIsDeletedFalse(String location, LocalDate from,
            LocalDate to);

    @Query("SELECT l FROM FinancialLedgerEntry l WHERE l.isDeleted = false AND l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT")
    List<FinancialLedgerEntry> findCreditsByLocationAndDateRange(@Param("loc") String location,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT l.referenceType, COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.DEBIT "
            + "GROUP BY l.referenceType")
    List<Object[]> sumDebitGroupedByReferenceType(@Param("loc") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT l.paymentMode, l.entryType, COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l "
            + "WHERE l.location = :loc AND l.eventDate >= :from AND l.eventDate <= :to AND l.isDeleted = false "
            + "GROUP BY l.paymentMode, l.entryType")
    List<Object[]> sumAmountGroupedByPaymentModeAndEntryType(@Param("loc") String location,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT l.paymentMode, COALESCE(SUM(l.amount), 0) FROM FinancialLedgerEntry l WHERE l.location = :loc "
            + "AND l.eventDate = :d AND l.isDeleted = false "
            + "AND l.entryType = com.katariastoneworld.apis.entity.LedgerEntryType.CREDIT "
            + "GROUP BY l.paymentMode")
    List<Object[]> sumCreditGroupedByPaymentModeForDate(@Param("loc") String location, @Param("d") LocalDate date);
}
