package com.katariastoneworld.apis.accounting.dto;

import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyReferenceType;

/** Stable domain reference for a money line in {@code transactions}. */
public record AccountingReference(
        MoneyReferenceType referenceType,
        Long referenceId,
        MoneyCategory category,
        String ledgerTxnType) {

    public static AccountingReference of(MoneyReferenceType referenceType, Long referenceId, MoneyCategory category) {
        return new AccountingReference(referenceType, referenceId, category, null);
    }

    public static AccountingReference of(
            MoneyReferenceType referenceType,
            Long referenceId,
            MoneyCategory category,
            String ledgerTxnType) {
        return new AccountingReference(referenceType, referenceId, category, ledgerTxnType);
    }
}
