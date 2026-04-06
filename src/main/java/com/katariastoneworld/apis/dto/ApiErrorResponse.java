package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Standard error body for REST clients: stable keys {@code code}, {@code message}, {@code requestId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /** Machine-readable code, e.g. {@code VALIDATION_FAILED}, {@code NOT_FOUND}. */
    private String code;

    /** Human-readable summary. */
    private String message;

    /** Correlates with {@code X-Request-Id} response header. */
    private String requestId;

    /** Field → message for {@code VALIDATION_FAILED}. */
    private Map<String, String> fieldErrors;

    /** Same as {@link #message}; kept for older clients that read {@code error}. */
    @JsonProperty("error")
    public String getError() {
        return message;
    }
}
