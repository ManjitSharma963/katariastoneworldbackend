package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.VoidByBillPaymentCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class AccountingTransactionVoider {

    private final MoneyTransactionRepository moneyTransactionRepository;

    AccountingTransactionVoider(MoneyTransactionRepository moneyTransactionRepository) {
        this.moneyTransactionRepository = moneyTransactionRepository;
    }

    void voidByRequestId(VoidByRequestIdCommand command) {
        if (command == null || command.requestId() == null || command.requestId().isBlank()) {
            return;
        }
        String location = command.location() != null ? command.location().trim() : "";
        if (location.isEmpty()) {
            return;
        }
        moneyTransactionRepository.findByLocationAndRequestId(location, command.requestId().trim())
                .filter(row -> row.getStatus() == MoneyTxnStatus.ACTIVE && !Boolean.TRUE.equals(row.getIsDeleted()))
                .ifPresent(row -> cancel(row, command.voidReason() != null ? command.voidReason() : "VOID", false));
    }

    void voidByBillPayment(VoidByBillPaymentCommand command) {
        if (command == null || command.billPaymentId() == null) {
            return;
        }
        List<MoneyTransaction> targets = moneyTransactionRepository
                .findByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(command.billPaymentId());
        String reason = command.voidReason() != null ? command.voidReason() : "VOID";
        for (MoneyTransaction row : targets) {
            if (row.getStatus() == MoneyTxnStatus.ACTIVE) {
                cancel(row, reason, false);
            }
        }
    }

    void voidByReference(VoidByReferenceCommand command) {
        if (command == null || command.reference() == null) {
            return;
        }
        String location = command.location() != null ? command.location().trim() : "";
        if (location.isEmpty()) {
            return;
        }
        AccountingReference ref = command.reference();
        if (ref.referenceId() == null || ref.referenceType() == null || ref.category() == null) {
            return;
        }

        List<MoneyTransaction> targets = new ArrayList<>(moneyTransactionRepository
                .findByLocationAndReferenceTypeAndReferenceIdAndCategoryAndStatusAndIsDeletedFalse(
                        location,
                        ref.referenceType(),
                        ref.referenceId(),
                        ref.category(),
                        MoneyTxnStatus.ACTIVE));

        if (targets.isEmpty()) {
            moneyTransactionRepository
                    .findByReferenceIdAndReferenceTypeAndCategory(
                            ref.referenceId(), ref.referenceType(), ref.category())
                    .filter(row -> location.equals(row.getLocation())
                            && row.getStatus() == MoneyTxnStatus.ACTIVE
                            && !Boolean.TRUE.equals(row.getIsDeleted()))
                    .ifPresent(targets::add);
        }

        String txnFilter = ref.ledgerTxnType() != null ? ref.ledgerTxnType().trim() : null;
        String reason = command.voidReason() != null ? command.voidReason() : "VOID";
        for (MoneyTransaction row : targets) {
            if (txnFilter != null && !txnFilter.isEmpty()) {
                if (row.getTxnType() == null || !txnFilter.equals(row.getTxnType().trim())) {
                    continue;
                }
            }
            cancel(row, reason, false);
        }
    }

    private void cancel(MoneyTransaction row, String reason, boolean markDeleted) {
        if (row.getStatus() == MoneyTxnStatus.CANCELLED) {
            return;
        }
        row.setStatus(MoneyTxnStatus.CANCELLED);
        row.setVoidReason(trimReason(reason));
        if (markDeleted) {
            row.setIsDeleted(true);
        }
        moneyTransactionRepository.save(row);
    }

    private static String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        String t = reason.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
