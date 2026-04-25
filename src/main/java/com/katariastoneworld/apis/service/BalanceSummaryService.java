package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.dto.UnifiedLedgerTransactionDTO;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.UnifiedFinancialLedgerEntry;
import com.katariastoneworld.apis.repository.UnifiedFinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 3: balances from {@code unified_financial_ledger} (CREDIT − DEBIT per payment rail), not {@code daily_budget_events}.
 */
@Service
@Transactional(readOnly = true)
public class BalanceSummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final List<LedgerPaymentMode> IN_HAND_MODES = List.of(LedgerPaymentMode.CASH, LedgerPaymentMode.UPI);
    private static final List<LedgerPaymentMode> BANK_MODES = List.of(
            LedgerPaymentMode.BANK, LedgerPaymentMode.CARD, LedgerPaymentMode.CHEQUE);

    @Autowired
    private UnifiedFinancialLedgerRepository unifiedFinancialLedgerRepository;

    public BalanceSummaryDTO getSummary(String location) {
        if (location == null || location.isBlank()) {
            return emptySummary();
        }
        String loc = location.trim();
        BigDecimal inHand = scale2(unifiedFinancialLedgerRepository.sumNetSignedByLocationAndPaymentModes(
                loc, LedgerTransactionType.CREDIT, IN_HAND_MODES));
        BigDecimal bank = scale2(unifiedFinancialLedgerRepository.sumNetSignedByLocationAndPaymentModes(
                loc, LedgerTransactionType.CREDIT, BANK_MODES));
        LocalDate today = LocalDate.now();
        BigDecimal todayDebitCashUpi = scale2(unifiedFinancialLedgerRepository.sumAmountByLocationDateRangeTypeModes(
                loc, today, today, LedgerTransactionType.DEBIT, IN_HAND_MODES));
        BigDecimal todayDebitBank = scale2(unifiedFinancialLedgerRepository.sumAmountByLocationDateRangeTypeModes(
                loc, today, today, LedgerTransactionType.DEBIT, BANK_MODES));
        BalanceSummaryDTO dto = new BalanceSummaryDTO();
        dto.setInHand(inHand);
        dto.setBank(bank);
        dto.setTotal(inHand.add(bank).setScale(2, RoundingMode.HALF_UP));
        dto.setTodayDebitCashUpi(todayDebitCashUpi);
        dto.setTodayDebitBank(todayDebitBank);
        return dto;
    }

    private static BalanceSummaryDTO emptySummary() {
        BalanceSummaryDTO d = new BalanceSummaryDTO();
        d.setInHand(ZERO);
        d.setBank(ZERO);
        d.setTotal(ZERO);
        d.setTodayDebitCashUpi(ZERO);
        d.setTodayDebitBank(ZERO);
        return d;
    }

    /** Sum of absolute debit amounts on CASH+UPI in range (replaces {@code daily_budget_events} expense slice). */
    public BigDecimal sumDebitCashUpiInRange(String location, LocalDate from, LocalDate to) {
        if (location == null || location.isBlank()) {
            return ZERO;
        }
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        return scale2(unifiedFinancialLedgerRepository.sumAmountByLocationDateRangeTypeModes(
                location.trim(), f, t, LedgerTransactionType.DEBIT, IN_HAND_MODES));
    }

    public List<UnifiedLedgerTransactionDTO> listTransactions(String location, LocalDate from, LocalDate to, int limit) {
        if (location == null || location.isBlank()) {
            return Collections.emptyList();
        }
        String loc = location.trim();
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) {
            LocalDate swap = f;
            f = t;
            t = swap;
        }
        int lim = Math.max(1, Math.min(limit, 2000));
        return unifiedFinancialLedgerRepository
                .findByLocationAndTxnDateBetweenOrderByTxnDateDescCreatedAtDescIdDesc(loc, f, t, PageRequest.of(0, lim))
                .getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private UnifiedLedgerTransactionDTO toDto(UnifiedFinancialLedgerEntry e) {
        UnifiedLedgerTransactionDTO d = new UnifiedLedgerTransactionDTO();
        d.setId(e.getId());
        d.setTxnDate(e.getTxnDate());
        d.setTxnType(e.getTxnType() != null ? e.getTxnType().name() : null);
        d.setAmount(e.getAmount());
        d.setPaymentMode(e.getPaymentMode() != null ? e.getPaymentMode().name() : null);
        d.setSource(e.getSource());
        d.setReferenceId(e.getReferenceId());
        d.setDescription(e.getDescription());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }

    private static BigDecimal scale2(BigDecimal v) {
        if (v == null) {
            return ZERO;
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
