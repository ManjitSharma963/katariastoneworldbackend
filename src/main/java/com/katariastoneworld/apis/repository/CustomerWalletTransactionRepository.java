package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.CustomerWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomerWalletTransactionRepository extends JpaRepository<CustomerWalletTransaction, Long> {

    @Query("""
            SELECT COALESCE(SUM(
              CASE
                WHEN t.txnType = :creditType THEN t.amount
                ELSE -t.amount
              END
            ), 0)
            FROM CustomerWalletTransaction t
            WHERE t.customer.id = :customerId
              AND t.status = :status
            """)
    BigDecimal getActiveWalletBalance(@Param("customerId") Long customerId,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("creditType") CustomerWalletTransaction.TxnType creditType);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerWalletTransaction t
            WHERE t.customer.id = :customerId
              AND t.status = :status
              AND t.txnType = :txnType
            """)
    BigDecimal sumByTxnType(@Param("customerId") Long customerId,
            @Param("txnType") CustomerWalletTransaction.TxnType txnType,
            @Param("status") CustomerWalletTransaction.Status status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerWalletTransaction t
            WHERE t.customer.id = :customerId
              AND t.status = :status
              AND t.txnType = :txnType
              AND t.source = :source
            """)
    BigDecimal sumByCustomerIdAndStatusAndTxnTypeAndSource(
            @Param("customerId") Long customerId,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("txnType") CustomerWalletTransaction.TxnType txnType,
            @Param("source") String source);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerWalletTransaction t
            WHERE t.status = :status
              AND t.txnType = :txnType
              AND t.source = 'BILL_PAYMENT'
              AND t.referenceId = :referenceId
            """)
    BigDecimal sumDebitByBillReference(@Param("referenceId") String referenceId,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("txnType") CustomerWalletTransaction.TxnType txnType);

    List<CustomerWalletTransaction> findBySourceAndReferenceIdAndTxnTypeAndStatus(
            String source,
            String referenceId,
            CustomerWalletTransaction.TxnType txnType,
            CustomerWalletTransaction.Status status);

    List<CustomerWalletTransaction> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<CustomerWalletTransaction> findByCustomer_IdAndStatusOrderByCreatedAtAscIdAsc(
            Long customerId,
            CustomerWalletTransaction.Status status);

    List<CustomerWalletTransaction> findBySourceAndTxnTypeAndStatusAndReferenceIdIn(
            String source,
            CustomerWalletTransaction.TxnType txnType,
            CustomerWalletTransaction.Status status,
            List<String> referenceIds);

    List<CustomerWalletTransaction> findByCustomer_IdInAndStatusOrderByCreatedAtAscIdAsc(
            List<Long> customerIds,
            CustomerWalletTransaction.Status status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerWalletTransaction t
            WHERE t.customer.location = :location
              AND t.status = :status
              AND t.txnType = :txnType
              AND t.source = :source
              AND t.createdAt >= :fromTs
              AND t.createdAt < :toExclusiveTs
            """)
    BigDecimal sumByLocationAndTxnTypeAndSourceAndCreatedAtRange(
            @Param("location") String location,
            @Param("txnType") CustomerWalletTransaction.TxnType txnType,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("source") String source,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toExclusiveTs") LocalDateTime toExclusiveTs);

    @Query("""
            SELECT t
            FROM CustomerWalletTransaction t
            WHERE t.customer.location = :location
              AND t.status = :status
              AND t.txnType = :txnType
              AND t.source = :source
              AND t.createdAt >= :fromTs
              AND t.createdAt < :toExclusiveTs
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<CustomerWalletTransaction> findByLocationAndTxnTypeAndSourceAndCreatedAtRange(
            @Param("location") String location,
            @Param("txnType") CustomerWalletTransaction.TxnType txnType,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("source") String source,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toExclusiveTs") LocalDateTime toExclusiveTs);

    @Query("""
            SELECT COALESCE(SUM(
              CASE
                WHEN t.txnType = :creditType THEN t.amount
                ELSE -t.amount
              END
            ), 0)
            FROM CustomerWalletTransaction t
            WHERE LOWER(TRIM(t.customer.location)) = LOWER(TRIM(:location))
              AND t.status = :status
            """)
    BigDecimal getActiveWalletBalanceByLocation(
            @Param("location") String location,
            @Param("status") CustomerWalletTransaction.Status status,
            @Param("creditType") CustomerWalletTransaction.TxnType creditType);
}
