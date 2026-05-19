package com.katariastoneworld.apis.accounting.dto;

import com.katariastoneworld.apis.entity.MoneyTransaction;

/** Result of a post operation. */
public record PostedTransactionResult(Long transactionId, boolean idempotentReplay) {

    public static PostedTransactionResult from(MoneyTransaction row, boolean idempotentReplay) {
        return new PostedTransactionResult(row != null ? row.getId() : null, idempotentReplay);
    }
}
