package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.InventoryReservation;
import com.katariastoneworld.apis.entity.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    @Query("""
            SELECT COALESCE(SUM(r.reservedQuantity), 0) FROM InventoryReservation r
            WHERE r.productId = :productId
            AND r.status = com.katariastoneworld.apis.entity.InventoryReservationStatus.ACTIVE
            AND (r.expiresAt IS NULL OR r.expiresAt > :now)
            """)
    BigDecimal sumActiveReservedByProductId(@Param("productId") Long productId, @Param("now") java.time.LocalDateTime now);

    @Query("""
            SELECT COALESCE(SUM(r.reservedQuantity), 0) FROM InventoryReservation r
            WHERE r.productId = :productId
            AND r.status = com.katariastoneworld.apis.entity.InventoryReservationStatus.ACTIVE
            AND (r.expiresAt IS NULL OR r.expiresAt > :now)
            AND r.referenceId = :billId AND r.billKind = :billKind
            """)
    BigDecimal sumActiveReservedForBill(
            @Param("productId") Long productId,
            @Param("billId") Long billId,
            @Param("billKind") String billKind,
            @Param("now") java.time.LocalDateTime now);

    List<InventoryReservation> findByReferenceIdAndBillKindAndStatus(
            Long referenceId,
            String billKind,
            InventoryReservationStatus status);
}
