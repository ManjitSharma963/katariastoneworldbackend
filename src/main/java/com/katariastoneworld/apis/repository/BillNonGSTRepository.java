package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillNonGSTRepository extends JpaRepository<BillNonGST, Long> {
    List<BillNonGST> findByCustomer(Customer customer);
    Optional<BillNonGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
    
    /**
     * Location-scoped listing. For legacy rows where {@code b.location} is null, fallback to {@code customer.location}.
     */
    @Query("SELECT b FROM BillNonGST b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    List<BillNonGST> findByBillLocation(@Param("location") String location);

    @Query("SELECT b FROM BillNonGST b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)) AND b.createdByUserId = :userId")
    List<BillNonGST> findByBillLocationAndCreatedByUserId(@Param("location") String location, @Param("userId") Long userId);

    @Query("SELECT b FROM BillNonGST b WHERE b.billNumber = :billNumber AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    Optional<BillNonGST> findByBillNumberAndBillLocation(@Param("billNumber") String billNumber, @Param("location") String location);

    @Query("SELECT DISTINCT b FROM BillNonGST b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.billNumber = :billNumber AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    Optional<BillNonGST> findByBillNumberAndBillLocationWithItemsAndProducts(@Param("billNumber") String billNumber, @Param("location") String location);

    @Query("SELECT b FROM BillNonGST b WHERE b.customer.location = :location")
    List<BillNonGST> findByCustomerLocation(@Param("location") String location);

    @Query("SELECT b FROM BillNonGST b WHERE b.customer.location = :location AND b.createdByUserId = :userId")
    List<BillNonGST> findByCustomerLocationAndCreatedByUserId(@Param("location") String location, @Param("userId") Long userId);

    @Query("SELECT b FROM BillNonGST b JOIN b.customer c WHERE c.location = :location AND b.billDate = :date")
    List<BillNonGST> findByCustomerLocationAndBillDate(@Param("location") String location, @Param("date") LocalDate date);

    @Query("SELECT b FROM BillNonGST b JOIN b.customer c WHERE c.location = :location AND b.billDate >= :from AND b.billDate <= :to")
    List<BillNonGST> findByCustomerLocationAndBillDateBetween(@Param("location") String location, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM BillNonGST b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)) AND b.billDate >= :from AND b.billDate <= :to")
    List<BillNonGST> findByBillLocationAndBillDateBetween(@Param("location") String location, @Param("from") LocalDate from, @Param("to") LocalDate to);
    
    @Query("SELECT b FROM BillNonGST b WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillNonGST> findByBillNumberAndCustomerLocation(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query("SELECT DISTINCT b FROM BillNonGST b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillNonGST> findByBillNumberAndCustomerLocationWithItemsAndProducts(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query("SELECT DISTINCT b FROM BillNonGST b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.id = :id")
    Optional<BillNonGST> findByIdWithItemsAndProducts(@Param("id") Long id);
    
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_non_gst b WHERE b.bill_number REGEXP '^[0-9]+$'", nativeQuery = true)
    Integer findMaxBillNumber();

    /**
     * Max numeric suffix for prefixed bill numbers like {@code KSW-U3-12}.
     * {@code prefix} should include trailing '-' (e.g. {@code KSW-U3-}).
     */
    @Query(value = """
            SELECT MAX(CAST(SUBSTRING(b.bill_number, CHAR_LENGTH(:prefix) + 1) AS UNSIGNED))
            FROM bills_non_gst b
            WHERE b.bill_number LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(b.bill_number, CHAR_LENGTH(:prefix) + 1) REGEXP '^[0-9]+$'
            """, nativeQuery = true)
    Integer findMaxBillNumberForPrefix(@Param("prefix") String prefix);

    /** Max bill number for bills created by this user (per-user sequence, no conflict between users). */
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_non_gst b WHERE b.bill_number REGEXP '^[0-9]+$' AND b.created_by_user_id = :userId", nativeQuery = true)
    Integer findMaxBillNumberByCreatedByUserId(@Param("userId") Long userId);
}

