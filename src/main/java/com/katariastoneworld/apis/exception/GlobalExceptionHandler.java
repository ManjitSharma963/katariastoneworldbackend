package com.katariastoneworld.apis.exception;

import com.katariastoneworld.apis.dto.ApiResponseDTO;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(
                        ex.getMessage() != null ? ex.getMessage() : "Invalid request",
                        null,
                        "INVALID_ARGUMENT"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDTO.error(
                        ex.getMessage() != null ? ex.getMessage() : "Unauthorized",
                        null,
                        "UNAUTHORIZED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error("Validation failed", fieldErrors, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleNoResourceFoundException(NoResourceFoundException ex) {
        String resourcePath = ex.getResourcePath();
        Map<String, String> payload = new LinkedHashMap<>();

        if (resourcePath == null || resourcePath.isBlank() || resourcePath.equals(".") || resourcePath.equals("/")) {
            payload.put("path", String.valueOf(resourcePath));
            payload.put("hint", "Available endpoints include /api/inventory, /api/bills, /api/auth");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.error("Invalid request path", payload, "RESOURCE_NOT_FOUND"));
        }

        if (resourcePath.startsWith("/auth/")) {
            payload.put("path", resourcePath);
            payload.put("suggestedPath", resourcePath.replaceFirst("^/auth", "/api/auth"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.error("Endpoint not found", payload, "RESOURCE_NOT_FOUND"));
        }

        payload.put("path", resourcePath);
        payload.put("hint", "Check the endpoint URL and HTTP method");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDTO.error("Resource not found", payload, "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String msg = root.getMessage() != null ? root.getMessage() : "Database constraint violation";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(msg, null, "DATA_INTEGRITY_ERROR"));
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleTransactionSystem(TransactionSystemException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage() != null ? root.getMessage() : "Transaction commit failed";
        String msg = "Could not commit JPA transaction: " + rootMsg;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(msg, null, "TXN_COMMIT_ERROR"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error(
                        ex.getMessage() != null ? ex.getMessage() : "Request failed",
                        null,
                        "RUNTIME_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error("An unexpected error occurred", null, "INTERNAL_ERROR"));
    }
}
