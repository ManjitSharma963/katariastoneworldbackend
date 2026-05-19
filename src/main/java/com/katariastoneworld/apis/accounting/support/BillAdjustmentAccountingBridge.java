package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Bill adjustment settlement collect (IN) — refunds use {@link RefundAccountingBridge}. */
@Component
public class BillAdjustmentAccountingBridge {

    private static final String MODULE = "BILL_ADJUSTMENT";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionRepository moneyTransactionRepository;

    public BillAdjustmentAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionRepository moneyTransactionRepository) {
        this.flags = flags;
        this.posting = posting;
        this.moneyTransactionRepository = moneyTransactionRepository;
    }

    public void postAdjustmentCollect(
            Long billId,
            String groupId,
            BigDecimal amount,
            MoneyPaymentMode paymentMode,
            LocalDate transactionDate,
            String reference,
            String location,
            Long customerId,
            String partyName,
            Long actorUserId) {
        if (billId == null || groupId == null || groupId.isBlank() || amount == null) {
            return;
        }
        String txnType = "ADJ_SETTLE_COLLECT_" + groupId.trim();
        if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                MoneyReferenceType.bill, billId, txnType)) {
            return;
        }

        PostMoneyCommand command = PostMoneyCommand.builder()
                .location(location != null ? location.trim() : "")
                .transactionDate(transactionDate != null ? transactionDate : LocalDate.now())
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .direction(MoneyDirection.IN)
                .category(MoneyCategory.BILL)
                .subCategory(MoneyLedgerCategories.SUB_ADJUSTMENT_PAYMENT)
                .referenceType(MoneyReferenceType.bill)
                .referenceId(billId)
                .paymentMode(paymentMode)
                .partyId(customerId)
                .partyName(partyName)
                .requestId("ADJ:COLLECT:" + groupId.trim())
                .ledgerTxnType(txnType)
                .adjustmentGroupId(groupId)
                .linkedGroupId(groupId)
                .ownerUserId(actorUserId)
                .notes(reference != null && !reference.isBlank() ? reference : ("Adjustment settlement " + groupId))
                .eventType(AccountingEventType.BILL_PAYMENT_IN)
                .metadataJson(AccountingPostingSupport.balancedEntryMetadata("CASH", "ADJUSTMENT_SETTLEMENT"))
                .build();

        if (flags.isAdjustmentEnabled()) {
            posting.postIn(command, MODULE);
            return;
        }
        saveLegacy(command);
    }

    public void voidAdjustmentCollect(String location, Long billId, String groupId, String reason) {
        if (billId == null || groupId == null || groupId.isBlank()) {
            return;
        }
        String txnType = "ADJ_SETTLE_COLLECT_" + groupId.trim();
        if (flags.isAdjustmentEnabled()) {
            posting.voidByRequestId(location, "ADJ:COLLECT:" + groupId.trim(), reason, MODULE);
            posting.voidByReference(
                    location, MoneyReferenceType.bill, billId, MoneyCategory.BILL, txnType, reason, MODULE);
        }
    }

    private void saveLegacy(PostMoneyCommand command) {
        MoneyTransaction tx = new MoneyTransaction();
        LocalDate d = command.transactionDate() != null ? command.transactionDate() : LocalDate.now();
        tx.setLocation(command.location());
        tx.setTransactionDate(d);
        tx.setDateTime(LocalDateTime.now());
        tx.setAmount(command.amount());
        tx.setDirection(command.direction());
        tx.setCategory(command.category());
        tx.setSubCategory(command.subCategory());
        tx.setTxnType(command.ledgerTxnType());
        tx.setAdjustmentGroupId(command.adjustmentGroupId());
        tx.setLinkedGroupId(command.linkedGroupId());
        tx.setPartyId(command.partyId());
        tx.setPartyName(command.partyName());
        tx.setPaymentMode(command.paymentMode());
        tx.setReferenceType(command.referenceType());
        tx.setReferenceId(command.referenceId());
        tx.setNotes(command.notes());
        tx.setOwnerUserId(command.ownerUserId());
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        moneyTransactionRepository.save(tx);
    }
}
