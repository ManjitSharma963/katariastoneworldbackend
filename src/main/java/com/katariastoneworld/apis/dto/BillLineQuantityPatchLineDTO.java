package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillLineQuantityPatchLineDTO {

    @NotNull
    private Long billItemId;

    /**
     * New billed quantity for this line (must stay at least the quantity already returned on this line).
     * Use {@code 0} to remove the line (restores remaining sold stock); not allowed when partial stock returns exist on the line.
     */
    @NotNull
    @PositiveOrZero
    private Double quantity;
}
