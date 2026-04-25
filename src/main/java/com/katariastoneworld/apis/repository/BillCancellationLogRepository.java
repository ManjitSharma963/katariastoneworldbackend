package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillCancellationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BillCancellationLogRepository extends JpaRepository<BillCancellationLog, Long> {

    List<BillCancellationLog> findByLocationAndBillDateBetweenOrderByCancelledAtDesc(
            String location, LocalDate billDateFrom, LocalDate billDateTo);
}
