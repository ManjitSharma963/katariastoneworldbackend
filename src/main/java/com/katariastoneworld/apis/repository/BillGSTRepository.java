package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillGSTRepository extends JpaRepository<BillGST, Long> {
    List<BillGST> findByCustomer(Customer customer);
    Optional<BillGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
    
    @Query("SELECT b FROM BillGST b WHERE b.customer.location = :location")
    List<BillGST> findByCustomerLocation(@Param("location") String location);
    
    @Query("SELECT b FROM BillGST b WHERE b.billNumber = :billNumber AND b.customer.location = :location")
    Optional<BillGST> findByBillNumberAndCustomerLocation(@Param("billNumber") String billNumber, @Param("location") String location);
    
    @Query(value = "SELECT MAX(CAST(b.bill_number AS UNSIGNED)) FROM bills_gst b WHERE b.bill_number REGEXP '^[0-9]+$'", nativeQuery = true)
    Integer findMaxBillNumber();
}

