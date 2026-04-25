package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.UnifiedFinancialLedgerEntry;
import com.katariastoneworld.apis.repository.UnifiedFinancialLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Single append/upsert entry point for the unified financial ledger (Phase 1).
 * <p>
 * When {@code referenceId} is non-null, rows are idempotent per (location, source, referenceId): same key updates
 * amount/date/mode/type/description. When {@code referenceId} is null, each call inserts a new row.
 * <p>
 * Legacy {@code financial_ledger} and domain tables remain authoritative for existing summaries until migrated.
 */
@Service
@Transactional
public class UnifiedFinancialLedgerService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedFinancialLedgerService.class);

    @Autowired
    private UnifiedFinancialLedgerRepository unifiedFinancialLedgerRepository;

    /**
     * Record one money movement. Prefer non-null {@code referenceId} for any entity-backed line (expense id, payment id, etc.).
     */
    public UnifiedFinancialLedgerEntry recordTransaction(
            String location,
            LocalDate date,
            BigDecimal amount,
            LedgerTransactionType type,
            LedgerPaymentMode mode,
            String source,
            Long referenceId,
            String description) {
        if (location == null || location.isBlank()) {
            log.warn("unified_ledger skip: blank location source={} ref={}", source, referenceId);
            return null;
        }
        if (type == null || mode == null) {
            log.warn("unified_ledger skip: null type/mode source={} ref={}", source, referenceId);
            return null;
        }
        if (source == null || source.isBlank()) {
            log.warn("unified_ledger skip: blank source location={}", location);
            return null;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("unified_ledger skip: non-positive amount source={} ref={}", source, referenceId);
            return null;
        }
        String loc = location.trim();
        String src = source.trim().toUpperCase();
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = date != null ? date : LocalDate.now();

        if (referenceId != null) {
            Optional<UnifiedFinancialLedgerEntry> existing =
                    unifiedFinancialLedgerRepository.findByLocationAndSourceAndReferenceId(loc, src, referenceId);
            if (existing.isPresent()) {
                UnifiedFinancialLedgerEntry row = existing.get();
                row.setTxnDate(d);
                row.setTxnType(type);
                row.setAmount(amt);
                row.setPaymentMode(mode);
                row.setDescription(description);
                return unifiedFinancialLedgerRepository.save(row);
            }
        }

        UnifiedFinancialLedgerEntry row = new UnifiedFinancialLedgerEntry();
        row.setLocation(loc);
        row.setTxnDate(d);
        row.setTxnType(type);
        row.setAmount(amt);
        row.setPaymentMode(mode);
        row.setSource(src);
        row.setReferenceId(referenceId);
        row.setDescription(description);
        return unifiedFinancialLedgerRepository.save(row);
    }

    /** Remove the unified row for an entity-backed key (e.g. soft-delete expense or remove bill payment). */
    public void removeTransaction(String location, String source, Long referenceId) {
        if (location == null || location.isBlank() || source == null || source.isBlank() || referenceId == null) {
            return;
        }
        unifiedFinancialLedgerRepository
                .findByLocationAndSourceAndReferenceId(location.trim(), source.trim().toUpperCase(), referenceId)
                .ifPresent(unifiedFinancialLedgerRepository::delete);
    }
}
