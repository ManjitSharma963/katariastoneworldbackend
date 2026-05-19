package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByBillPaymentCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.BillPayment;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Routes bill payment money lines through the accounting engine when enabled.
 */
@Component
public class BillPaymentAccountingBridge {

    @Autowired
    private AccountingFeatureFlags accountingFeatureFlags;

    @Autowired
    private AccountingTransactionService accountingTransactionService;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private BillGSTRepository billGSTRepository;

    @Autowired
    private BillNonGSTRepository billNonGSTRepository;

    public void postBillPaymentLedger(BillPayment payment, Long billVersionId, String linkedGroupId, String txnType) {
        if (accountingFeatureFlags.isBillEnabled()) {
            postViaEngine(payment, billVersionId, linkedGroupId, txnType);
        } else {
            postLegacy(payment, billVersionId, linkedGroupId, txnType);
        }
    }

    public void voidBillPaymentLedger(Long billPaymentId, String reason) {
        if (billPaymentId == null) {
            return;
        }
        if (accountingFeatureFlags.isBillEnabled()) {
            accountingTransactionService.voidByBillPayment(VoidByBillPaymentCommand.of(billPaymentId, reason));
            return;
        }
        voidLegacy(billPaymentId);
    }

    private void postViaEngine(BillPayment payment, Long billVersionId, String linkedGroupId, String txnType) {
        BillPaymentContext ctx = resolveContext(payment);
        if (ctx == null) {
            return;
        }
        if (payment.getId() != null
                && moneyTransactionRepository.findFirstByBillPaymentIdAndStatusAndIsDeletedFalseOrderByIdAsc(
                        payment.getId(), MoneyTxnStatus.ACTIVE).isPresent()) {
            return;
        }

        PostMoneyCommand command = buildPostCommand(payment, billVersionId, linkedGroupId, txnType, ctx);
        if (command.direction() == MoneyDirection.IN) {
            accountingTransactionService.postIn(command);
        } else {
            accountingTransactionService.postOut(command);
        }
    }

    private PostMoneyCommand buildPostCommand(
            BillPayment payment,
            Long billVersionId,
            String linkedGroupId,
            String txnType,
            BillPaymentContext ctx) {
        String normalizedTxnType = txnType != null ? txnType.trim().toUpperCase(Locale.ROOT) : "BILL_PAYMENT";
        boolean reversalTxn = normalizedTxnType.contains("REVERSAL");
        String requestId = payment.getId() != null
                ? "BILL_PAYMENT:" + ctx.direction().name() + ":" + payment.getId()
                : null;

        PostMoneyCommand.Builder builder = PostMoneyCommand.builder()
                .location(ctx.location())
                .transactionDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now())
                .amount(ctx.amount())
                .direction(ctx.direction())
                .paymentMode(ctx.paymentMode())
                .referenceType(MoneyReferenceType.bill)
                .referenceId(ctx.billId())
                .partyId(ctx.customerId())
                .partyName(ctx.partyName())
                .requestId(requestId)
                .billPaymentId(payment.getId())
                .billVersionId(billVersionId)
                .linkedGroupId(linkedGroupId)
                .ownerUserId(payment.getCreatedBy())
                .ledgerTxnType(normalizedTxnType);

        if (reversalTxn) {
            builder.category(MoneyCategory.BILL_REVERSAL)
                    .subCategory(MoneyLedgerCategories.SUB_BILL_CANCELLATION)
                    .eventType(AccountingEventType.BILL_PAYMENT_VOID)
                    .notes("Bill cancellation refund to customer | BillNo: " + ctx.billNumberLabel());
            if (payment.getReversalOfId() != null) {
                builder.reversalOfBillPaymentId(payment.getReversalOfId());
            }
        } else {
            builder.category(MoneyCategory.BILL)
                    .eventType(ctx.direction() == MoneyDirection.IN
                            ? AccountingEventType.BILL_PAYMENT_IN
                            : AccountingEventType.GENERIC_OUT);
            if (ctx.supplementaryChildBill()) {
                builder.subCategory(MoneyLedgerCategories.SUB_ADJUSTMENT_PAYMENT);
            } else {
                builder.subCategory(resolveBillPaymentSubCategory(normalizedTxnType));
            }
            builder.notes(normalizedTxnType + " | BillNo: " + ctx.billNumberLabel());
        }
        return builder.build();
    }

    private void postLegacy(BillPayment payment, Long billVersionId, String linkedGroupId, String txnType) {
        if (payment == null || payment.getBillId() == null || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BillPaymentContext ctx = resolveContext(payment);
        if (ctx == null) {
            return;
        }

        String normalizedTxnType = txnType != null ? txnType.trim().toUpperCase(Locale.ROOT) : "BILL_PAYMENT";
        boolean reversalTxn = normalizedTxnType.contains("REVERSAL");

        if (payment.getId() != null
                && moneyTransactionRepository.existsByBillPaymentIdAndIsDeletedFalse(payment.getId())) {
            return;
        }
        if (!reversalTxn && payment.getId() == null
                && moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndAmountAndPaymentMode(
                        MoneyReferenceType.bill, ctx.billId(), ctx.amount(), ctx.paymentMode())) {
            return;
        }

        MoneyTransaction tx = new MoneyTransaction();
        tx.setAmount(ctx.amount());
        tx.setDirection(ctx.direction());
        if (reversalTxn) {
            tx.setCategory(MoneyCategory.BILL_REVERSAL);
            tx.setTxnType("BILL_REVERSAL");
            tx.setSubCategory(MoneyLedgerCategories.SUB_BILL_CANCELLATION);
            tx.setNotes("Bill cancellation refund to customer | BillNo: " + ctx.billNumberLabel());
        } else {
            tx.setCategory(MoneyCategory.BILL);
            tx.setTxnType(normalizedTxnType);
            if (ctx.supplementaryChildBill()) {
                tx.setSubCategory(MoneyLedgerCategories.SUB_ADJUSTMENT_PAYMENT);
            } else {
                tx.setSubCategory(resolveBillPaymentSubCategory(normalizedTxnType));
            }
            tx.setNotes(normalizedTxnType + " | BillNo: " + ctx.billNumberLabel());
        }
        tx.setPartyId(ctx.customerId());
        tx.setPartyName(ctx.partyName());
        tx.setPaymentMode(ctx.paymentMode());
        tx.setReferenceType(MoneyReferenceType.bill);
        tx.setReferenceId(ctx.billId());
        tx.setBillPaymentId(payment.getId());
        tx.setBillVersionId(billVersionId);
        tx.setLinkedGroupId(linkedGroupId);
        tx.setTransactionDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now());
        tx.setDateTime(LocalDateTime.now());
        tx.setLocation(ctx.location());
        tx.setOwnerUserId(payment.getCreatedBy());
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        if (reversalTxn && payment.getReversalOfId() != null) {
            moneyTransactionRepository
                    .findFirstByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(payment.getReversalOfId())
                    .ifPresent(orig -> tx.setReversalOfId(orig.getId()));
        }
        moneyTransactionRepository.save(tx);
    }

    private void voidLegacy(Long billPaymentId) {
        var rows = moneyTransactionRepository.findByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(billPaymentId);
        if (rows.isEmpty()) {
            return;
        }
        for (MoneyTransaction tx : rows) {
            tx.setIsDeleted(true);
            tx.setStatus(MoneyTxnStatus.CANCELLED);
        }
        moneyTransactionRepository.saveAll(rows);
    }

    private BillPaymentContext resolveContext(BillPayment payment) {
        if (payment == null || payment.getBillId() == null || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        Long billId = payment.getBillId();
        BillKind kind = payment.getBillKind() != null ? payment.getBillKind() : BillKind.NON_GST;
        String billNumber = null;
        String location = "";
        Long customerId = null;
        String customerName = null;
        boolean supplementaryChildBill = false;

        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findById(billId).orElse(null);
            if (bill == null) {
                return null;
            }
            billNumber = bill.getBillNumber();
            location = bill.getLocation() != null ? bill.getLocation().trim() : "";
            if (bill.getCustomer() != null) {
                customerId = bill.getCustomer().getId();
                customerName = bill.getCustomer().getCustomerName();
            }
        } else {
            BillNonGST bill = billNonGSTRepository.findById(billId).orElse(null);
            if (bill == null) {
                return null;
            }
            billNumber = bill.getBillNumber();
            location = bill.getLocation() != null ? bill.getLocation().trim() : "";
            if (bill.getCustomer() != null) {
                customerId = bill.getCustomer().getId();
                customerName = bill.getCustomer().getCustomerName();
            }
            supplementaryChildBill = Boolean.TRUE.equals(bill.getSupplementaryBill());
        }

        MoneyPaymentMode paymentMode = mapPaymentMode(
                payment.getPaymentMode() != null ? payment.getPaymentMode().name() : null);
        BigDecimal rawAmount = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = rawAmount.abs();
        MoneyDirection direction = rawAmount.compareTo(BigDecimal.ZERO) >= 0 ? MoneyDirection.IN : MoneyDirection.OUT;
        String partyName = (customerName != null && !customerName.trim().isEmpty())
                ? customerName.trim()
                : (customerId != null ? ("Customer_" + customerId) : "Customer_Unknown");
        String billNumberLabel = billNumber != null ? billNumber : ("#" + billId);

        return new BillPaymentContext(
                billId, location, customerId, partyName, billNumberLabel, amount, direction, paymentMode, supplementaryChildBill);
    }

    private static String resolveBillPaymentSubCategory(String normalizedTxnType) {
        String t = normalizedTxnType != null ? normalizedTxnType.trim().toUpperCase(Locale.ROOT) : "BILL_PAYMENT";
        if (t.contains("REVERSAL")) {
            return "BILL_PAYMENT_REVERSAL";
        }
        if ("ADVANCE_APPLICATION".equals(t)) {
            return "ADVANCE_APPLICATION";
        }
        return "BILL_PAYMENT";
    }

    private static MoneyPaymentMode mapPaymentMode(String mode) {
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "CASH" -> MoneyPaymentMode.CASH;
            case "UPI", "WALLET" -> MoneyPaymentMode.UPI;
            case "BANK_TRANSFER", "CHEQUE", "OTHER", "BANK" -> MoneyPaymentMode.BANK;
            default -> MoneyPaymentMode.BANK;
        };
    }

    private record BillPaymentContext(
            Long billId,
            String location,
            Long customerId,
            String partyName,
            String billNumberLabel,
            BigDecimal amount,
            MoneyDirection direction,
            MoneyPaymentMode paymentMode,
            boolean supplementaryChildBill) {
    }
}
