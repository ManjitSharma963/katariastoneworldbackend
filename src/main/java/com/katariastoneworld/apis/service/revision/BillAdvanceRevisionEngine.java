package com.katariastoneworld.apis.service.revision;

import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.service.BillService;
import org.springframework.stereotype.Component;

/**
 * Advance recalculation engine: reverse wallet usage and re-apply FIFO against bill total (append-only wallet rows).
 */
@Component
public class BillAdvanceRevisionEngine {

    private final BillService billService;

    public BillAdvanceRevisionEngine(BillService billService) {
        this.billService = billService;
    }

    public BillResponseDTO resynchronizeAdvance(Long billId, String billType, String location, Long actorUserId) {
        return billService.resynchronizeAdvanceApplicationForBill(billId, billType, actorUserId, location);
    }
}
