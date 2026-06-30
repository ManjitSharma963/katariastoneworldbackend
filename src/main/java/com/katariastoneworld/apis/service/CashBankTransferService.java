package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.accounting.support.CashBankTransferAccountingBridge;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferHistoryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferRequestDTO;
import com.katariastoneworld.apis.entity.CashBankTransferDirection;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CashBankTransferService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Autowired
    private CashBankTransferAccountingBridge cashBankTransferAccountingBridge;

    @Autowired
    private BalanceSummaryService balanceSummaryService;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    public List<CashBankTransferHistoryDTO> listHistory(String location, LocalDate from, LocalDate to, int limit) {
        if (location == null || location.isBlank()) {
            return Collections.emptyList();
        }
        String loc = location.trim();
        LocalDate f = from;
        LocalDate t = to;
        if (f != null && t != null && t.isBefore(f)) {
            LocalDate swap = f;
            f = t;
            t = swap;
        }
        int lim = Math.max(1, Math.min(limit, 500));
        List<MoneyTransaction> rows = moneyTransactionRepository.findCashBankTransferRows(
                loc,
                MoneyLedgerCategories.SUB_CASH_BANK_TRANSFER,
                f,
                t,
                PageRequest.of(0, lim * 2));

        Map<String, List<MoneyTransaction>> groups = new LinkedHashMap<>();
        for (MoneyTransaction row : rows) {
            String key = row.getLinkedGroupId();
            if (key == null || key.isBlank()) {
                key = row.getAdjustmentGroupId();
            }
            if (key == null || key.isBlank()) {
                key = "id-" + row.getId();
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        return groups.entrySet().stream()
                .map(e -> toHistoryDto(e.getKey(), e.getValue()))
                .filter(d -> d != null)
                .sorted(Comparator
                        .comparing(CashBankTransferHistoryDTO::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CashBankTransferHistoryDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional
    public BalanceSummaryDTO transfer(String location, CashBankTransferRequestDTO request) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        BigDecimal amt = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        CashBankTransferDirection direction = CashBankTransferDirection.parse(request.getDirection());
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();

        BalanceSummaryDTO current = balanceSummaryService.getSummary(location.trim());
        if (direction == CashBankTransferDirection.CASH_TO_BANK) {
            BigDecimal available = current.getInHand() != null ? current.getInHand() : ZERO;
            if (available.compareTo(amt) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient cash+UPI balance. Available: ₹" + available.toPlainString());
            }
        } else {
            BigDecimal available = current.getBank() != null ? current.getBank() : ZERO;
            if (available.compareTo(amt) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient bank balance. Available: ₹" + available.toPlainString());
            }
        }

        cashBankTransferAccountingBridge.postTransfer(
                location.trim(), amt, direction, date, request.getNotes());
        return balanceSummaryService.getSummary(location.trim());
    }

    private static CashBankTransferHistoryDTO toHistoryDto(String groupId, List<MoneyTransaction> legs) {
        if (legs == null || legs.isEmpty()) {
            return null;
        }
        MoneyTransaction out = legs.stream()
                .filter(r -> r.getDirection() == MoneyDirection.OUT)
                .findFirst()
                .orElse(legs.get(0));

        CashBankTransferDirection direction = out.getPaymentMode() == MoneyPaymentMode.BANK
                ? CashBankTransferDirection.BANK_TO_CASH
                : CashBankTransferDirection.CASH_TO_BANK;

        CashBankTransferHistoryDTO dto = new CashBankTransferHistoryDTO();
        dto.setTransferGroupId(groupId);
        dto.setDate(out.getTransactionDate() != null
                ? out.getTransactionDate()
                : (out.getDateTime() != null ? out.getDateTime().toLocalDate() : null));
        dto.setDirection(direction.name());
        dto.setAmount(out.getAmount() != null ? out.getAmount().setScale(2, RoundingMode.HALF_UP) : ZERO);
        dto.setNotes(out.getNotes());
        dto.setCreatedAt(out.getCreatedAt());
        return dto;
    }
}
