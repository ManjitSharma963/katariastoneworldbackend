package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillLineQuantityPatchLineDTO {

    @NotNull
    private Long billItemId;

    /** New billed quantity for this line (must stay at least the quantity already returned on this line). */
    @NotNull
    @Positive
    private Double quantity;
}
