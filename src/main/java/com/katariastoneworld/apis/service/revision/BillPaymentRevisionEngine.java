package com.katariastoneworld.apis.service.revision;

import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.dto.BillPaymentResponseDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillRevisionIntegrityReportDTO;
import com.katariastoneworld.apis.service.BillService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment adjustment engine: append payment lines, refresh derived settlement, integrity checks (no hard deletes).
 */
@Component
public class BillPaymentRevisionEngine {

    private static final BigDecimal EPS = new BigDecimal("0.02");

    private final BillService billService;

    public BillPaymentRevisionEngine(BillService billService) {
        this.billService = billService;
    }

    public BillResponseDTO appendPayment(
            Long billId, String billType, BillPaymentRequestDTO paymentRequest, String location, Long actorUserId) {
        return billService.addPaymentToBill(billId, billType, paymentRequest, location, actorUserId);
    }

    public BillResponseDTO refreshOutstanding(Long billId, String billType, String location) {
        return billService.refreshOutstandingAndPaymentStatus(billId, billType, location);
    }

    public BillRevisionIntegrityReportDTO verifySettlement(BillResponseDTO bill) {
        List<String> findings = new ArrayList<>();
        BigDecimal total = bd(bill.getTotalAmount());
        BigDecimal adv = bd(bill.getAdvanceUsed());
        BigDecimal paid = bd(bill.getPaidAmount());
        BigDecimal due = bd(bill.getAmountDue());
        BigDecimal expectedDue = total.subtract(adv).subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (expectedDue.subtract(due).abs().compareTo(EPS) > 0) {
            findings.add(String.format(
                    "amountDue (%.2f) differs from total − advance − paid (%.2f)",
                    due.doubleValue(), expectedDue.doubleValue()));
        }
        BigDecimal sumNonWallet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (bill.getPayments() != null) {
            for (BillPaymentResponseDTO p : bill.getPayments()) {
                if (p == null || p.getAmount() == null) {
                    continue;
                }
                String mode = p.getPaymentMode() != null ? p.getPaymentMode() : "";
                if ("WALLET".equalsIgnoreCase(mode)) {
                    continue;
                }
                sumNonWallet = sumNonWallet.add(BigDecimal.valueOf(p.getAmount()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (sumNonWallet.subtract(paid).abs().compareTo(EPS) > 0) {
            findings.add(String.format(
                    "Sum of non-wallet payment lines (%.2f) differs from paidAmount (%.2f)",
                    sumNonWallet.doubleValue(), paid.doubleValue()));
        }
        String ps = bill.getPaymentStatus();
        if (ps != null) {
            BigDecimal covered = adv.add(paid).setScale(2, RoundingMode.HALF_UP);
            if (covered.compareTo(total.add(EPS)) > 0 && !"REFUND_PENDING".equalsIgnoreCase(ps)) {
                findings.add("paymentStatus should be REFUND_PENDING when covered exceeds total");
            }
            if (expectedDue.compareTo(EPS) > 0 && "PAID".equalsIgnoreCase(ps)) {
                findings.add("paymentStatus is PAID but amountDue > 0");
            }
        }
        return BillRevisionIntegrityReportDTO.builder()
                .consistent(findings.isEmpty())
                .findings(findings)
                .build();
    }

    private static BigDecimal bd(Double v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
