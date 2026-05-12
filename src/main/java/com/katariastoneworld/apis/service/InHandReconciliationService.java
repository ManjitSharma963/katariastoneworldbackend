package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.InHandReconciliationDTO;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Cross-check bill CASH/UPI payment totals vs {@code transactions} (BILL category, same payment modes).
 */
@Service
@Transactional(readOnly = true)
public class InHandReconciliationService {

    private static final BigDecimal EPS = new BigDecimal("0.02");

    private static final List<MoneyPaymentMode> CASH_UPI = List.of(MoneyPaymentMode.CASH, MoneyPaymentMode.UPI);

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    public InHandReconciliationDTO reconcileInHandBillPayments(String location, LocalDate from, LocalDate to) {
        String loc = location != null ? location.trim() : "";
        LocalDate f = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) {
            t = f;
        }

        BigDecimal gst = billPaymentRepository.sumCashUpiGstForLocationAndPaymentDateRange(loc, f, t);
        BigDecimal nongst = billPaymentRepository.sumCashUpiNonGstForLocationAndPaymentDateRange(loc, f, t);
        BigDecimal billTotal = gst.add(nongst).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ledger = moneyTransactionRepository.sumNetBillInHandByLocationDateRangeModes(loc, f, t, CASH_UPI);
        if (ledger == null) {
            ledger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            ledger = ledger.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal diff = billTotal.subtract(ledger).setScale(2, RoundingMode.HALF_UP);
        boolean match = diff.abs().compareTo(EPS) <= 0;

        String notes = "Compares sum of CASH/UPI bill_payments (active bills, non-deleted payments, payment_date in range) "
                + "to net BILL-category CASH/UPI amounts in transactions for the same range.";

        InHandReconciliationDTO dto = new InHandReconciliationDTO();
        dto.setLocation(loc);
        dto.setFrom(f);
        dto.setTo(t);
        dto.setBillCashUpiTotal(billTotal);
        dto.setLedgerBillPaymentInHandTotal(ledger);
        dto.setDifference(diff);
        dto.setMatch(match);
        dto.setNotes(notes);
        return dto;
    }
}
