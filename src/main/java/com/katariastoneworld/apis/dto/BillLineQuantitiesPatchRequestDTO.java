package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillLineQuantitiesPatchRequestDTO {

    /**
     * Existing lines to update (quantities). May be empty when only {@link #addedItems} is used.
     */
    @Valid
    private List<BillLineQuantityPatchLineDTO> lines = new ArrayList<>();

    /**
     * New lines to append (Case D): stock is reserved, SALE/OUT posted, reservation consumed (Non-GST; GST when stock flag on).
     */
    @Valid
    private List<BillItemDTO> addedItems = new ArrayList<>();
}
