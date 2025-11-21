package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillNonGSTRepository extends JpaRepository<BillNonGST, Long> {
    List<BillNonGST> findByCustomer(Customer customer);
    Optional<BillNonGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
    
    @Query("SELECT b FROM BillNonGST b WHERE b.customer.location = :location")
    List<BillNonGST> findByCustomerLocation(@Param("location") String location);
    
    @Query("SELECT b FROM BillNonGST b WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillNonGST> findByBillNumberAndCustomerLocation(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_non_gst b WHERE b.bill_number REGEXP '^[0-9]+$'", nativeQuery = true)
    Integer findMaxBillNumber();
}

