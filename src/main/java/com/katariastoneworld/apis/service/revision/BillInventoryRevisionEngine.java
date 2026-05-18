package com.katariastoneworld.apis.service.revision;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.service.BillService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory adjustment engine for bill revisions: pre-flight net stock for full replace (append-only ledger on apply).
 */
@Component
public class BillInventoryRevisionEngine {

    private final BillService billService;

    public BillInventoryRevisionEngine(BillService billService) {
        this.billService = billService;
    }

    /**
     * Validates bill can be fully replaced (no partial stock returns) and net qty increases fit on-hand stock.
     * Does not write inventory rows.
     */
    public void preflightFullReplace(Long billId, String billType, String location, BillRequestDTO proposal) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location is required for inventory pre-flight");
        }
        billService.assertEligibleForFullBillReplace(billId, billType, location);
        billService.assertNetStockSufficientForFullReplace(billId, billType, proposal);
    }

    /**
     * Collects stock warnings without throwing (for UI preview). Still runs eligibility checks.
     */
    public List<String> collectStockWarningsForProposal(Long billId, String billType, String location, BillRequestDTO proposal) {
        List<String> warnings = new ArrayList<>();
        if (proposal == null || proposal.getItems() == null) {
            return warnings;
        }
        try {
            billService.assertEligibleForFullBillReplace(billId, billType, location);
            billService.assertNetStockSufficientForFullReplace(billId, billType, proposal);
        } catch (IllegalArgumentException ex) {
            warnings.add(ex.getMessage());
        }
        return warnings;
    }
}
