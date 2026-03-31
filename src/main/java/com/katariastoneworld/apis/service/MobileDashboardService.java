package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.MobileDashboardDTO;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PWA / mobile-friendly day view: {@code financial_ledger} only (no bill header aggregates).
 */
@Service
@Transactional(readOnly = true)
public class MobileDashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    public MobileDashboardDTO buildForDate(String location, LocalDate day) {
        final String loc = location == null ? "" : location.trim();
        LocalDate d = day != null ? day : LocalDate.now();

        BigDecimal totalCredit = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(loc, d, LedgerEntryType.CREDIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(loc, d, LedgerEntryType.DEBIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = totalCredit.subtract(totalDebit).setScale(2, RoundingMode.HALF_UP);

        Map<String, Double> paymentModes = new LinkedHashMap<>();
        List<Object[]> modeRows = financialLedgerRepository.sumCreditGroupedByPaymentModeForDate(loc, d);
        for (Object[] row : modeRows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            BillPaymentMode mode = (BillPaymentMode) row[0];
            BigDecimal sum = row[1] instanceof BigDecimal b ? b : BigDecimal.ZERO;
            sum = sum.setScale(2, RoundingMode.HALF_UP);
            if (sum.compareTo(ZERO) > 0) {
                paymentModes.put(mode.name(), sum.doubleValue());
            }
        }

        return MobileDashboardDTO.builder()
                .date(d.toString())
                .totalSales(totalCredit.doubleValue())
                .totalExpense(totalDebit.doubleValue())
                .netBalance(net.doubleValue())
                .paymentModes(paymentModes)
                .build();
    }
}
