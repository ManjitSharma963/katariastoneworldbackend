package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillCancellationLogDTO {
    private Long id;
    private String billKind;
    private Long billId;
    private String billNumber;
    private String location;
    private LocalDate billDate;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalAmount;
    private BigDecimal paidFromPayments;
    private BigDecimal advanceApplied;
    private BigDecimal inHandCollected;
    private String paymentMethodSummary;
    private String paymentStatus;
    private LocalDateTime cancelledAt;
    private Long cancelledByUserId;
}
