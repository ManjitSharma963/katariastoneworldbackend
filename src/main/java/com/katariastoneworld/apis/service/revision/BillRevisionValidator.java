package com.katariastoneworld.apis.service.revision;

import com.katariastoneworld.apis.dto.BillLineQuantitiesPatchRequestDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillRevisionEditMode;
import com.katariastoneworld.apis.dto.BillRevisionEditRequest;
import com.katariastoneworld.apis.service.BillService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Central validation for bill revision requests (lifecycle, location, payload shape).
 */
@Component
public class BillRevisionValidator {

    private final BillService billService;

    public BillRevisionValidator(BillService billService) {
        this.billService = billService;
    }

    public void validateEditRequest(BillRevisionEditRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getMode(), "mode");
        Objects.requireNonNull(request.getBillId(), "billId");
        Objects.requireNonNull(request.getBillType(), "billType");
        Objects.requireNonNull(request.getLocation(), "location");
        Objects.requireNonNull(request.getActorUserId(), "actorUserId");
        billService.assertBillMutableForRevision(request.getBillId(), request.getBillType(), request.getLocation());
        if (request.getMode() == BillRevisionEditMode.FULL_REPLACE) {
            if (request.getReplaceRequest() == null) {
                throw new IllegalArgumentException("replaceRequest is required for FULL_REPLACE");
            }
            if (request.getActorRole() == null || request.getActorRole().isBlank()) {
                throw new IllegalArgumentException("actorRole is required for FULL_REPLACE");
            }
            validateReplacePayload(request.getReplaceRequest());
        } else if (request.getMode() == BillRevisionEditMode.LINE_QUANTITIES_PATCH) {
            if (request.getPatchRequest() == null) {
                throw new IllegalArgumentException("patchRequest is required for LINE_QUANTITIES_PATCH");
            }
            validatePatchPayload(request.getPatchRequest());
        } else {
            throw new IllegalArgumentException("Unsupported mode: " + request.getMode());
        }
    }

    public List<String> validateReplacePayloadSoft(BillRequestDTO req) {
        List<String> issues = new ArrayList<>();
        if (req == null) {
            issues.add("Request body is required");
            return issues;
        }
        if (req.getCustomerMobileNumber() == null || !req.getCustomerMobileNumber().matches("^[0-9]{10}$")) {
            issues.add("Customer mobile must be 10 digits");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            issues.add("At least one line item is required");
        } else {
            for (int i = 0; i < req.getItems().size(); i++) {
                var it = req.getItems().get(i);
                if (it == null || it.getItemName() == null || it.getItemName().isBlank()) {
                    issues.add("Line " + (i + 1) + ": item name is required");
                }
                if (it != null && (it.getQuantity() == null || it.getQuantity() <= 0)) {
                    issues.add("Line " + (i + 1) + ": quantity must be positive");
                }
                if (it != null && (it.getPricePerUnit() == null || it.getPricePerUnit() < 0)) {
                    issues.add("Line " + (i + 1) + ": price must be zero or positive");
                }
            }
        }
        return issues;
    }

    private void validateReplacePayload(BillRequestDTO req) {
        List<String> issues = validateReplacePayloadSoft(req);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", issues));
        }
    }

    private void validatePatchPayload(BillLineQuantitiesPatchRequestDTO req) {
        boolean hasLines = req.getLines() != null && !req.getLines().isEmpty();
        boolean hasAdded = req.getAddedItems() != null && !req.getAddedItems().isEmpty();
        if (!hasLines && !hasAdded) {
            throw new IllegalArgumentException("At least one line patch or addedItems row is required");
        }
    }
}
