package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponseDTO {
    private Long id;
    private String name;
    private String contactNumber;
    private String address;
    private String location;
    private LocalDateTime createdAt;
}
