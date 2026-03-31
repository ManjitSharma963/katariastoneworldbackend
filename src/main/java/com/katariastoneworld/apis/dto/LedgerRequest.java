package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable input for {@link com.katariastoneworld.apis.service.FinancialLedgerService#createEntry(LedgerRequest)}.
 * Idempotency key: {@code sourceType} + {@code sourceId} among non-deleted rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerRequest {

    private String location;
    private String sourceType;
    private String sourceId;
    private LedgerEntryType entryType;
    private BigDecimal amount;
    private BillPaymentMode paymentMode;
    /** e.g. BILL, EXPENSE, CUSTOMER, EMPLOYEE */
    private String referenceType;
    private String referenceId;
    private LocalDate eventDate;

    /** Set for {@code BILL_PAYMENT} rows (GST vs NON_GST). */
    private BillKind billKind;
    /** Bill id when source is a bill payment. */
    private Long billId;
    /** Optional customer id for advances / client flows. */
    private Long customerId;
}
