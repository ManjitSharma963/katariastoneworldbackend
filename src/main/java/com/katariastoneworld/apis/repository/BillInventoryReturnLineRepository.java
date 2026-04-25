package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillInventoryReturnLine;
import com.katariastoneworld.apis.entity.BillKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillInventoryReturnLineRepository extends JpaRepository<BillInventoryReturnLine, Long> {

    @Query("""
            SELECT l.billItemId, COALESCE(SUM(l.quantityReturned), 0)
            FROM BillInventoryReturnLine l
            WHERE l.header.billKind = :kind AND l.header.billId = :billId
            GROUP BY l.billItemId
            """)
    List<Object[]> sumReturnedQuantityGroupedByBillItemId(@Param("kind") BillKind kind, @Param("billId") Long billId);
}
