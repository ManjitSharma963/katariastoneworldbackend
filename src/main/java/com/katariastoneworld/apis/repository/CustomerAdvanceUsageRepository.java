package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.CustomerAdvanceUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CustomerAdvanceUsageRepository extends JpaRepository<CustomerAdvanceUsage, Long> {

    List<CustomerAdvanceUsage> findByBillKindAndBillId(BillKind billKind, Long billId);

    List<CustomerAdvanceUsage> findByBillKindAndBillIdIn(BillKind billKind, Collection<Long> billIds);

    List<CustomerAdvanceUsage> findByAdvanceIdOrderByCreatedAtDesc(Long advanceId);

    /** Sum of advance applied to bills when usage row was created in [start, end) for this location. */
    @Query("SELECT COALESCE(SUM(u.amountUsed), 0) FROM CustomerAdvanceUsage u JOIN u.advance adv JOIN adv.customer c "
            + "WHERE c.location = :loc AND u.createdAt >= :start AND u.createdAt < :end")
    BigDecimal sumUsageForLocationAndCreatedAtRange(@Param("loc") String loc, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
