package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.UnifiedFinancialLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface UnifiedFinancialLedgerRepository extends JpaRepository<UnifiedFinancialLedgerEntry, Long> {

    Optional<UnifiedFinancialLedgerEntry> findByLocationAndSourceAndReferenceId(String location, String source, Long referenceId);

    /**
     * Net position for payment rails: {@code SUM(CREDIT amount) - SUM(DEBIT amount)} for the given modes.
     * Phase 3 balance summary (replaces ad-hoc {@code daily_budget_events} replay for cash/bank totals).
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.txnType = :credit THEN e.amount ELSE -e.amount END), 0) "
            + "FROM UnifiedFinancialLedgerEntry e "
            + "WHERE e.location = :location AND e.paymentMode IN :modes")
    BigDecimal sumNetSignedByLocationAndPaymentModes(
            @Param("location") String location,
            @Param("credit") LedgerTransactionType credit,
            @Param("modes") Collection<LedgerPaymentMode> modes);

    Page<UnifiedFinancialLedgerEntry> findByLocationAndTxnDateBetweenOrderByTxnDateDescCreatedAtDescIdDesc(
            String location, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM UnifiedFinancialLedgerEntry e "
            + "WHERE e.location = :location AND e.txnDate >= :from AND e.txnDate <= :to "
            + "AND e.txnType = :txnType AND e.paymentMode IN :modes")
    BigDecimal sumAmountByLocationDateRangeTypeModes(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("txnType") LedgerTransactionType txnType,
            @Param("modes") Collection<LedgerPaymentMode> modes);
}
