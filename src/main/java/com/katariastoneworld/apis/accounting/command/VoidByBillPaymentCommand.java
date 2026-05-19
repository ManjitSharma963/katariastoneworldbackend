package com.katariastoneworld.apis.accounting.command;

/** Cancel active money lines keyed by {@code bill_payments.id} (append-only). */
public record VoidByBillPaymentCommand(Long billPaymentId, String voidReason) {

    public static VoidByBillPaymentCommand of(Long billPaymentId, String voidReason) {
        return new VoidByBillPaymentCommand(billPaymentId, voidReason);
    }
}
