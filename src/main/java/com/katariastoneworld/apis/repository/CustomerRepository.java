package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByPhoneAndUserId(String phone, Long userId);
    Optional<Customer> findByPhoneAndLocation(String phone, String location);
    Optional<Customer> findByIdAndLocation(Long id, String location);
    boolean existsByPhone(String phone);
    List<Customer> findByLocation(String location);
    List<Customer> findByUserId(Long userId);
}

