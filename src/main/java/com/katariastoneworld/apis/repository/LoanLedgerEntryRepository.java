package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.LoanLedgerEntry;
import com.katariastoneworld.apis.entity.LoanLedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanLedgerEntryRepository extends JpaRepository<LoanLedgerEntry, Long> {

    Optional<LoanLedgerEntry> findByExpenseId(Long expenseId);

    List<LoanLedgerEntry> findByLocationAndLenderIdOrderByEntryDateDescIdDesc(String location, Long lenderId);

    @Query("SELECT e FROM LoanLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.lenderId = :lenderId ORDER BY e.entryDate DESC, e.id DESC")
    List<LoanLedgerEntry> findByLocationNormalizedAndLenderIdOrderByEntryDateDescIdDesc(
            @Param("location") String location,
            @Param("lenderId") Long lenderId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LoanLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.lenderId = :lenderId AND e.entryType = :type")
    BigDecimal sumAmountByLocationLenderAndType(
            @Param("location") String location,
            @Param("lenderId") Long lenderId,
            @Param("type") LoanLedgerEntryType type);

    /**
     * Sum ledger rows of {@code entryType} in the date range whose notes include Mode: bank_transfer, cheque, or card
     * (same convention as {@code LoanLedgerService.composeNotesWithMode}).
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LoanLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.entryType = :entryType "
            + "AND e.entryDate >= :fromDate AND e.entryDate <= :toDate "
            + "AND (LOWER(COALESCE(e.notes, '')) LIKE '%mode: bank_transfer%' "
            + "OR LOWER(COALESCE(e.notes, '')) LIKE '%mode: cheque%' "
            + "OR LOWER(COALESCE(e.notes, '')) LIKE '%mode: card%')")
    BigDecimal sumBankChequeModeEntriesBetween(
            @Param("location") String location,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("entryType") LoanLedgerEntryType entryType);

    /** Loan RECEIPT in range paid in cash or UPI (notes contain Mode: cash or Mode: upi). */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LoanLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:location)) "
            + "AND e.entryType = :receiptType "
            + "AND e.entryDate >= :fromDate AND e.entryDate <= :toDate "
            + "AND (LOWER(COALESCE(e.notes, '')) LIKE '%mode: cash%' OR LOWER(COALESCE(e.notes, '')) LIKE '%mode: upi%')")
    BigDecimal sumCashUpiReceiptsBetween(
            @Param("location") String location,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("receiptType") LoanLedgerEntryType receiptType);
}
