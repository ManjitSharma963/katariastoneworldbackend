package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAsOfResponseDTO {
    private LocalDate rangeEnd;
    private LocalDate rangeStart;
    private List<ProductStockAsOfRowDTO> rows;
    /** Short note on how figures are derived and limitations. */
    private String explanation;
}
