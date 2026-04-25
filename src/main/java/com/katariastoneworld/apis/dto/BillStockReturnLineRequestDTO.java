package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillStockReturnLineRequestDTO {

    /** PK of {@code bill_items_gst} or {@code bill_items_non_gst} (see bill type on path). */
    @NotNull
    private Long billItemId;

    @NotNull
    @Positive
    private Double quantity;
}
