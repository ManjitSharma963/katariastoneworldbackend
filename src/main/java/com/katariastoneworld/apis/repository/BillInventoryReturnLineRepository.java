package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillInventoryReturnLine;
import com.katariastoneworld.apis.entity.BillKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Query("""
            SELECT COALESCE(SUM(COALESCE(l.lineReturnValue, 0)), 0)
            FROM BillInventoryReturnLine l
            WHERE l.header.billKind = :kind AND l.header.billId = :billId
            """)
    BigDecimal sumLineReturnValueForBill(@Param("kind") BillKind kind, @Param("billId") Long billId);

    @Query("""
            SELECT l.header.billId, l.billItemId, COALESCE(SUM(l.quantityReturned), 0)
            FROM BillInventoryReturnLine l
            WHERE l.header.billKind = :kind AND l.header.billId IN :billIds
            GROUP BY l.header.billId, l.billItemId
            """)
    List<Object[]> sumReturnedQuantityGroupedByBillIds(
            @Param("kind") BillKind kind, @Param("billIds") List<Long> billIds);

    @Query("""
            SELECT l.header.billId, COALESCE(SUM(COALESCE(l.lineReturnValue, 0)), 0)
            FROM BillInventoryReturnLine l
            WHERE l.header.billKind = :kind AND l.header.billId IN :billIds
            GROUP BY l.header.billId
            """)
    List<Object[]> sumLineReturnValueGroupedByBillIds(
            @Param("kind") BillKind kind, @Param("billIds") List<Long> billIds);
}
