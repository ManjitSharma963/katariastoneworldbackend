package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ClientPurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ClientPurchasePaymentRepository extends JpaRepository<ClientPurchasePayment, Long> {
    
    @Query("SELECT cp FROM ClientPurchasePayment cp WHERE cp.clientPurchase.id = :clientPurchaseId ORDER BY cp.paymentDate DESC, cp.createdAt DESC")
    List<ClientPurchasePayment> findByClientPurchaseIdOrderByDateDesc(@Param("clientPurchaseId") Long clientPurchaseId);
    
    @Query("SELECT cp FROM ClientPurchasePayment cp WHERE cp.clientPurchase.id = :clientPurchaseId AND cp.clientPurchase.location = :location")
    List<ClientPurchasePayment> findByClientPurchaseIdAndLocation(@Param("clientPurchaseId") Long clientPurchaseId, @Param("location") String location);
    
    @Query("SELECT cp FROM ClientPurchasePayment cp WHERE cp.clientPurchase.location = :location ORDER BY cp.paymentDate DESC, cp.createdAt DESC")
    List<ClientPurchasePayment> findByLocationOrderByDateDesc(@Param("location") String location);

    @Query("SELECT p.clientPurchase.id, COALESCE(SUM(p.amount), 0) FROM ClientPurchasePayment p "
            + "WHERE p.clientPurchase.location = :location GROUP BY p.clientPurchase.id")
    List<Object[]> sumPaidAmountsGroupedByPurchaseId(@Param("location") String location);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM ClientPurchasePayment p WHERE p.clientPurchase.id = :purchaseId")
    BigDecimal sumAmountForPurchaseId(@Param("purchaseId") Long purchaseId);
}

