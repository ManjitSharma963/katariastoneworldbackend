package com.katariastoneworld.apis.entity;

/**
 * Discriminator for {@link BillPayment}: GST bills use {@code bills_gst.id}, non-GST use {@code bills_non_gst.id}.
 */
public enum BillKind {
    GST,
    NON_GST
}
