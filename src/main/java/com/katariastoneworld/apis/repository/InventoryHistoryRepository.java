package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.InventoryActionType;
import com.katariastoneworld.apis.entity.InventoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {

    /**
     * Sum of {@code quantity_changed} per product for movements strictly after the given instant (typically start of day after range end).
     */
    @Query(
            value = "SELECT h.product_id, COALESCE(SUM(h.quantity_changed), 0) FROM inventory_history h "
                    + "INNER JOIN products p ON p.id = h.product_id WHERE p.location = :location "
                    + "AND h.created_at >= :afterInstant GROUP BY h.product_id",
            nativeQuery = true)
    List<Object[]> sumQuantityChangedByProductAfterInstant(
            @Param("location") String location,
            @Param("afterInstant") LocalDateTime afterInstant);

    List<InventoryHistory> findByProductIdOrderByCreatedAtDesc(Long productId);

    @Query("SELECT h FROM InventoryHistory h, Product p " +
            "WHERE p.id = h.productId " +
            "AND p.location = :location " +
            "AND (:actionType IS NULL OR h.actionType = :actionType) " +
            "AND (:fromTs IS NULL OR h.createdAt >= :fromTs) " +
            "AND (:toExclusive IS NULL OR h.createdAt < :toExclusive) " +
            "ORDER BY h.createdAt DESC")
    List<InventoryHistory> findAllForLocationWithFilters(
            @Param("location") String location,
            @Param("actionType") InventoryActionType actionType,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toExclusive") LocalDateTime toExclusive);
}
