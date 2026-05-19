package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class BudgetAccountingBridge {

    private static final String MODULE = "BUDGET";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionLegacySync legacySync;

    public BudgetAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.legacySync = legacySync;
    }

    public void postBudgetAdjustment(
            String location,
            BigDecimal appliedDelta,
            boolean bankTransferFunding,
            LocalDate eventDate,
            String notes) {
        if (location == null || location.isBlank() || appliedDelta == null
                || appliedDelta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal amt = appliedDelta.abs().setScale(2, RoundingMode.HALF_UP);
        boolean increase = appliedDelta.signum() > 0;
        MoneyDirection direction = increase ? MoneyDirection.IN : MoneyDirection.OUT;
        String requestId = "BUDGET:ADJ:" + location.trim() + ":" + eventDate + ":" + (increase ? "INC" : "DEC") + ":" + amt;

        if (flags.isBudgetEnabled()) {
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(direction)
                    .category(MoneyCategory.OTHER)
                    .subCategory("BUDGET_ADJUSTMENT")
                    .referenceType(MoneyReferenceType.other)
                    .referenceId(null)
                    .paymentMode(bankTransferFunding ? MoneyPaymentMode.BANK : MoneyPaymentMode.CASH)
                    .requestId(requestId)
                    .ledgerTxnType("BUDGET_ADJUSTMENT")
                    .notes(notes)
                    .eventType(AccountingEventType.BUDGET_ADJUST)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata(
                            increase ? "CASH" : "BUDGET_RESERVE",
                            increase ? "BUDGET_RESERVE" : "CASH"))
                    .build();
            if (increase) {
                posting.postIn(command, MODULE);
            } else {
                posting.postOut(command, MODULE);
            }
            return;
        }
        legacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                increase ? LedgerTransactionType.CREDIT : LedgerTransactionType.DEBIT,
                bankTransferFunding ? LedgerPaymentMode.BANK : LedgerPaymentMode.CASH,
                "BUDGET_ADJUSTMENT",
                null,
                notes);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isBudgetEnabled() || source == null || !"BUDGET_ADJUSTMENT".equalsIgnoreCase(source.trim())) {
            return false;
        }
        posting.voidByReference(
                location,
                MoneyReferenceType.other,
                referenceId,
                MoneyCategory.OTHER,
                "BUDGET_ADJUSTMENT",
                reason,
                MODULE);
        return true;
    }
}
