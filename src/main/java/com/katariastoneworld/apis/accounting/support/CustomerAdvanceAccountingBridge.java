package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
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

/** Customer advance deposit (cash IN) — wallet row remains in {@code customer_wallet_transactions}. */
@Component
public class CustomerAdvanceAccountingBridge {

    private static final String MODULE = "CUSTOMER_ADVANCE";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionLegacySync legacySync;

    public CustomerAdvanceAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.legacySync = legacySync;
    }

    public void postAdvanceDeposit(
            String location,
            Long customerId,
            Long advanceId,
            BillPaymentMode mode,
            BigDecimal amount,
            LocalDate eventDate,
            String description) {
        if (location == null || location.isBlank() || advanceId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String requestId = "ADVANCE:DEPOSIT:" + advanceId;
        if (flags.isAdvanceEnabled()) {
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.IN)
                    .category(MoneyCategory.ADVANCE)
                    .subCategory(LedgerSources.ADVANCE)
                    .referenceType(MoneyReferenceType.bill)
                    .referenceId(advanceId)
                    .paymentMode(mapPaymentMode(mode))
                    .partyId(customerId)
                    .partyName(customerId != null ? ("Customer_" + customerId) : null)
                    .requestId(requestId)
                    .ledgerTxnType(LedgerSources.ADVANCE)
                    .notes(description != null ? description : ("Customer advance deposit customerId=" + customerId))
                    .eventType(AccountingEventType.ADVANCE_IN)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata("CASH", "CUSTOMER_ADVANCE_LIABILITY"))
                    .build();
            posting.postIn(command, MODULE);
            return;
        }
        legacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.ADVANCE,
                advanceId,
                description != null ? description : ("Customer advance deposit customerId=" + customerId));
    }

    public void voidAdvanceDeposit(String location, Long advanceId, String reason) {
        if (advanceId == null) {
            return;
        }
        if (!flags.isAdvanceEnabled()) {
            legacySync.hardDeleteSyncedLine(LedgerSources.ADVANCE, advanceId);
            return;
        }
        posting.voidByRequestId(location, "ADVANCE:DEPOSIT:" + advanceId, reason, MODULE);
        posting.voidByReference(
                location,
                MoneyReferenceType.bill,
                advanceId,
                MoneyCategory.ADVANCE,
                LedgerSources.ADVANCE,
                reason,
                MODULE);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isAdvanceEnabled() || referenceId == null || source == null) {
            return false;
        }
        if (!LedgerSources.ADVANCE.equalsIgnoreCase(source.trim())) {
            return false;
        }
        voidAdvanceDeposit(location, referenceId, reason);
        return true;
    }

    private static MoneyPaymentMode mapPaymentMode(BillPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        return switch (mode) {
            case CASH -> MoneyPaymentMode.CASH;
            case UPI, WALLET -> MoneyPaymentMode.UPI;
            case BANK_TRANSFER, CHEQUE, OTHER -> MoneyPaymentMode.BANK;
        };
    }
}
