package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    // Optional: If provided, will be used; otherwise calculated
    @PositiveOrZero(message = "Total amount must be positive or zero")
    private Double totalAmount;
    
    // Flag to indicate if this is a simple bill (no GST, no seller details, only items and total)
    private Boolean simpleBill = false;
}

