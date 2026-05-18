package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for {@link com.katariastoneworld.apis.service.BillRevisionService#editBill}.
 * Supply {@link #replaceRequest} when {@link #mode} is {@link BillRevisionEditMode#FULL_REPLACE},
 * or {@link #patchRequest} when {@link #mode} is {@link BillRevisionEditMode#LINE_QUANTITIES_PATCH}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillRevisionEditRequest {

    private BillRevisionEditMode mode;
    private Long billId;
    private String billType;
    private String location;
    private Long actorUserId;
    /** Required for full replace (backdate rules, payment resolution). */
    private String actorRole;
    private BillRequestDTO replaceRequest;
    private BillLineQuantitiesPatchRequestDTO patchRequest;
}
