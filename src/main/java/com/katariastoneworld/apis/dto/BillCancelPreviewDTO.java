package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancelPreviewDTO {
    private Long billId;
    private String billNumber;
    private String billType;
    private String billLifecycleStatus;
    private String paymentStatus;
    private String customerName;
    private String customerPhone;
    private String billDate;
    private Double billAmount;
    private Double paidAmountExcludingAdvance;
    private Double advanceUsed;
    private Double cashUpiRefund;
    private Double bankRefund;
    private Double totalRefundToCustomer;
    private Double effectiveBillTotalAfterReturns;
    private Double balanceStillDue;
    private Double customerAdvanceBalanceAfter;
    private Boolean draftBill;
    private Boolean alreadyCancelled;
    private List<BillCancelPreviewItemDTO> items = new ArrayList<>();
    private List<BillCancelPreviewPaymentDTO> payments = new ArrayList<>();
    private List<BillCancelPreviewInventoryDTO> inventoryImpact = new ArrayList<>();
}
