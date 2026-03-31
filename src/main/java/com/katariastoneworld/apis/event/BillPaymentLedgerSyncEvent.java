package com.katariastoneworld.apis.event;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Published after {@code bill_payments} rows are persisted or soft-deleted.
 * Handled synchronously in the same transaction so ledger and payment row commit or roll back together.
 */
public record BillPaymentLedgerSyncEvent(
        String location,
        BillKind billKind,
        Long billId,
        Long paymentId,
        BillPaymentMode paymentMode,
        BigDecimal amount,
        LocalDate paymentDate,
        boolean active
) {
}
