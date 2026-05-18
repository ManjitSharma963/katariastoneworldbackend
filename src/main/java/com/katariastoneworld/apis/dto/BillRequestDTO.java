package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillRequestDTO {
    
    @NotBlank(message = "Customer mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Customer mobile number must be exactly 10 digits")
    private String customerMobileNumber;
    
    // Optional customer details
    private String customerName;
    private String address;
    private String gstin;
    private String customerEmail;
    
    @NotEmpty(message = "Bill must have at least one item")
    @Valid
    private List<BillItemDTO> items;
    
    @NotNull(message = "Tax percentage is required")
    @PositiveOrZero(message = "Tax percentage must be positive or zero")
    private Double taxPercentage;
    
    @NotNull(message = "Discount amount is required")
    @PositiveOrZero(message = "Discount amount must be positive or zero")
    private Double discountAmount;
    
    // Optional charges
    @PositiveOrZero(message = "Labour charge must be positive or zero")
    private Double labourCharge;
    
    @PositiveOrZero(message = "Transportation charge must be positive or zero")
    private Double transportationCharge;
    
    @PositiveOrZero(message = "Other expenses must be positive or zero")
    private Double otherExpenses;
    
    // Optional: If provided, will be used; otherwise calculated
    @PositiveOrZero(message = "Total amount must be positive or zero")
    private Double totalAmount;
    
    // Flag to indicate if this is a simple bill (no GST, no seller details, only items and total)
    private Boolean simpleBill = false;

    /**
     * How the sale was paid (stored in {@code payment_method} on the bill). Optional.
     * Suggested values: {@code cash}, {@code netbanking}, {@code credit}, {@code bank_transfer}
     * (or "bank transfer" — stored as sent, max 50 chars).
     */
    @Size(max = 50, message = "Payment method must be at most 50 characters")
    private String paymentMethod;

    /**
     * Split payments for this bill. When non-null/non-empty, these lines are persisted
     * to {@code bill_payments}. When omitted, legacy {@link #paymentMethod} allocates the full bill total to one mode (if set).
     */
    @Valid
    @JsonAlias({ "paymentLines" })
    private List<BillPaymentRequestDTO> payments;

    // --- GST bill only (ignored for Non-GST); optional ---

    @Size(max = 20, message = "HSN code must be at most 20 characters")
    private String hsnCode;

    @Size(max = 50, message = "Vehicle number must be at most 50 characters")
    private String vehicleNo;

    @Size(max = 2000, message = "Delivery address must be at most 2000 characters")
    private String deliveryAddress;

    /** Optional bill date. Backdated dates require admin approval controls. */
    private LocalDate billDate;

    /** Mandatory for backdated bills. */
    @Size(max = 500, message = "Backdate reason must be at most 500 characters")
    private String backdateReason;

    /** Mandatory for backdated bills. */
    @Size(max = 120, message = "Backdate approvedBy must be at most 120 characters")
    private String backdateApprovedBy;

    /** Allows payment lines with past paymentDate only for admin-approved backdated cash posting. */
    private Boolean allowBackdatedPaymentDate = false;

    /** Mandatory when allowBackdatedPaymentDate=true and a past payment date is supplied. */
    @Size(max = 120, message = "Backdated payment approvedBy must be at most 120 characters")
    private String backdatedPaymentApprovedBy;

    /** Optional linkage for supplementary bills. */
    private Long parentBillId;
    /** Optional linkage for supplementary bills (GST or NON_GST). */
    @Size(max = 16, message = "Parent bill type must be at most 16 characters")
    private String parentBillType;
    /** Optional reason for supplementary bill creation. */
    @Size(max = 500, message = "Supplementary reason must be at most 500 characters")
    private String supplementaryReason;

    /**
     * Non-GST full bill replace only. When prior cash + advance on the bill exceeded the new total after edit,
     * default {@code STORE_CREDIT} posts a customer wallet CREDIT ({@code BILL_EDIT_ADJUSTMENT}). {@code NONE} skips
     * automatic store credit (handle cash refund outside the app).
     */
    private String excessPaymentHandling = "STORE_CREDIT";

    /**
     * Optional operator note. On create, stored on the bill header. On full replace (PUT), if non-blank,
     * a dated block is appended to existing bill notes. Clients may send {@code description} instead of {@code notes}.
     */
    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    @JsonAlias({ "description" })
    private String notes;
}

