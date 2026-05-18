package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.BillInventoryReturn;
import com.katariastoneworld.apis.entity.BillKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillInventoryReturnRepository extends JpaRepository<BillInventoryReturn, Long> {

    @Query("""
            SELECT DISTINCT r FROM BillInventoryReturn r
            LEFT JOIN FETCH r.lines
            WHERE r.billKind = :kind AND r.billId = :billId
            ORDER BY r.id ASC
            """)
    List<BillInventoryReturn> findWithLinesByBillKindAndBillId(@Param("kind") BillKind kind, @Param("billId") Long billId);
}
