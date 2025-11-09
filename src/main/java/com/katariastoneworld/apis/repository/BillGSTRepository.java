package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillGSTRepository extends JpaRepository<BillGST, Long> {
    List<BillGST> findByCustomer(Customer customer);
    Optional<BillGST> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
}

