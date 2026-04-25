package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API envelope for all JSON endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;

    public static <T> ApiResponseDTO<T> success(String message, T data) {
        return new ApiResponseDTO<>(true, message, data, "");
    }

    public static <T> ApiResponseDTO<T> error(String message, T data, String errorCode) {
        return new ApiResponseDTO<>(false, message, data, errorCode != null ? errorCode : "");
    }
}
