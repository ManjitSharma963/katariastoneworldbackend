package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Reconstructed stock for one product at the end of {@code rangeEnd} and optionally {@code rangeStart},
 * using {@code inventory_history} movements after those dates (see {@link com.katariastoneworld.apis.service.ProductService#getStockAsOf}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockAsOfRowDTO {
    private Long productId;
    /** Quantity at end of range-end date; null if the product did not exist yet on that date. */
    private BigDecimal quantityAtEnd;
    /** Quantity at end of range-start date when {@code rangeStart} was requested; null if not applicable. */
    private BigDecimal quantityAtStart;
}
