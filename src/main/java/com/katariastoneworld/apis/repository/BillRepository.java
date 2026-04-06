package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Bill;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByCustomer(Customer customer);

    Optional<Bill> findByBillNumber(String billNumber);

    boolean existsByBillNumber(String billNumber);

    @Query("SELECT b FROM Bill b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    List<Bill> findByBillLocation(@Param("location") String location);

    @Query("SELECT b FROM Bill b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)) AND b.createdByUserId = :userId")
    List<Bill> findByBillLocationAndCreatedByUserId(@Param("location") String location, @Param("userId") Long userId);

    @Query("SELECT b FROM Bill b WHERE b.billNumber = :billNumber AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    Optional<Bill> findByBillNumberAndBillLocation(@Param("billNumber") String billNumber, @Param("location") String location);

    @Query("SELECT DISTINCT b FROM Bill b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.billNumber = :billNumber AND (b.location = :location OR (b.location IS NULL AND b.customer.location = :location))")
    Optional<Bill> findByBillNumberAndBillLocationWithItemsAndProducts(@Param("billNumber") String billNumber,
            @Param("location") String location);

    @Query("SELECT b FROM Bill b WHERE b.customer.location = :location")
    List<Bill> findByCustomerLocation(@Param("location") String location);

    @Query("SELECT b FROM Bill b WHERE b.customer.location = :location AND b.createdByUserId = :userId")
    List<Bill> findByCustomerLocationAndCreatedByUserId(@Param("location") String location, @Param("userId") Long userId);

    @Query("SELECT b FROM Bill b JOIN b.customer c WHERE c.location = :location AND b.billDate = :date")
    List<Bill> findByCustomerLocationAndBillDate(@Param("location") String location, @Param("date") LocalDate date);

    @Query("SELECT b FROM Bill b JOIN b.customer c WHERE c.location = :location AND b.billDate >= :from AND b.billDate <= :to")
    List<Bill> findByCustomerLocationAndBillDateBetween(@Param("location") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.location = :location OR (b.location IS NULL AND b.customer.location = :location)) AND b.billDate >= :from AND b.billDate <= :to")
    List<Bill> findByBillLocationAndBillDateBetween(@Param("location") String location, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<Bill> findByBillNumberAndCustomerLocation(@Param("billNumber") String billNumber,
            @Param("location") String location);

    @Query("SELECT DISTINCT b FROM Bill b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<Bill> findByBillNumberAndCustomerLocationWithItemsAndProducts(@Param("billNumber") String billNumber,
            @Param("location") String location);

    @Query("SELECT DISTINCT b FROM Bill b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.items i LEFT JOIN FETCH i.product WHERE b.id = :id")
    Optional<Bill> findByIdWithItemsAndProducts(@Param("id") Long id);

    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills b WHERE b.bill_kind = :kind AND b.bill_number REGEXP '^[0-9]+$'", nativeQuery = true)
    Integer findMaxNumericBillNumberForKind(@Param("kind") String kind);

    @Query(value = """
            SELECT MAX(CAST(SUBSTRING(b.bill_number, CHAR_LENGTH(:prefix) + 1) AS UNSIGNED))
            FROM bills b
            WHERE b.bill_kind = :kind
              AND b.bill_number LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(b.bill_number, CHAR_LENGTH(:prefix) + 1) REGEXP '^[0-9]+$'
            """, nativeQuery = true)
    Integer findMaxBillNumberForPrefixAndKind(@Param("prefix") String prefix, @Param("kind") String kind);

    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills b WHERE b.bill_kind = :kind AND b.bill_number REGEXP '^[0-9]+$' AND b.created_by_user_id = :userId", nativeQuery = true)
    Integer findMaxNumericBillNumberByCreatedByUserIdAndKind(@Param("userId") Long userId, @Param("kind") String kind);
}
