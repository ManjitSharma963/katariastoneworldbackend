package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Lightweight child supplementary bill linked to a parent invoice. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillSupplementarySummaryDTO {
    private Long id;
    private String billNumber;
    private LocalDate billDate;
    private Double totalAmount;
    private String paymentStatus;
    private String supplementaryReason;
}
