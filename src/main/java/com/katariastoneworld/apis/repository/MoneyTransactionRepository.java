package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MoneyTransactionRepository extends JpaRepository<MoneyTransaction, Long> {

    /**
     * Idempotency for lines keyed by domain row id — include {@code category} so numeric ids from
     * different ledgers (e.g. loan vs receivable) do not collide.
     */
    boolean existsByReferenceIdAndReferenceTypeAndCategory(
            Long referenceId,
            MoneyReferenceType referenceType,
            MoneyCategory category);

    Optional<MoneyTransaction> findByReferenceIdAndReferenceTypeAndCategory(
            Long referenceId,
            MoneyReferenceType referenceType,
            MoneyCategory category);

    boolean existsByReferenceTypeAndReferenceIdAndAmountAndPaymentMode(
            MoneyReferenceType referenceType,
            Long referenceId,
            BigDecimal amount,
            MoneyPaymentMode paymentMode);

    /**
     * One active ledger row per saved {@code bill_payments} line (idempotency for bill → transactions).
     */
    boolean existsByBillPaymentIdAndIsDeletedFalse(Long billPaymentId);

    Optional<MoneyTransaction> findFirstByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(Long billPaymentId);

    List<MoneyTransaction> findByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(Long billPaymentId);

    List<MoneyTransaction> findByReferenceTypeAndReferenceIdAndCategoryAndIsDeletedFalse(
            MoneyReferenceType referenceType,
            Long referenceId,
            MoneyCategory category);

    /**
     * Net position for payment rails: {@code SUM(IN) − SUM(OUT)} for active, non-deleted rows.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.direction = com.katariastoneworld.apis.entity.MoneyDirection.IN "
            + "THEN m.amount ELSE -m.amount END), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.paymentMode IN :modes "
            + "AND (m.subCategory IS NULL OR m.subCategory <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') "
            + "AND (m.txnType IS NULL OR m.txnType <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') ")
    BigDecimal sumNetSignedByLocationAndPaymentModes(
            @Param("location") String location,
            @Param("modes") Collection<MoneyPaymentMode> modes);

    /**
     * Net CASH+UPI through end of day before {@code beforeDate}:
     * {@code SUM(IN) − SUM(OUT)} for {@code transactionDate < beforeDate} (excludes {@code ADVANCE_APPLICATION}).
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.direction = com.katariastoneworld.apis.entity.MoneyDirection.IN "
            + "THEN m.amount ELSE -m.amount END), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.paymentMode IN :modes "
            + "AND m.transactionDate IS NOT NULL AND m.transactionDate < :beforeDate "
            + "AND (m.subCategory IS NULL OR m.subCategory <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') "
            + "AND (m.txnType IS NULL OR m.txnType <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') ")
    BigDecimal sumNetSignedByLocationAndPaymentModesBeforeDate(
            @Param("location") String location,
            @Param("modes") Collection<MoneyPaymentMode> modes,
            @Param("beforeDate") LocalDate beforeDate);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.direction = :direction AND m.paymentMode IN :modes "
            + "AND (m.subCategory IS NULL OR m.subCategory <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') "
            + "AND (m.txnType IS NULL OR m.txnType <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') ")
    BigDecimal sumAmountByLocationDateRangeDirectionModes(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("direction") MoneyDirection direction,
            @Param("modes") Collection<MoneyPaymentMode> modes);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.direction = :direction AND m.paymentMode IN :modes "
            + "AND m.category NOT IN :excludeCategories")
    BigDecimal sumAmountByLocationDateRangeDirectionModesExcludingCategories(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("direction") MoneyDirection direction,
            @Param("modes") Collection<MoneyPaymentMode> modes,
            @Param("excludeCategories") Collection<MoneyCategory> excludeCategories);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.direction = com.katariastoneworld.apis.entity.MoneyDirection.OUT "
            + "AND m.paymentMode IN :modes "
            + "AND m.category IN :categories")
    BigDecimal sumOutByLocationDateRangeModesAndCategories(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("modes") Collection<MoneyPaymentMode> modes,
            @Param("categories") Collection<MoneyCategory> categories);

    List<MoneyTransaction> findByAdjustmentGroupIdAndIsDeletedFalseOrderByIdAsc(String adjustmentGroupId);

    boolean existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
            MoneyReferenceType referenceType,
            Long referenceId,
            String txnType);

    /**
     * Net flow for CASH/UPI bill collections (and reversals) in a range — for reconciliation with {@code bill_payments}.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.direction = com.katariastoneworld.apis.entity.MoneyDirection.IN "
            + "THEN m.amount ELSE -m.amount END), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.category = com.katariastoneworld.apis.entity.MoneyCategory.BILL "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.paymentMode IN :modes "
            + "AND (m.subCategory IS NULL OR m.subCategory <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') "
            + "AND (m.txnType IS NULL OR m.txnType <> '" + MoneyLedgerCategories.SUB_ADVANCE_APPLICATION + "') ")
    BigDecimal sumNetBillInHandByLocationDateRangeModes(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("modes") Collection<MoneyPaymentMode> modes);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MoneyTransaction m "
            + "WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.direction = com.katariastoneworld.apis.entity.MoneyDirection.IN "
            + "AND m.category = :category AND m.paymentMode IN :modes")
    BigDecimal sumInByLocationDateRangeCategoryModes(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("category") MoneyCategory category,
            @Param("modes") Collection<MoneyPaymentMode> modes);

    Page<MoneyTransaction> findByLocationAndTransactionDateBetweenAndIsDeletedFalseOrderByTransactionDateDescDateTimeDescIdDesc(
            String location, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT m FROM MoneyTransaction m WHERE m.location = :location AND m.isDeleted = false "
            + "AND m.status = com.katariastoneworld.apis.entity.MoneyTxnStatus.ACTIVE "
            + "AND m.transactionDate >= :from AND m.transactionDate <= :to "
            + "AND m.direction = com.katariastoneworld.apis.entity.MoneyDirection.OUT "
            + "AND m.category = com.katariastoneworld.apis.entity.MoneyCategory.CLIENT_PAYMENT "
            + "ORDER BY m.transactionDate ASC, m.id ASC")
    List<MoneyTransaction> findClientPaymentOutLinesForReport(
            @Param("location") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
