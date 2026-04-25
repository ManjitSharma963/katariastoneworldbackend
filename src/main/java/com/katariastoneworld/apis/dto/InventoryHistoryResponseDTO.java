package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHistoryResponseDTO {

    private Long id;
    private Long productId;
    private String actionType;
    private Double quantityChanged;
    private Double previousQuantity;
    private Double newQuantity;
    private Long referenceId;
    private String notes;
    private LocalDateTime createdAt;

    /** Ledger: {@link com.katariastoneworld.apis.entity.InventoryTxnType} name when using inventory_transactions. */
    private String txnType;
    /** Ledger: IN or OUT. */
    private String direction;
    /** Ledger: BILL, PURCHASE, MANUAL. */
    private String referenceType;

    /** When set (e.g. bill date), used for date-range inventory reports instead of {@link #createdAt} only. */
    private LocalDate businessDate;
}
