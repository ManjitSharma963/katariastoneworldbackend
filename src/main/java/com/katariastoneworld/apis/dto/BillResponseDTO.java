package com.katariastoneworld.apis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillResponseDTO {
    
    private Long id;
    private String billNumber;
    private String billType; // "GST" or "NON_GST"
    private Long customerId;
    private String customerMobileNumber;
    private String customerName;
    private String address;
    private String gstin;
    private String customerEmail;
    private LocalDate billDate;
    private List<BillItemDTO> items;
    private Double totalSqft;
    private Double subtotal;
    private Double taxPercentage;
    private Double taxAmount;
    private Double serviceCharge;
    private Double labourCharge;
    private Double transportationCharge;
    private Double otherExpenses;
    private Double discountAmount;
    private Double totalAmount;
    private String paymentStatus;
    /** @deprecated Legacy summary string; source-of-truth is {@code payments}/{@code bill_payments}. */
    @Deprecated
    @Schema(description = "How the customer paid (e.g. cash, netbanking). \"-\" if not recorded.")
    private String paymentMethod;
    /** Same value as {@link #paymentMethod}; use in sale tables / UI that expect this name. */
    @Schema(description = "Payment mode (alias of paymentMethod) for sales listing.")
    private String paymentMode;

    /** Sum of {@code bill_payments} for this bill (legacy PAID bills without rows may infer full total). */
    private Double totalPaid;

    /** Persisted bill paid amount column (excluding advance usage). */
    private Double paidAmount;

    /** Portion of this bill covered by customer advance / token balance (not in bill_payments). */
    private Double advanceUsed;

    /** Remaining bill balance (never negative). */
    private Double amountDue;

    private List<BillPaymentResponseDTO> payments = new ArrayList<>();

    private String notes;
    private LocalDateTime createdAt;
    private Long createdByUserId; // User (staff) who created this bill
    private Boolean simpleBill = false; // Flag to indicate if this is a simple bill (no GST, no seller details)
    /** Customer/location for this bill; used to fetch seller details for GST bill PDF. */
    private String location;

    /** GST bills only: optional default HSN, vehicle no., delivery address. */
    private String hsnCode;
    private String vehicleNo;
    private String deliveryAddress;
}
