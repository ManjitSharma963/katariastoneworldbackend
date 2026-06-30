package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.CashBankTransferDirection;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class CashBankTransferAccountingBridge {

    private static final String MODULE = "CASH_BANK_TRANSFER";
    private static final String LEDGER_TXN_TYPE = MoneyLedgerCategories.SUB_CASH_BANK_TRANSFER;

    private final AccountingPostingSupport posting;

    public CashBankTransferAccountingBridge(AccountingPostingSupport posting) {
        this.posting = posting;
    }

    public String postTransfer(
            String location,
            BigDecimal amount,
            CashBankTransferDirection direction,
            LocalDate eventDate,
            String notes) {
        if (location == null || location.isBlank() || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0 || direction == null) {
            return null;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = eventDate != null ? eventDate : LocalDate.now();
        String groupId = UUID.randomUUID().toString();
        String loc = location.trim();
        String desc = buildNotes(direction, notes);

        if (direction == CashBankTransferDirection.CASH_TO_BANK) {
            postLeg(loc, d, amt, MoneyDirection.OUT, MoneyPaymentMode.CASH, groupId, "OUT", desc,
                    AccountingEventType.GENERIC_OUT, "CASH", "BANK");
            postLeg(loc, d, amt, MoneyDirection.IN, MoneyPaymentMode.BANK, groupId, "IN", desc,
                    AccountingEventType.GENERIC_IN, "CASH", "BANK");
        } else {
            postLeg(loc, d, amt, MoneyDirection.OUT, MoneyPaymentMode.BANK, groupId, "OUT", desc,
                    AccountingEventType.GENERIC_OUT, "BANK", "CASH");
            postLeg(loc, d, amt, MoneyDirection.IN, MoneyPaymentMode.CASH, groupId, "IN", desc,
                    AccountingEventType.GENERIC_IN, "BANK", "CASH");
        }
        return groupId;
    }

    private void postLeg(
            String location,
            LocalDate date,
            BigDecimal amount,
            MoneyDirection direction,
            MoneyPaymentMode paymentMode,
            String groupId,
            String leg,
            String notes,
            AccountingEventType eventType,
            String fromRail,
            String toRail) {
        String requestId = "CBT:" + groupId + ":" + leg + ":" + paymentMode.name();
        long referenceId = BudgetAccountingBridge.budgetSyntheticReferenceId(requestId);
        PostMoneyCommand command = PostMoneyCommand.builder()
                .location(location)
                .transactionDate(date)
                .amount(amount)
                .direction(direction)
                .category(MoneyCategory.OTHER)
                .subCategory(LEDGER_TXN_TYPE)
                .referenceType(MoneyReferenceType.other)
                .referenceId(referenceId)
                .paymentMode(paymentMode)
                .requestId(requestId)
                .ledgerTxnType(LEDGER_TXN_TYPE)
                .notes(notes)
                .eventType(eventType)
                .linkedGroupId(groupId)
                .adjustmentGroupId(groupId)
                .metadataJson(AccountingPostingSupport.balancedEntryMetadata(fromRail, toRail))
                .build();
        if (direction == MoneyDirection.IN) {
            posting.postIn(command, MODULE);
        } else {
            posting.postOut(command, MODULE);
        }
    }

    private static String buildNotes(CashBankTransferDirection direction, String notes) {
        String base = direction == CashBankTransferDirection.CASH_TO_BANK
                ? "Cash/UPI deposited to bank"
                : "Bank withdrawal to cash";
        if (notes != null && !notes.isBlank()) {
            return base + " — " + notes.trim();
        }
        return base;
    }
}
