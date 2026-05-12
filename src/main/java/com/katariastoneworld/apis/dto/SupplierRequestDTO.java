package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupplierRequestDTO {

    @NotBlank(message = "Supplier name is required")
    private String name;

    /**
     * Required: exactly 10 digits after stripping non-digits (validated in {@link com.katariastoneworld.apis.service.SupplierService}).
     */
    @NotBlank(message = "Contact number is required")
    @JsonAlias({ "contact_number", "contactNumber" })
    private String contactNumber;

    private String address;
}
