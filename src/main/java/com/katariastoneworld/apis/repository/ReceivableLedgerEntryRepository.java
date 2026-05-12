package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ReceivableLedgerEntry;
import com.katariastoneworld.apis.entity.ReceivableLedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ReceivableLedgerEntryRepository extends JpaRepository<ReceivableLedgerEntry, Long> {

    @Query("SELECT e FROM ReceivableLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.borrowerId = :borrowerId ORDER BY e.entryDate DESC, e.id DESC")
    List<ReceivableLedgerEntry> findByLocationNormalizedAndBorrowerIdOrderByEntryDateDescIdDesc(
            @Param("location") String location,
            @Param("borrowerId") Long borrowerId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ReceivableLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.borrowerId = :borrowerId AND e.entryType = :type")
    BigDecimal sumAmountByLocationBorrowerAndType(
            @Param("location") String location,
            @Param("borrowerId") Long borrowerId,
            @Param("type") ReceivableLedgerEntryType type);
}
