package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByLocationOrderByNameAsc(String location);

    Optional<Supplier> findByIdAndLocation(Long id, String location);
}
