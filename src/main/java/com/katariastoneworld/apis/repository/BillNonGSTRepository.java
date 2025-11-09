package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillNonGSTRepository extends JpaRepository<BillNonGST, Long> {
    List<BillNonGST> findByCustomer(Customer customer);
    Optional<BillNonGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
}

