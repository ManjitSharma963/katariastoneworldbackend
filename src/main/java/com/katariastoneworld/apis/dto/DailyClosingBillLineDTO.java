package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingBillLineDTO {

    /** GST or NON_GST */
    private String billType;

    /** Primary key in {@code bills_gst} or {@code bills_non_gst}. */
    private Long billId;

    private String billNumber;
    private LocalDate billDate;
    private Double totalAmount;
    private Double paidAmount;
    private Double dueAmount;

    /** PAID, PARTIAL, or DUE (computed from amounts). */
    private String status;

    /** Comma-separated modes from payment rows, or legacy summary when no rows. */
    private String paymentModes;

    /** Amounts allocated to each bucket for this bill (split payments). CHEQUE is included in {@link #otherAmount} for a 4-column UI (Cash / UPI / Bank / Other). */
    private Double cashAmount;
    private Double upiAmount;
    private Double bankTransferAmount;
    /** Cheques and any non-standard modes; shown as "Other" in UI. */
    private Double otherAmount;

    /**
     * When total payments on this bill exceed the bill total (data entry or rounding), this is the excess amount; otherwise 0.
     * {@link #status} remains PAID for backward compatibility.
     */
    private Double overpaidAmount;
}
