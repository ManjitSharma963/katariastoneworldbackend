package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Bill;
import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByCustomer(Customer customer);
    Optional<Bill> findByBillNumber(String billNumber);
    boolean existsByBillNumber(String billNumber);
}
