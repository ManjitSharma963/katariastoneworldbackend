package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestDTO {
    
    @NotBlank(message = "Phone number is required")
    private String phone;
    
    private String name;
    
    private String customerName;
    
    private String address;
    
    private String gstin;
    
    private String email;
    
    private String location; // Bhondsi or Tapugada
}

