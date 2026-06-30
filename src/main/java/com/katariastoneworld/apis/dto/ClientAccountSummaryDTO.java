package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAccountSummaryDTO {
    private String clientName;
    private ClientChannelBalanceDTO combined;
    private ClientChannelBalanceDTO gst;
    private ClientChannelBalanceDTO nonGst;
}
