package com.katariastoneworld.apis.dto;

import lombok.Data;

import java.util.List;

@Data
public class CashbookListResponseDTO {
    private List<CashbookRowDTO> rows;
    private TodayBudgetDTO summary;
}
