package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillEvent;
import com.katariastoneworld.apis.entity.BillKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillEventRepository extends JpaRepository<BillEvent, Long> {

    List<BillEvent> findByBillKindAndBillIdOrderByCreatedAtDescIdDesc(BillKind billKind, Long billId);
}
