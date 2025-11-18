package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponseDTO {
    private Long id;
    private String phone;
    private String name;
    private String customerName;
    private String address;
    private String gstin;
    private String email;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

