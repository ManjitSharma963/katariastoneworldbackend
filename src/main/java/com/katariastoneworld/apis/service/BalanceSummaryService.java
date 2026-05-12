package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.dto.UnifiedLedgerTransactionDTO;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
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
 * Balances and ledger listings from {@code transactions} (CASH/UPI vs BANK rails).
 */
@Service
@Transactional(readOnly = true)
public class BalanceSummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final List<MoneyPaymentMode> IN_HAND_MODES = List.of(MoneyPaymentMode.CASH, MoneyPaymentMode.UPI);
    private static final List<MoneyPaymentMode> BANK_MODES = List.of(MoneyPaymentMode.BANK);

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    public BalanceSummaryDTO getSummary(String location) {
        if (location == null || location.isBlank()) {
            return emptySummary();
        }
        String loc = location.trim();
        BigDecimal inHand = scale2(moneyTransactionRepository.sumNetSignedByLocationAndPaymentModes(loc, IN_HAND_MODES));
        BigDecimal bank = scale2(moneyTransactionRepository.sumNetSignedByLocationAndPaymentModes(loc, BANK_MODES));
        LocalDate today = LocalDate.now();
        BigDecimal todayDebitCashUpi = scale2(moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                loc, today, today, MoneyDirection.OUT, IN_HAND_MODES));
        BigDecimal todayDebitBank = scale2(moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                loc, today, today, MoneyDirection.OUT, BANK_MODES));
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

    /** Sum of OUT amounts on CASH+UPI in range (expense / in-hand outflows). */
    public BigDecimal sumDebitCashUpiInRange(String location, LocalDate from, LocalDate to) {
        if (location == null || location.isBlank()) {
            return ZERO;
        }
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        return scale2(moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                location.trim(), f, t, MoneyDirection.OUT, IN_HAND_MODES));
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
        return moneyTransactionRepository
                .findByLocationAndTransactionDateBetweenAndIsDeletedFalseOrderByTransactionDateDescDateTimeDescIdDesc(
                        loc, f, t, PageRequest.of(0, lim))
                .getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private UnifiedLedgerTransactionDTO toDto(MoneyTransaction e) {
        UnifiedLedgerTransactionDTO d = new UnifiedLedgerTransactionDTO();
        d.setId(e.getId());
        d.setTxnDate(e.getTransactionDate() != null ? e.getTransactionDate() : (e.getDateTime() != null ? e.getDateTime().toLocalDate() : null));
        d.setTxnType(e.getDirection() != null ? e.getDirection().name() : null);
        d.setAmount(e.getAmount());
        d.setPaymentMode(e.getPaymentMode() != null ? e.getPaymentMode().name() : null);
        d.setSource(e.getCategory() != null ? e.getCategory().name() : null);
        d.setReferenceId(e.getReferenceId());
        d.setDescription(e.getNotes());
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
