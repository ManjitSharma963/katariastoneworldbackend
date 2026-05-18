package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.constants.BillAdjustmentSettlementMethod;
import com.katariastoneworld.apis.constants.BillAdjustmentType;
import com.katariastoneworld.apis.constants.BillLifecycleStatus;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.dto.*;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.BillInventoryReturnRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NON-GST bill adjustment / exchange orchestration (additive, traceable).
 * Original bill lines and payments are never destructively overwritten.
 */
@Service
public class BillAdjustmentService {

    private static final BigDecimal EPS = new BigDecimal("0.005");

    private final BillService billService;
    private final BillNonGSTRepository billNonGSTRepository;
    private final BillInventoryReturnRepository billInventoryReturnRepository;
    private final MoneyTransactionRepository moneyTransactionRepository;
    private final CustomerAdvanceService customerAdvanceService;

    public BillAdjustmentService(
            BillService billService,
            BillNonGSTRepository billNonGSTRepository,
            BillInventoryReturnRepository billInventoryReturnRepository,
            MoneyTransactionRepository moneyTransactionRepository,
            CustomerAdvanceService customerAdvanceService) {
        this.billService = billService;
        this.billNonGSTRepository = billNonGSTRepository;
        this.billInventoryReturnRepository = billInventoryReturnRepository;
        this.moneyTransactionRepository = moneyTransactionRepository;
        this.customerAdvanceService = customerAdvanceService;
    }

    public void assertNonGstBillType(String billType) {
        if (billType == null || !billType.replace('-', '_').equalsIgnoreCase("NON_GST")) {
            throw new IllegalArgumentException("Adjustment workflow is supported for NON_GST bills only");
        }
    }

    public String newAdjustmentGroupId() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ADJ-" + day + "-" + seq;
    }

    public BillAdjustmentSessionDTO openAdjustmentSession(Long billId, String location) {
        BillResponseDTO bill = billService.getNonGstBillForAdjustmentSession(billId, location);
        BillAdjustmentSessionDTO session = new BillAdjustmentSessionDTO();
        session.setAdjustmentGroupId(newAdjustmentGroupId());
        session.setOriginalBill(bill);
        session.setReturnSummary(bill.getReturnSummary());
        session.setReturnHistory(bill.getReturnHistory() != null ? bill.getReturnHistory() : List.of());
        session.setSupplementaryBills(bill.getSupplementaryBills() != null ? bill.getSupplementaryBills() : List.of());
        session.setBillLifecycleStatus(bill.getBillLifecycleStatus());
        session.setPaymentStatus(bill.getPaymentStatus());
        session.setAdvanceUsed(bill.getAdvanceUsed());
        session.setTotalPaid(bill.getTotalPaid());

        if (bill.getItems() != null) {
            for (BillItemDTO it : bill.getItems()) {
                Long id = it.getItemId();
                if (id == null) {
                    continue;
                }
                double sold = it.getQuantity() != null ? it.getQuantity() : 0;
                double rtd = it.getQuantityReturnedToDate() != null ? it.getQuantityReturnedToDate() : 0;
                double max = it.getQuantityReturnable() != null ? it.getQuantityReturnable() : Math.max(0, sold - rtd);
                BillAdjustmentSessionLineDTO line = new BillAdjustmentSessionLineDTO();
                line.setBillItemId(id);
                line.setProductName(it.getItemName());
                line.setBatchOrLot(firstNonBlank(it.getCategory()));
                line.setUnit(it.getUnit() != null ? it.getUnit() : "sq.ft");
                line.setSoldQuantity(sold);
                line.setReturnedAlready(rtd);
                line.setReturnableQuantity(max);
                line.setPricePerUnit(it.getPricePerUnit());
                session.getLines().add(line);
            }
        }

        BillReturnSummaryDTO rs = bill.getReturnSummary();
        BillAdjustmentSettlementPreviewDTO preview = new BillAdjustmentSettlementPreviewDTO();
        if (rs != null) {
            preview.setOriginalBillAmount(rs.getOriginalInvoiceTotalAmount());
            preview.setCumulativeReturnedValue(rs.getCumulativeReturnedValue());
            preview.setEffectiveBillAmount(rs.getEffectiveBillTotal());
            preview.setSuggestedRefundVersusEffective(rs.getSuggestedCustomerRefundVersusEffective());
        } else {
            preview.setOriginalBillAmount(bill.getTotalAmount());
            preview.setCumulativeReturnedValue(0.0);
            preview.setEffectiveBillAmount(bill.getTotalAmount());
            preview.setSuggestedRefundVersusEffective(0.0);
        }
        preview.setAdvanceUsed(bill.getAdvanceUsed());
        preview.setTotalPaid(bill.getTotalPaid());
        session.setSettlementPreview(preview);
        return session;
    }

    @Transactional
    public BillFinalizeAdjustmentResponseDTO finalizeAdjustment(
            Long billId,
            BillFinalizeAdjustmentRequestDTO request,
            String location,
            Long actorUserId,
            String userRole) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        boolean hasReturn = request.getStockReturn() != null
                && request.getStockReturn().getLines() != null
                && !request.getStockReturn().getLines().isEmpty();
        boolean hasSupp = request.getSupplementaryBill() != null
                && request.getSupplementaryBill().getItems() != null
                && !request.getSupplementaryBill().getItems().isEmpty();
        if (!hasReturn && !hasSupp) {
            throw new IllegalArgumentException("At least one return line or supplementary item is required");
        }

        String groupId = request.getAdjustmentGroupId() != null && !request.getAdjustmentGroupId().isBlank()
                ? request.getAdjustmentGroupId().trim()
                : newAdjustmentGroupId();

        BillNonGST parent = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String parentLocation = parent.getLocation() != null && !parent.getLocation().isBlank()
                ? parent.getLocation().trim()
                : (parent.getCustomer() != null && parent.getCustomer().getLocation() != null
                        ? parent.getCustomer().getLocation().trim()
                        : null);
        if (!Objects.equals(location, parentLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }

        BillStockReturnResponseDTO returnResp = null;
        BigDecimal returnValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (hasReturn) {
            BillStockReturnRequestDTO sr = request.getStockReturn();
            sr.setAdjustmentGroupId(groupId);
            sr.setRefundMode(resolveReturnRefundMode(request, hasSupp));
            if (request.getNotes() != null && !request.getNotes().isBlank()) {
                sr.setNotes(mergeNotes(sr.getNotes(), request.getNotes()));
            }
            returnResp = billService.recordPartialStockReturn(billId, "NON_GST", sr, location, actorUserId);
            returnValue = nz(returnResp.getComputedReturnAmount());
            tagTransactionsByTxnType(MoneyReferenceType.bill, billId, "STOCK_RETURN_" + returnResp.getReturnId(), groupId);
        }

        BillResponseDTO supplementaryResp = null;
        BigDecimal newItemsValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (hasSupp) {
            BillRequestDTO sup = request.getSupplementaryBill();
            if (sup.getSupplementaryReason() == null || sup.getSupplementaryReason().isBlank()) {
                sup.setSupplementaryReason(
                        request.getAdjustmentReason() != null
                                ? request.getAdjustmentReason()
                                : "Exchange / adjustment linked to bill " + parent.getBillNumber());
            }
            ensureSupplementaryCollectionPayment(sup, request);
            supplementaryResp = billService.createSupplementaryBill(
                    billId, "NON_GST", sup, location, actorUserId, userRole != null ? userRole : "user");
            newItemsValue = BigDecimal.valueOf(
                            supplementaryResp.getTotalAmount() != null ? supplementaryResp.getTotalAmount() : 0)
                    .setScale(2, RoundingMode.HALF_UP);
            tagSupplementaryBillTransactions(supplementaryResp.getId(), groupId);
        }

        BigDecimal diff = newItemsValue.subtract(returnValue).setScale(2, RoundingMode.HALF_UP);
        applyNetSettlement(
                parent,
                groupId,
                request,
                diff,
                location,
                actorUserId);

        if (returnResp != null) {
            BigDecimal postedRefund = resolvePostedRefundForReturnHeader(request, returnResp, diff);
            stampReturnHeader(returnResp, groupId, srRefundModeForHeader(request, hasSupp), request, postedRefund);
        }

        String adjType = request.getAdjustmentType() != null && !request.getAdjustmentType().isBlank()
                ? request.getAdjustmentType().trim()
                : inferAdjustmentType(hasReturn, hasSupp);
        parent.setAdjustmentType(adjType);
        parent.setAdjustmentReason(request.getAdjustmentReason());
        if (hasSupp && !BillLifecycleStatus.ADJUSTED.equalsIgnoreCase(parent.getBillStatus())) {
            parent.setBillStatus(BillLifecycleStatus.ADJUSTED);
        }
        billNonGSTRepository.save(parent);

        BillResponseDTO parentDto = billService.getBillById(billId, "NON_GST", location);
        BillFinalizeAdjustmentResponseDTO response = new BillFinalizeAdjustmentResponseDTO();
        response.setAdjustmentGroupId(groupId);
        response.setStockReturn(returnResp);
        response.setSupplementaryBill(supplementaryResp);
        response.setParentBill(parentDto);
        response.setSettlement(buildSettlementPreview(parentDto));
        response.setTimeline(buildTimeline(parent, returnResp, supplementaryResp, groupId, request));
        return response;
    }

    public BillAdjustmentHistoryDTO getAdjustmentHistory(Long billId, String location) {
        BillResponseDTO bill = billService.getBillById(billId, "NON_GST", location);
        BillAdjustmentHistoryDTO hist = new BillAdjustmentHistoryDTO();
        hist.setBillId(billId);
        hist.setBillNumber(bill.getBillNumber());
        hist.setReturnSummary(bill.getReturnSummary());
        hist.setReturns(bill.getReturnHistory() != null ? bill.getReturnHistory() : List.of());
        hist.setSupplementaryBills(bill.getSupplementaryBills() != null ? bill.getSupplementaryBills() : List.of());
        hist.setEvents(bill.getBillEvents() != null ? bill.getBillEvents() : List.of());
        hist.setTimeline(buildHistoryTimeline(bill));
        return hist;
    }

    public BillStockReturnResponseDTO recordReturn(
            Long billId,
            BillStockReturnRequestDTO request,
            String location,
            Long actorUserId) {
        if (request.getAdjustmentGroupId() == null || request.getAdjustmentGroupId().isBlank()) {
            request.setAdjustmentGroupId(newAdjustmentGroupId());
        }
        BillStockReturnResponseDTO resp =
                billService.recordPartialStockReturn(billId, "NON_GST", request, location, actorUserId);
        stampReturnHeader(resp, request.getAdjustmentGroupId(), request.getRefundMode(), null, null);
        tagTransactionsByTxnType(
                MoneyReferenceType.bill, billId, "STOCK_RETURN_" + resp.getReturnId(), request.getAdjustmentGroupId());
        return resp;
    }

    private void applyNetSettlement(
            BillNonGST parent,
            String groupId,
            BillFinalizeAdjustmentRequestDTO request,
            BigDecimal diff,
            String location,
            Long actorUserId) {
        String method = request.getSettlementMethod() != null
                ? request.getSettlementMethod().trim().toUpperCase()
                : BillAdjustmentSettlementMethod.NONE;
        BigDecimal amount = request.getSettlementAmount() != null
                ? request.getSettlementAmount().setScale(2, RoundingMode.HALF_UP)
                : diff.abs();

        if (BillAdjustmentSettlementMethod.COLLECT.equals(method) && diff.compareTo(EPS) > 0) {
            if (amount.compareTo(EPS) <= 0) {
                amount = diff;
            }
            String txnType = "ADJ_SETTLE_COLLECT_" + groupId;
            if (!moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                    MoneyReferenceType.bill, parent.getId(), txnType)) {
                postMoney(
                        parent,
                        groupId,
                        amount,
                        MoneyDirection.IN,
                        MoneyCategory.BILL,
                        MoneyLedgerCategories.SUB_ADJUSTMENT_PAYMENT,
                        txnType,
                        mapPaymentMode(request.getPaymentMode()),
                        request.getTransactionDate(),
                        request.getReference(),
                        location,
                        actorUserId);
            }
            return;
        }

        if (BillAdjustmentSettlementMethod.REFUND.equals(method) && diff.compareTo(EPS) < 0) {
            if (amount.compareTo(EPS) <= 0) {
                amount = diff.abs();
            }
            if (hasStockReturnRefundForGroup(parent.getId(), groupId)) {
                return;
            }
            String txnType = "ADJ_SETTLE_REFUND_" + groupId;
            if (!moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                    MoneyReferenceType.bill, parent.getId(), txnType)) {
                postMoney(
                        parent,
                        groupId,
                        amount,
                        MoneyDirection.OUT,
                        MoneyCategory.BILL_RETURN,
                        MoneyLedgerCategories.SUB_ADJUSTMENT_REFUND,
                        txnType,
                        mapPaymentMode(request.getPaymentMode()),
                        request.getTransactionDate(),
                        request.getReference(),
                        location,
                        actorUserId);
            }
            return;
        }

        if (BillAdjustmentSettlementMethod.ADVANCE.equals(method)) {
            if (parent.getCustomer() == null || parent.getCustomer().getId() == null) {
                throw new IllegalArgumentException("Customer is required for advance settlement");
            }
            if (hasStockReturnRefundForGroup(parent.getId(), groupId)) {
                return;
            }
            BigDecimal credit = amount.compareTo(EPS) > 0 ? amount : diff.abs();
            if (credit.compareTo(EPS) > 0) {
                customerAdvanceService.creditBillReturnToWalletIfAbsent(
                        parent.getCustomer(),
                        credit,
                        BillKind.NON_GST,
                        parent.getId(),
                        parent.getBillNumber(),
                        null);
            }
        }
    }

    /** Legacy rows: stock return already posted BILL_RETURN before adjustment settlement existed. */
    private boolean hasStockReturnRefundForGroup(Long billId, String groupId) {
        if (billId == null || groupId == null || groupId.isBlank()) {
            return false;
        }
        return moneyTransactionRepository
                .findByReferenceTypeAndReferenceIdAndCategoryAndIsDeletedFalse(
                        MoneyReferenceType.bill, billId, MoneyCategory.BILL_RETURN)
                .stream()
                .anyMatch(tx -> tx.getTxnType() != null
                        && tx.getTxnType().startsWith("STOCK_RETURN_")
                        && (groupId.equals(tx.getAdjustmentGroupId()) || groupId.equals(tx.getLinkedGroupId())));
    }

    private void postMoney(
            BillNonGST bill,
            String groupId,
            BigDecimal amount,
            MoneyDirection direction,
            MoneyCategory category,
            String subCategory,
            String txnType,
            MoneyPaymentMode paymentMode,
            LocalDate transactionDate,
            String reference,
            String location,
            Long actorUserId) {
        Customer customer = bill.getCustomer();
        String partyName = customer != null && customer.getCustomerName() != null
                ? customer.getCustomerName().trim()
                : "Customer";
        MoneyTransaction tx = new MoneyTransaction();
        tx.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        tx.setDirection(direction);
        tx.setCategory(category);
        tx.setSubCategory(subCategory);
        tx.setTxnType(txnType);
        tx.setAdjustmentGroupId(groupId);
        tx.setLinkedGroupId(groupId);
        tx.setPartyId(customer != null ? customer.getId() : null);
        tx.setPartyName(partyName);
        tx.setPaymentMode(paymentMode);
        tx.setReferenceType(MoneyReferenceType.bill);
        tx.setReferenceId(bill.getId());
        tx.setNotes(reference != null && !reference.isBlank() ? reference : "Adjustment settlement " + groupId);
        tx.setTransactionDate(transactionDate != null ? transactionDate : bill.getBillDate());
        tx.setDateTime(LocalDateTime.now());
        tx.setLocation(location != null ? location.trim() : "");
        tx.setOwnerUserId(actorUserId);
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        moneyTransactionRepository.save(tx);
    }

    private BillReturnRefundMode resolveReturnRefundMode(BillFinalizeAdjustmentRequestDTO request, boolean hasSupp) {
        String method = request.getSettlementMethod() != null
                ? request.getSettlementMethod().trim().toUpperCase()
                : BillAdjustmentSettlementMethod.NONE;
        if (hasSupp && BillAdjustmentSettlementMethod.COLLECT.equals(method)) {
            return BillReturnRefundMode.NO_REFUND;
        }
        if (BillAdjustmentSettlementMethod.REFUND.equals(method)
                || BillAdjustmentSettlementMethod.ADVANCE.equals(method)) {
            // Payout/credit is posted once via applyNetSettlement — not again on the stock-return row.
            return BillReturnRefundMode.NO_REFUND;
        }
        return BillReturnRefundMode.NO_REFUND;
    }

    private void ensureSupplementaryCollectionPayment(BillRequestDTO sup, BillFinalizeAdjustmentRequestDTO request) {
        if (!BillAdjustmentSettlementMethod.COLLECT.equalsIgnoreCase(
                request.getSettlementMethod() != null ? request.getSettlementMethod() : "")) {
            return;
        }
        BigDecimal amt = request.getSettlementAmount();
        if (amt == null || amt.compareTo(EPS) <= 0) {
            return;
        }
        if (sup.getPayments() == null) {
            sup.setPayments(new ArrayList<>());
        }
        double amtVal = amt.doubleValue();
        boolean already = sup.getPayments().stream()
                .anyMatch(p -> p.getAmount() != null && p.getAmount() >= amtVal - 0.005);
        if (!already) {
            BillPaymentRequestDTO pay = new BillPaymentRequestDTO();
            pay.setAmount(amtVal);
            pay.setPaymentMode(request.getPaymentMode() != null ? request.getPaymentMode() : "CASH");
            sup.getPayments().add(pay);
        }
    }

    private static BillReturnRefundMode srRefundModeForHeader(
            BillFinalizeAdjustmentRequestDTO request, boolean hasSupp) {
        String method = request.getSettlementMethod() != null
                ? request.getSettlementMethod().trim().toUpperCase()
                : BillAdjustmentSettlementMethod.NONE;
        if (BillAdjustmentSettlementMethod.REFUND.equals(method)) {
            String mode = request.getPaymentMode() != null ? request.getPaymentMode().trim().toUpperCase() : "CASH";
            if (mode.contains("BANK")) {
                return BillReturnRefundMode.BANK_REFUND;
            }
            return BillReturnRefundMode.CASH_REFUND;
        }
        if (BillAdjustmentSettlementMethod.ADVANCE.equals(method)) {
            return BillReturnRefundMode.WALLET_CREDIT;
        }
        if (hasSupp && BillAdjustmentSettlementMethod.COLLECT.equals(method)) {
            return BillReturnRefundMode.NO_REFUND;
        }
        return BillReturnRefundMode.NO_REFUND;
    }

    private static BigDecimal resolvePostedRefundForReturnHeader(
            BillFinalizeAdjustmentRequestDTO request,
            BillStockReturnResponseDTO returnResp,
            BigDecimal diff) {
        String method = request.getSettlementMethod() != null
                ? request.getSettlementMethod().trim().toUpperCase()
                : BillAdjustmentSettlementMethod.NONE;
        if (BillAdjustmentSettlementMethod.REFUND.equals(method) && diff.compareTo(EPS) < 0) {
            BigDecimal amount = request.getSettlementAmount() != null
                    ? request.getSettlementAmount().setScale(2, RoundingMode.HALF_UP)
                    : diff.abs();
            return amount.compareTo(EPS) > 0 ? amount : nz(returnResp.getComputedReturnAmount());
        }
        return nz(returnResp.getPostedSettlementAmount());
    }

    private void stampReturnHeader(
            BillStockReturnResponseDTO resp,
            String groupId,
            BillReturnRefundMode mode,
            BillFinalizeAdjustmentRequestDTO request,
            BigDecimal postedSettlementOverride) {
        if (resp == null || resp.getReturnId() == null) {
            return;
        }
        BigDecimal posted = postedSettlementOverride != null
                ? postedSettlementOverride
                : nz(resp.getPostedSettlementAmount());
        billInventoryReturnRepository.findById(resp.getReturnId()).ifPresent(header -> {
            header.setAdjustmentGroupId(groupId);
            header.setRefundMode(mapRefundModeStorage(mode, request));
            header.setRefundAmount(posted);
            boolean settled = mode == BillReturnRefundMode.NO_REFUND
                    || posted.compareTo(EPS) > 0;
            header.setSettled(settled);
            if (settled) {
                header.setSettledAt(LocalDateTime.now());
            }
            billInventoryReturnRepository.save(header);
        });
    }

    private static String mapRefundModeStorage(BillReturnRefundMode mode, BillFinalizeAdjustmentRequestDTO request) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case CASH_REFUND -> {
                String pm = request != null && request.getPaymentMode() != null
                        ? request.getPaymentMode().trim().toUpperCase()
                        : "CASH";
                yield pm.contains("UPI") ? "UPI" : "CASH";
            }
            case BANK_REFUND -> "BANK_TRANSFER";
            case WALLET_CREDIT, ADVANCE_RESTORE -> "ADVANCE";
            default -> null;
        };
    }

    private void tagTransactionsByTxnType(
            MoneyReferenceType refType, Long refId, String txnType, String groupId) {
        moneyTransactionRepository
                .findByReferenceTypeAndReferenceIdAndCategoryAndIsDeletedFalse(refType, refId, MoneyCategory.BILL_RETURN)
                .stream()
                .filter(tx -> txnType.equals(tx.getTxnType()))
                .forEach(tx -> {
                    tx.setAdjustmentGroupId(groupId);
                    tx.setLinkedGroupId(groupId);
                    moneyTransactionRepository.save(tx);
                });
    }

    private void tagSupplementaryBillTransactions(Long supplementaryBillId, String groupId) {
        if (supplementaryBillId == null) {
            return;
        }
        moneyTransactionRepository
                .findByReferenceTypeAndReferenceIdAndCategoryAndIsDeletedFalse(
                        MoneyReferenceType.bill, supplementaryBillId, MoneyCategory.BILL)
                .forEach(tx -> {
                    tx.setAdjustmentGroupId(groupId);
                    if (tx.getLinkedGroupId() == null || tx.getLinkedGroupId().isBlank()) {
                        tx.setLinkedGroupId(groupId);
                    }
                    moneyTransactionRepository.save(tx);
                });
    }

    private List<BillAdjustmentTimelineStepDTO> buildTimeline(
            BillNonGST parent,
            BillStockReturnResponseDTO returnResp,
            BillResponseDTO supplementaryResp,
            String groupId,
            BillFinalizeAdjustmentRequestDTO request) {
        List<BillAdjustmentTimelineStepDTO> steps = new ArrayList<>();
        steps.add(step("ORIGINAL", "Original Bill", "Bill #" + parent.getBillNumber(), parent.getCreatedAt(), null));
        if (returnResp != null) {
            steps.add(step(
                    "RETURN",
                    "Return Entry",
                    "Return id " + returnResp.getReturnId() + " · "
                            + nz(returnResp.getComputedReturnAmount()).toPlainString(),
                    returnResp.getCreatedAt(),
                    groupId));
            steps.add(step("INVENTORY", "Inventory Restored", "Stock IN for returned quantities", returnResp.getCreatedAt(), groupId));
        }
        if (supplementaryResp != null) {
            steps.add(step(
                    "SUPPLEMENTARY",
                    "Supplementary Bill",
                    "Bill #" + supplementaryResp.getBillNumber(),
                    null,
                    groupId));
        }
        if (request.getSettlementMethod() != null
                && !BillAdjustmentSettlementMethod.NONE.equalsIgnoreCase(request.getSettlementMethod())) {
            steps.add(step(
                    "SETTLEMENT",
                    "Settlement Transaction",
                    request.getSettlementMethod() + " · group " + groupId,
                    LocalDateTime.now(),
                    groupId));
        }
        return steps;
    }

    private List<BillAdjustmentTimelineStepDTO> buildHistoryTimeline(BillResponseDTO bill) {
        List<BillAdjustmentTimelineStepDTO> steps = new ArrayList<>();
        steps.add(step("ORIGINAL", "Original Bill", "Bill #" + bill.getBillNumber(), null, null));
        if (bill.getReturnHistory() != null) {
            for (BillStockReturnHistoryDTO r : bill.getReturnHistory()) {
                steps.add(step(
                        "RETURN",
                        "Return Entry",
                        "Return #" + r.getReturnId(),
                        r.getCreatedAt(),
                        null));
            }
        }
        if (bill.getSupplementaryBills() != null) {
            for (BillSupplementarySummaryDTO s : bill.getSupplementaryBills()) {
                steps.add(step(
                        "SUPPLEMENTARY",
                        "Supplementary Bill",
                        "Bill #" + s.getBillNumber(),
                        s.getBillDate() != null ? s.getBillDate().atStartOfDay() : null,
                        null));
            }
        }
        return steps;
    }

    private static BillAdjustmentTimelineStepDTO step(
            String step, String label, String detail, LocalDateTime at, String groupId) {
        return new BillAdjustmentTimelineStepDTO(step, label, detail, at, groupId);
    }

    private static BillAdjustmentSettlementPreviewDTO buildSettlementPreview(BillResponseDTO bill) {
        BillAdjustmentSettlementPreviewDTO p = new BillAdjustmentSettlementPreviewDTO();
        BillReturnSummaryDTO rs = bill.getReturnSummary();
        if (rs != null) {
            p.setOriginalBillAmount(rs.getOriginalInvoiceTotalAmount());
            p.setCumulativeReturnedValue(rs.getCumulativeReturnedValue());
            p.setEffectiveBillAmount(rs.getEffectiveBillTotal());
            p.setSuggestedRefundVersusEffective(rs.getSuggestedCustomerRefundVersusEffective());
        } else {
            p.setOriginalBillAmount(bill.getTotalAmount());
            p.setEffectiveBillAmount(bill.getTotalAmount());
        }
        p.setAdvanceUsed(bill.getAdvanceUsed());
        p.setTotalPaid(bill.getTotalPaid());
        return p;
    }

    private static String inferAdjustmentType(boolean hasReturn, boolean hasSupp) {
        if (hasReturn && hasSupp) {
            return BillAdjustmentType.EXCHANGE;
        }
        if (hasReturn) {
            return BillAdjustmentType.RETURN_ONLY;
        }
        return BillAdjustmentType.ITEM_REPLACEMENT;
    }

    private static MoneyPaymentMode mapPaymentMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MoneyPaymentMode.CASH;
        }
        String u = raw.trim().toUpperCase();
        if (u.contains("UPI")) {
            return MoneyPaymentMode.UPI;
        }
        if (u.contains("BANK")) {
            return MoneyPaymentMode.BANK;
        }
        return MoneyPaymentMode.CASH;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String mergeNotes(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a.trim() + " | " + b.trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return "—";
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "—";
    }
}
