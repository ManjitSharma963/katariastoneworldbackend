package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.constants.BillLifecycleStatus;
import com.katariastoneworld.apis.dto.*;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.BillInventoryReturnRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enterprise bill cancellation (void) for finalized bills; hard delete only for DRAFT.
 * Preserves history via reversals — never physically deletes finalized rows.
 */
@Service
public class BillCancellationService {

    private static final BigDecimal EPS = new BigDecimal("0.005");

    private final BillService billService;
    private final BillNonGSTRepository billNonGSTRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillInventoryReturnRepository billInventoryReturnRepository;
    private final CustomerAdvanceService customerAdvanceService;

    public BillCancellationService(
            BillService billService,
            BillNonGSTRepository billNonGSTRepository,
            BillPaymentRepository billPaymentRepository,
            BillInventoryReturnRepository billInventoryReturnRepository,
            CustomerAdvanceService customerAdvanceService) {
        this.billService = billService;
        this.billNonGSTRepository = billNonGSTRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.billInventoryReturnRepository = billInventoryReturnRepository;
        this.customerAdvanceService = customerAdvanceService;
    }

    public BillCancelPreviewDTO getCancelPreview(Long billId, String billType, String location) {
        if (!"NON_GST".equalsIgnoreCase(normalizeBillType(billType))
                && !"NON-GST".equalsIgnoreCase(billType)) {
            return previewGst(billId, billType, location);
        }
        return previewNonGst(billId, location);
    }

    @Transactional
    public void cancelBill(Long billId, String billType, String location, Long actorUserId, BillCancelRequestDTO body) {
        String normalized = normalizeBillType(billType);
        if (isDraft(billId, normalized, location)) {
            hardDeleteDraft(billId, normalized, location);
            return;
        }
        validateCancellable(billId, normalized, location);
        String reason = buildCancellationReason(body);
        billService.deleteBill(billId, billType, location, actorUserId, reason);
        stampCancellationMetadata(billId, normalized, actorUserId, reason, body);
    }

    @Transactional
    public void cancelBillById(Long billId, String location, Long actorUserId, BillCancelRequestDTO body) {
        try {
            cancelBill(billId, "GST", location, actorUserId, body);
        } catch (RuntimeException ex) {
            cancelBill(billId, "NON_GST", location, actorUserId, body);
        }
    }

    @Transactional
    public void hardDeleteDraft(Long billId, String billType, String location) {
        String normalized = normalizeBillType(billType);
        if (!isDraft(billId, normalized, location)) {
            throw new IllegalArgumentException("Only DRAFT bills can be permanently deleted");
        }
        if ("GST".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Draft hard delete is supported for NON_GST bills in this release");
        }
        BillNonGST bill = billNonGSTRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!Objects.equals(location, resolveLocation(bill))) {
            throw new IllegalArgumentException("Bill not found");
        }
        bill.setIsDeleted(true);
        bill.setBillStatus(BillLifecycleStatus.CANCELLED);
        billNonGSTRepository.save(bill);
    }

    private void validateCancellable(Long billId, String billType, String location) {
        if ("GST".equalsIgnoreCase(billType)) {
            BillResponseDTO bill = billService.getBillById(billId, "GST", location);
            assertNotCancelled(bill);
            return;
        }
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!Objects.equals(location, resolveLocation(bill))) {
            throw new IllegalArgumentException("Bill not found");
        }
        if (Boolean.TRUE.equals(bill.getIsDeleted())
                || BillLifecycleStatus.CANCELLED.equalsIgnoreCase(strip(bill.getBillStatus()))
                || bill.getPaymentStatus() == BillNonGST.PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Bill is already cancelled");
        }
        List<BillNonGST> children = billNonGSTRepository.findSupplementaryByParent(
                billId, BillKind.NON_GST.name(), location.trim());
        if (children != null && children.stream().anyMatch(c -> !Boolean.TRUE.equals(c.getIsDeleted()))) {
            throw new IllegalArgumentException(
                    "Cannot cancel: active supplementary bill(s) are linked to this invoice. Cancel or settle them first.");
        }
        List<BillInventoryReturn> returns =
                billInventoryReturnRepository.findWithLinesByBillKindAndBillId(BillKind.NON_GST, billId);
        for (BillInventoryReturn ret : returns) {
            if (Boolean.FALSE.equals(ret.getSettled())
                    && ret.getRefundAmount() != null
                    && ret.getRefundAmount().compareTo(EPS) > 0) {
                throw new IllegalArgumentException(
                        "Cannot cancel: stock return #" + ret.getId() + " has pending refund settlement.");
            }
        }
        if (BillLifecycleStatus.EXCHANGED.equalsIgnoreCase(strip(bill.getBillStatus()))) {
            throw new IllegalArgumentException(
                    "Cannot cancel: bill is in EXCHANGED status. Resolve exchange / supplementary bills first.");
        }
    }

    private BillCancelPreviewDTO previewNonGst(Long billId, String location) {
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!Objects.equals(location, resolveLocation(bill))) {
            throw new IllegalArgumentException("Bill not found");
        }
        BillResponseDTO dto = billService.getBillById(billId, "NON_GST", location);
        List<BillPayment> pays =
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal advance = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = sumActiveNonAdvance(pays);
        BigDecimal inHand = sumInHand(pays);
        BigDecimal bank = paid.subtract(inHand).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefund = inHand.add(bank).add(advance).setScale(2, RoundingMode.HALF_UP);

        BillCancelPreviewDTO p = new BillCancelPreviewDTO();
        p.setBillId(billId);
        p.setBillNumber(bill.getBillNumber());
        p.setBillType("NON_GST");
        p.setBillLifecycleStatus(displayLifecycle(bill.getBillStatus()));
        p.setPaymentStatus(bill.getPaymentStatus() != null ? bill.getPaymentStatus().name() : null);
        Customer c = bill.getCustomer();
        p.setCustomerName(c != null ? c.getCustomerName() : null);
        p.setCustomerPhone(c != null ? c.getPhone() : null);
        p.setBillDate(bill.getBillDate() != null ? bill.getBillDate().toString() : null);
        p.setBillAmount(nz(bill.getTotalAmount()));
        p.setPaidAmountExcludingAdvance(paid.doubleValue());
        p.setAdvanceUsed(advance.doubleValue());
        p.setCashUpiRefund(inHand.doubleValue());
        p.setBankRefund(bank.doubleValue());
        p.setTotalRefundToCustomer(totalRefund.doubleValue());
        p.setDraftBill(isDraftStatus(bill.getBillStatus()));
        p.setAlreadyCancelled(Boolean.TRUE.equals(bill.getIsDeleted())
                || BillLifecycleStatus.CANCELLED.equalsIgnoreCase(strip(bill.getBillStatus())));

        if (dto.getReturnSummary() != null) {
            p.setEffectiveBillTotalAfterReturns(dto.getReturnSummary().getEffectiveBillTotal());
            BigDecimal eff = BigDecimal.valueOf(
                    dto.getReturnSummary().getEffectiveBillTotal() != null
                            ? dto.getReturnSummary().getEffectiveBillTotal()
                            : 0);
            BigDecimal bal = eff.subtract(advance).subtract(paid).max(BigDecimal.ZERO);
            p.setBalanceStillDue(bal.doubleValue());
        } else {
            p.setEffectiveBillTotalAfterReturns(nz(bill.getTotalAmount()));
            p.setBalanceStillDue(0.0);
        }

        if (c != null && c.getId() != null) {
            BigDecimal walletNow = customerAdvanceService.getAvailableWalletBalance(c.getId());
            p.setCustomerAdvanceBalanceAfter(walletNow.add(advance).doubleValue());
        }

        Map<Long, BigDecimal> returnedByLine = billService.getReturnedQuantitiesByLine(BillKind.NON_GST, billId);
        if (bill.getItems() != null) {
            for (BillItemNonGST line : bill.getItems()) {
                BigDecimal sold = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
                BigDecimal ret = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
                BigDecimal restore = sold.subtract(ret).max(BigDecimal.ZERO);
                p.getItems().add(new BillCancelPreviewItemDTO(
                        line.getId(),
                        line.getProductName(),
                        line.getProductType(),
                        sold.doubleValue(),
                        line.getUnit() != null ? line.getUnit() : "sq.ft",
                        line.getPricePerUnit() != null ? line.getPricePerUnit().doubleValue() : 0,
                        line.getItemTotalPrice() != null ? line.getItemTotalPrice().doubleValue() : 0));
                if (restore.compareTo(EPS) > 0) {
                    p.getInventoryImpact().add(new BillCancelPreviewInventoryDTO(
                            line.getProductName(),
                            restore.doubleValue(),
                            line.getUnit() != null ? line.getUnit() : "sq.ft"));
                }
            }
        }
        for (BillPayment pay : pays) {
            if (Boolean.TRUE.equals(pay.getIsDeleted())) {
                continue;
            }
            boolean adv = isAdvancePayment(pay);
            p.getPayments().add(new BillCancelPreviewPaymentDTO(
                    pay.getId(),
                    pay.getPaymentDate() != null ? pay.getPaymentDate().toString() : null,
                    pay.getPaymentMode() != null ? pay.getPaymentMode().name() : "OTHER",
                    pay.getSourceType(),
                    null,
                    pay.getAmount() != null ? pay.getAmount().doubleValue() : 0,
                    adv));
        }
        return p;
    }

    private BillCancelPreviewDTO previewGst(Long billId, String billType, String location) {
        BillResponseDTO dto = billService.getBillById(billId, "GST", location);
        BillCancelPreviewDTO p = new BillCancelPreviewDTO();
        p.setBillId(billId);
        p.setBillNumber(dto.getBillNumber());
        p.setBillType("GST");
        p.setBillLifecycleStatus(displayLifecycle(dto.getBillLifecycleStatus()));
        p.setPaymentStatus(dto.getPaymentStatus());
        p.setCustomerName(dto.getCustomerName());
        p.setCustomerPhone(dto.getCustomerMobileNumber());
        p.setBillDate(dto.getBillDate() != null ? dto.getBillDate().toString() : null);
        p.setBillAmount(dto.getTotalAmount());
        p.setPaidAmountExcludingAdvance(dto.getTotalPaid());
        p.setAdvanceUsed(dto.getAdvanceUsed());
        p.setDraftBill(isDraftStatus(dto.getBillLifecycleStatus()));
        p.setAlreadyCancelled("CANCELLED".equalsIgnoreCase(dto.getPaymentStatus()));
        return p;
    }

    private void stampCancellationMetadata(
            Long billId, String billType, Long actorUserId, String reason, BillCancelRequestDTO body) {
        if (!"NON_GST".equalsIgnoreCase(billType)) {
            return;
        }
        billNonGSTRepository.findById(billId).ifPresent(bill -> {
            bill.setCancelledAt(java.time.LocalDateTime.now());
            bill.setCancelledByUserId(actorUserId);
            bill.setCancellationReason(reason);
            billNonGSTRepository.save(bill);
        });
    }

    private static String buildCancellationReason(BillCancelRequestDTO body) {
        if (body == null) {
            return null;
        }
        String code = body.getReasonCode() != null ? body.getReasonCode().trim() : "";
        String detail = body.getReason() != null ? body.getReason().trim() : "";
        if (!code.isEmpty() && !detail.isEmpty()) {
            return code + ": " + detail;
        }
        if (!detail.isEmpty()) {
            return detail;
        }
        return code.isEmpty() ? null : code;
    }

    private boolean isDraft(Long billId, String billType, String location) {
        if ("GST".equalsIgnoreCase(billType)) {
            try {
                BillResponseDTO d = billService.getBillById(billId, "GST", location);
                return isDraftStatus(d.getBillLifecycleStatus());
            } catch (Exception e) {
                return false;
            }
        }
        return billNonGSTRepository.findById(billId)
                .map(b -> isDraftStatus(b.getBillStatus()))
                .orElse(false);
    }

    private static boolean isDraftStatus(String status) {
        return BillLifecycleStatus.DRAFT.equalsIgnoreCase(strip(status));
    }

    private static String displayLifecycle(String status) {
        if (status == null || status.isBlank()) {
            return BillLifecycleStatus.FINALIZED;
        }
        if (BillLifecycleStatus.COMPLETED.equalsIgnoreCase(status.trim())
                || BillLifecycleStatus.ACTIVE.equalsIgnoreCase(status.trim())) {
            return BillLifecycleStatus.FINALIZED;
        }
        return status.trim();
    }

    private static void assertNotCancelled(BillResponseDTO bill) {
        if ("CANCELLED".equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new IllegalArgumentException("Bill is already cancelled");
        }
    }

    private static String normalizeBillType(String billType) {
        return billType != null ? billType.replace('-', '_').toUpperCase() : "NON_GST";
    }

    private static String strip(String s) {
        return s != null ? s.trim() : "";
    }

    private static String resolveLocation(BillNonGST bill) {
        if (bill.getLocation() != null && !bill.getLocation().isBlank()) {
            return bill.getLocation().trim();
        }
        return bill.getCustomer() != null && bill.getCustomer().getLocation() != null
                ? bill.getCustomer().getLocation().trim()
                : "";
    }

    private static double nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP).doubleValue() : 0;
    }

    private static BigDecimal sumActiveNonAdvance(List<BillPayment> rows) {
        BigDecimal s = BigDecimal.ZERO;
        if (rows == null) {
            return s;
        }
        for (BillPayment p : rows) {
            if (Boolean.TRUE.equals(p.getIsDeleted()) || isAdvancePayment(p) || p.getAmount() == null) {
                continue;
            }
            s = s.add(p.getAmount());
        }
        return s.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumInHand(List<BillPayment> rows) {
        BigDecimal s = BigDecimal.ZERO;
        if (rows == null) {
            return s;
        }
        for (BillPayment p : rows) {
            if (Boolean.TRUE.equals(p.getIsDeleted()) || isAdvancePayment(p) || p.getAmount() == null) {
                continue;
            }
            if (p.getPaymentMode() == BillPaymentMode.CASH || p.getPaymentMode() == BillPaymentMode.UPI) {
                s = s.add(p.getAmount());
            }
        }
        return s.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isAdvancePayment(BillPayment p) {
        if (p == null) {
            return false;
        }
        String st = p.getSourceType();
        return st != null && (st.equalsIgnoreCase("ADVANCE") || st.contains("WALLET"));
    }
}
