package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.InventoryDirection;
import com.katariastoneworld.apis.entity.InventoryReferenceType;
import com.katariastoneworld.apis.entity.InventoryTransaction;
import com.katariastoneworld.apis.entity.InventoryTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);

    /**
     * Latest bill sale row for this product still treated as an originating sale (not itself a reversal target row).
     * Used to set {@code reversal_of_id} on RETURN / IN rows.
     */
    Optional<InventoryTransaction> findFirstByProductIdAndReferenceTypeAndReferenceIdAndBillKindAndTxnTypeAndDirectionAndReversalOfIdIsNullOrderByIdDesc(
            Long productId,
            InventoryReferenceType referenceType,
            Long referenceId,
            String billKind,
            InventoryTxnType txnType,
            InventoryDirection direction);

    /**
     * Sum of signed quantity changes per product for movements on or after {@code afterInstant}
     * (used with current stock to reconstruct quantity before that instant).
     */
    @Query(
            value = "SELECT t.product_id, COALESCE(SUM(CASE WHEN t.direction = 'IN' THEN t.quantity ELSE -t.quantity END), 0) "
                    + "FROM inventory_transactions t "
                    + "INNER JOIN products p ON p.id = t.product_id WHERE p.location = :location "
                    + "AND t.created_at >= :afterInstant GROUP BY t.product_id",
            nativeQuery = true)
    List<Object[]> sumSignedQuantityByProductAfterInstant(
            @Param("location") String location,
            @Param("afterInstant") LocalDateTime afterInstant);

    @Query("""
            SELECT t FROM InventoryTransaction t, Product p
            WHERE p.id = t.productId
            AND p.location = :location
            AND (
              :fromD IS NULL
              OR (
                (t.businessDate IS NOT NULL AND t.businessDate >= :fromD AND t.businessDate <= :toD)
                OR (t.businessDate IS NULL AND t.createdAt >= :fromTs AND t.createdAt < :toExclusive)
              )
            )
            AND (:txnType IS NULL OR t.txnType = :txnType)
            ORDER BY t.createdAt DESC
            """)
    List<InventoryTransaction> findAllForLocationWithFilters(
            @Param("location") String location,
            @Param("txnType") InventoryTxnType txnType,
            @Param("fromD") LocalDate fromD,
            @Param("toD") LocalDate toD,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toExclusive") LocalDateTime toExclusive);
}
