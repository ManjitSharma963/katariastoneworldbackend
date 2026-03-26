package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealerRequestDTO {

    @NotBlank(message = "Dealer name is required")
    private String name;

    @JsonAlias({ "contact_number", "contactNumber" })
    private String contactNumber;

    private String address;
}
