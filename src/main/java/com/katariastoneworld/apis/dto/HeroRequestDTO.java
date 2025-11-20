package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeroRequestDTO {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Image URL is required")
    @JsonAlias({"image_url", "imageUrl"})
    private String imageUrl;
    
    private String subtitle;
    
    @JsonAlias({"display_order", "displayOrder"})
    private Integer displayOrder;
    
    @JsonAlias({"is_active", "isActive"})
    private Boolean isActive;
}

