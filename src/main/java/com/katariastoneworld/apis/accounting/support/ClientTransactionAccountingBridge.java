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

@Component
public class ClientTransactionAccountingBridge {

    private static final String MODULE = "CLIENT";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionLegacySync legacySync;

    public ClientTransactionAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.legacySync = legacySync;
    }

    public void postClientPaymentIn(
            String location,
            String clientId,
            Long clientTransactionId,
            BillPaymentMode mode,
            BigDecimal amount,
            LocalDate eventDate) {
        postClient(
                location,
                clientId,
                clientTransactionId,
                mode,
                amount,
                eventDate,
                MoneyDirection.IN,
                LedgerSources.CLIENT_PAYMENT,
                "CLIENT_PAYMENT",
                AccountingEventType.GENERIC_IN,
                "CASH",
                "CLIENT_RECEIVABLE",
                "Client payment clientId=" + clientId);
    }

    public void postClientPaymentOut(
            String location,
            String clientId,
            Long clientTransactionId,
            BillPaymentMode mode,
            BigDecimal amount,
            LocalDate eventDate) {
        postClient(
                location,
                clientId,
                clientTransactionId,
                mode,
                amount,
                eventDate,
                MoneyDirection.OUT,
                LedgerSources.CLIENT_OUT,
                "CLIENT_OUT",
                AccountingEventType.GENERIC_OUT,
                "CLIENT_ADVANCE_OR_EXPENSE",
                "CASH",
                "Client payment out clientId=" + clientId);
    }

    private void postClient(
            String location,
            String clientId,
            Long clientTransactionId,
            BillPaymentMode mode,
            BigDecimal amount,
            LocalDate eventDate,
            MoneyDirection direction,
            String ledgerSource,
            String subCategory,
            AccountingEventType eventType,
            String debitAccount,
            String creditAccount,
            String notes) {
        if (location == null || clientTransactionId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (flags.isClientEnabled()) {
            PostMoneyCommand.Builder builder = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(eventDate != null ? eventDate : LocalDate.now())
                    .amount(amt)
                    .direction(direction)
                    .category(MoneyCategory.CLIENT_PAYMENT)
                    .subCategory(subCategory)
                    .referenceType(MoneyReferenceType.other)
                    .referenceId(clientTransactionId)
                    .paymentMode(mapPaymentMode(mode))
                    .requestId("CLIENT:" + subCategory + ":" + clientTransactionId)
                    .ledgerTxnType(ledgerSource)
                    .notes(notes)
                    .eventType(eventType)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata(debitAccount, creditAccount));
            if (direction == MoneyDirection.IN) {
                posting.postIn(builder.build(), MODULE);
            } else {
                posting.postOut(builder.build(), MODULE);
            }
            return;
        }
        legacySync.syncFromUnified(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                direction == MoneyDirection.IN ? LedgerTransactionType.CREDIT : LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                ledgerSource,
                clientTransactionId,
                notes);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isClientEnabled() || referenceId == null || source == null) {
            return false;
        }
        String s = source.trim().toUpperCase();
        if (LedgerSources.CLIENT_PAYMENT.equals(s) || LedgerSources.CLIENT_OUT.equals(s)
                || LedgerSources.CLIENT.equals(s)) {
            posting.voidByRequestId(location, "CLIENT:CLIENT_PAYMENT:" + referenceId, reason, MODULE);
            posting.voidByRequestId(location, "CLIENT:CLIENT_OUT:" + referenceId, reason, MODULE);
            posting.voidByReference(
                    location,
                    MoneyReferenceType.other,
                    referenceId,
                    MoneyCategory.CLIENT_PAYMENT,
                    s,
                    reason,
                    MODULE);
            return true;
        }
        return false;
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
