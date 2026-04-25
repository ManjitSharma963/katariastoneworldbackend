package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillInventoryReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillInventoryReturnRepository extends JpaRepository<BillInventoryReturn, Long> {
}
