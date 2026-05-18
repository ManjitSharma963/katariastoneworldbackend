package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillRevisionDifferenceDTO;
import com.katariastoneworld.apis.dto.BillRevisionEditMode;
import com.katariastoneworld.apis.dto.BillRevisionEditRequest;
import com.katariastoneworld.apis.dto.BillRevisionIntegrityReportDTO;
import com.katariastoneworld.apis.dto.BillRevisionPreviewDTO;
import com.katariastoneworld.apis.dto.BillRevisionResultDTO;
import com.katariastoneworld.apis.entity.BillEventType;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillVersion;
import com.katariastoneworld.apis.repository.BillVersionRepository;
import com.katariastoneworld.apis.service.revision.BillAdvanceRevisionEngine;
import com.katariastoneworld.apis.service.revision.BillInventoryRevisionEngine;
import com.katariastoneworld.apis.service.revision.BillPaymentRevisionEngine;
import com.katariastoneworld.apis.service.revision.BillRevisionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional orchestration for enterprise bill revision: validation → inventory pre-flight → mutation → snapshots →
 * integrity verification. Append-only accounting is enforced in {@link BillService} and revision engines.
 */
@Service
@Transactional
public class BillRevisionService {

    private static final Logger log = LoggerFactory.getLogger(BillRevisionService.class);
    private static final BigDecimal SETTLE_EPS = new BigDecimal("0.01");

    private final BillService billService;
    private final BillEventService billEventService;
    private final BillVersionRepository billVersionRepository;
    private final BillRevisionValidator revisionValidator;
    private final BillInventoryRevisionEngine inventoryEngine;
    private final BillPaymentRevisionEngine paymentEngine;
    private final BillAdvanceRevisionEngine advanceEngine;

    public BillRevisionService(
            BillService billService,
            BillEventService billEventService,
            BillVersionRepository billVersionRepository,
            BillRevisionValidator revisionValidator,
            BillInventoryRevisionEngine inventoryEngine,
            BillPaymentRevisionEngine paymentEngine,
            BillAdvanceRevisionEngine advanceEngine) {
        this.billService = billService;
        this.billEventService = billEventService;
        this.billVersionRepository = billVersionRepository;
        this.revisionValidator = revisionValidator;
        this.inventoryEngine = inventoryEngine;
        this.paymentEngine = paymentEngine;
        this.advanceEngine = advanceEngine;
    }

    /**
     * Applies a full replace or line patch; returns bill plus post-commit integrity report.
     */
    public BillRevisionResultDTO editBillWithIntegrity(BillRevisionEditRequest request) {
        BillResponseDTO bill = editBill(request);
        BillRevisionIntegrityReportDTO integrity = paymentEngine.verifySettlement(bill);
        if (!integrity.isConsistent()) {
            log.warn("bill_revision_integrity_mismatch billId={} kind={} findings={}",
                    request.getBillId(), request.getBillType(), integrity.getFindings());
        }
        return BillRevisionResultDTO.builder().bill(bill).integrity(integrity).build();
    }

    public BillResponseDTO editBill(BillRevisionEditRequest request) {
        revisionValidator.validateEditRequest(request);
        if (request.getMode() == BillRevisionEditMode.FULL_REPLACE) {
            inventoryEngine.preflightFullReplace(
                    request.getBillId(),
                    request.getBillType(),
                    request.getLocation(),
                    request.getReplaceRequest());
            BillResponseDTO bill = billService.replaceBill(
                    request.getBillId(),
                    request.getBillType(),
                    request.getReplaceRequest(),
                    request.getLocation(),
                    request.getActorUserId(),
                    request.getActorRole());
            recomputeSnapshots(request.getBillId(), request.getBillType(), request.getLocation());
            return bill;
        }
        BillResponseDTO bill = billService.patchBillLineQuantities(
                request.getBillId(),
                request.getBillType(),
                request.getPatchRequest(),
                request.getLocation(),
                request.getActorUserId());
        recomputeSnapshots(request.getBillId(), request.getBillType(), request.getLocation());
        return bill;
    }

    @Transactional(readOnly = true)
    public BillRevisionPreviewDTO buildRevisionPreview(
            Long billId, String billType, String location, BillRequestDTO proposal) {
        BillResponseDTO current = billService.getBillById(billId, billType, location);
        BillRevisionDifferenceDTO difference = calculateDifference(current, proposal, billType);
        List<String> validationMessages = revisionValidator.validateReplacePayloadSoft(proposal);
        List<String> stockWarnings = inventoryEngine.collectStockWarningsForProposal(
                billId, billType, location, proposal);
        boolean canFullReplace = validationMessages.isEmpty() && stockWarnings.isEmpty();
        try {
            billService.assertEligibleForFullBillReplace(billId, billType, location);
        } catch (IllegalArgumentException ex) {
            canFullReplace = false;
            validationMessages.add(ex.getMessage());
        }
        SettlementPreview settlement = computeSettlementPreview(
                bd(difference.getProposedNewTotalAmount()),
                bd(difference.getOldAdvanceUsed()),
                bd(difference.getOldPaidAmount()));
        return BillRevisionPreviewDTO.builder()
                .difference(difference)
                .settlementKind(settlement.kind)
                .settlementHeadline(settlement.headline)
                .settlementAmount(settlement.amount.doubleValue())
                .additionalPaymentRequired(settlement.additionalPayment.doubleValue())
                .refundOrStoreCreditAmount(settlement.refundOrCredit.doubleValue())
                .stockWarnings(stockWarnings)
                .validationMessages(validationMessages)
                .canFullReplace(canFullReplace)
                .build();
    }

    @Transactional(readOnly = true)
    public BillRevisionDifferenceDTO calculateDifference(
            BillResponseDTO currentState, BillRequestDTO proposal, String billType) {
        if (currentState.getBillType() != null
                && !currentState.getBillType().trim().equalsIgnoreCase(billType.trim())) {
            throw new IllegalArgumentException("billType does not match currentState.billType");
        }
        boolean gst = isGstBillType(billType);
        BigDecimal subtotal = proposal.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getPricePerUnit())
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (gst) {
            BigDecimal taxRate = BigDecimal.valueOf(
                            proposal.getTaxPercentage() != null ? proposal.getTaxPercentage() : 0d)
                    .setScale(2, RoundingMode.HALF_UP);
            taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        BigDecimal labour = nz(proposal.getLabourCharge());
        BigDecimal transport = nz(proposal.getTransportationCharge());
        BigDecimal other = nz(proposal.getOtherExpenses());
        BigDecimal discount = proposal.getDiscountAmount() != null
                ? BigDecimal.valueOf(proposal.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal proposedTotal = subtotal.add(taxAmount).add(labour).add(transport).add(other).subtract(discount);
        if (proposedTotal.compareTo(BigDecimal.ZERO) < 0) {
            proposedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            proposedTotal = proposedTotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal oldSubtotal = bd(currentState.getSubtotal());
        BigDecimal oldTax = bd(currentState.getTaxAmount());
        BigDecimal oldTotal = bd(currentState.getTotalAmount());
        BigDecimal adv = bd(currentState.getAdvanceUsed());
        BigDecimal paid = bd(currentState.getPaidAmount());
        BigDecimal due = bd(currentState.getAmountDue());
        BigDecimal est = proposedTotal.subtract(adv).subtract(paid);
        if (est.compareTo(BigDecimal.ZERO) < 0) {
            est = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            est = est.setScale(2, RoundingMode.HALF_UP);
        }
        return BillRevisionDifferenceDTO.builder()
                .oldSubtotal(oldSubtotal.doubleValue())
                .proposedNewSubtotal(subtotal.doubleValue())
                .oldTaxAmount(oldTax.doubleValue())
                .proposedNewTaxAmount(taxAmount.doubleValue())
                .oldTotalAmount(oldTotal.doubleValue())
                .proposedNewTotalAmount(proposedTotal.doubleValue())
                .totalDelta(proposedTotal.subtract(oldTotal).doubleValue())
                .oldAdvanceUsed(adv.doubleValue())
                .oldPaidAmount(paid.doubleValue())
                .oldAmountDue(due.doubleValue())
                .estimatedDueIfAdvanceAndCashUnchanged(est.doubleValue())
                .build();
    }

    public void adjustInventoryForFullReplaceProposal(Long billId, String billType, String location, BillRequestDTO proposal) {
        inventoryEngine.preflightFullReplace(billId, billType, location, proposal);
    }

    public BillResponseDTO adjustAdvance(Long billId, String billType, String location, Long actorUserId) {
        billService.assertBillMutableForRevision(billId, billType, location);
        return advanceEngine.resynchronizeAdvance(billId, billType, location, actorUserId);
    }

    public BillResponseDTO adjustPayments(
            Long billId, String billType, BillPaymentRequestDTO paymentRequest, String location, Long actorUserId) {
        billService.assertBillMutableForRevision(billId, billType, location);
        return paymentEngine.appendPayment(billId, billType, paymentRequest, location, actorUserId);
    }

    public BillResponseDTO adjustOutstanding(Long billId, String billType, String location, Long actorUserId) {
        return paymentEngine.refreshOutstanding(billId, billType, location);
    }

    public void createRevisionAudit(Long billId, String billType, Long actorUserId, String payloadJson) {
        BillKind kind = parseBillKind(billType);
        Long versionId = billVersionRepository
                .findTopByBillIdOrderByVersionNoDescCreatedAtDesc(billId)
                .map(BillVersion::getId)
                .orElse(null);
        billEventService.record(kind, billId, BillEventType.REVISION_AUDIT, versionId, null, actorUserId, payloadJson);
    }

    public void recomputeSnapshots(Long billId, String billType, String location) {
        billService.recomputeSnapshotsForBill(billId, billType, location);
    }

    @Transactional(readOnly = true)
    public BillRevisionIntegrityReportDTO verifyBillRevisionIntegrity(Long billId, String billType, String location) {
        BillResponseDTO r = billService.getBillById(billId, billType, location);
        return paymentEngine.verifySettlement(r);
    }

    private record SettlementPreview(String kind, String headline, BigDecimal amount,
            BigDecimal additionalPayment, BigDecimal refundOrCredit) {}

    private static SettlementPreview computeSettlementPreview(BigDecimal proposedTotal, BigDecimal advance, BigDecimal paid) {
        BigDecimal covered = advance.add(paid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overage = covered.subtract(proposedTotal);
        if (overage.compareTo(SETTLE_EPS) > 0) {
            return new SettlementPreview(
                    "REFUND_OR_STORE_CREDIT",
                    "Refund / store credit (preview)",
                    overage,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    overage);
        }
        BigDecimal due = proposedTotal.subtract(covered).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (due.compareTo(SETTLE_EPS) > 0) {
            return new SettlementPreview(
                    "ADDITIONAL_PAYMENT",
                    "Additional payment required",
                    due,
                    due,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        return new SettlementPreview(
                "EVEN",
                "Settlement even",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(Double v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Double v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isGstBillType(String billType) {
        return billType != null && "GST".equalsIgnoreCase(billType.trim());
    }

    private static BillKind parseBillKind(String billType) {
        if (billType == null || billType.isBlank()) {
            throw new IllegalArgumentException("billType is required");
        }
        return isGstBillType(billType) ? BillKind.GST : BillKind.NON_GST;
    }
}
