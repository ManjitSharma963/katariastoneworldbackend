package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillVersionRepository extends JpaRepository<BillVersion, Long> {

    List<BillVersion> findByBillIdOrderByVersionNoDescCreatedAtDesc(Long billId);

    Optional<BillVersion> findTopByBillIdOrderByVersionNoDescCreatedAtDesc(Long billId);
}

