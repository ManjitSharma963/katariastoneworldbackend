package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    List<BillPayment> findByBillKindAndBillIdOrderByIdAsc(BillKind billKind, Long billId);

    java.util.Optional<BillPayment> findByIdAndBillKindAndBillId(Long id, BillKind billKind, Long billId);

    List<BillPayment> findByBillKindAndBillIdIn(BillKind billKind, Collection<Long> billIds);

    /**
     * Payments received on {@code date} for bills at this location (GST + non-GST).
     * Uses bill.location; for legacy rows with null bill.location, falls back to customer.location.
     */
    @Query("""
            SELECT p FROM BillPayment p WHERE p.isDeleted = false AND p.paymentDate = :date AND EXISTS (
                  SELECT 1 FROM Bill b WHERE b.id = p.billId AND b.billKind = p.billKind
                    AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)))
            """)
    List<BillPayment> findByPaymentDateAndBillLocation(@Param("location") String location, @Param("date") LocalDate date);

    /**
     * Payments with {@code payment_date} in [{@code from}, {@code to}] for bills at this location.
     * Uses bill.location; for legacy rows with null bill.location, falls back to customer.location.
     */
    @Query("""
            SELECT p FROM BillPayment p WHERE p.isDeleted = false AND p.paymentDate >= :from AND p.paymentDate <= :to AND EXISTS (
                  SELECT 1 FROM Bill b WHERE b.id = p.billId AND b.billKind = p.billKind
                    AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)))
            """)
    List<BillPayment> findByPaymentDateBetweenAndBillLocation(@Param("location") String location, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
