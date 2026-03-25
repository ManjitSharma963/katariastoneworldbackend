package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillRequestDTO {
    
    @NotBlank(message = "Customer mobile number is required")
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
}

