package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private Double discountAmount;
    private Double totalAmount;
    private String paymentStatus;
    private String paymentMethod;
    private String notes;
    private LocalDateTime createdAt;
    private Boolean simpleBill = false; // Flag to indicate if this is a simple bill (no GST, no seller details)
}
