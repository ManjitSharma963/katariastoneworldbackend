package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillGSTRepository extends JpaRepository<BillGST, Long> {
    List<BillGST> findByCustomer(Customer customer);
    Optional<BillGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
    
    @Query("SELECT b FROM BillGST b WHERE b.customer.location = :location")
    List<BillGST> findByCustomerLocation(@Param("location") String location);

    @Query("SELECT b FROM BillGST b WHERE b.customer.location = :location AND b.createdByUserId = :userId")
    List<BillGST> findByCustomerLocationAndCreatedByUserId(@Param("location") String location, @Param("userId") Long userId);

    @Query("SELECT b FROM BillGST b JOIN b.customer c WHERE c.location = :location AND b.billDate = :date")
    List<BillGST> findByCustomerLocationAndBillDate(@Param("location") String location, @Param("date") LocalDate date);

    @Query("SELECT b FROM BillGST b JOIN b.customer c WHERE c.location = :location AND b.billDate >= :from AND b.billDate <= :to")
    List<BillGST> findByCustomerLocationAndBillDateBetween(@Param("location") String location, @Param("from") LocalDate from, @Param("to") LocalDate to);
    
    @Query("SELECT b FROM BillGST b WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillGST> findByBillNumberAndCustomerLocation(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query("SELECT DISTINCT b FROM BillGST b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillGST> findByBillNumberAndCustomerLocationWithItemsAndProducts(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query("SELECT DISTINCT b FROM BillGST b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.id = :id")
    Optional<BillGST> findByIdWithItemsAndProducts(@Param("id") Long id);
    
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_gst b WHERE b.bill_number REGEXP '^[0-9]+$'", nativeQuery = true)
    Integer findMaxBillNumber();

    /** Max bill number for bills created by this user (per-user sequence, no conflict between users). */
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_gst b WHERE b.bill_number REGEXP '^[0-9]+$' AND b.created_by_user_id = :userId", nativeQuery = true)
    Integer findMaxBillNumberByCreatedByUserId(@Param("userId") Long userId);
}

