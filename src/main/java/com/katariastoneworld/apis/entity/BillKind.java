package com.katariastoneworld.apis.entity;

/**
 * Discriminator for {@link BillPayment} and {@link Bill}: matches {@code bills.bill_kind} / {@code bill_payments.bill_kind}.
 */
public enum BillKind {
    GST,
    NON_GST
}
