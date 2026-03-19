package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByLocation(String location);
    Optional<Employee> findByIdAndLocation(Long id, String location);
    List<Employee> findByUserId(Long userId);
}

