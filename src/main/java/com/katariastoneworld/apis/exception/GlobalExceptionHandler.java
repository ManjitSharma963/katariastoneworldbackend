package com.katariastoneworld.apis.exception;

import com.katariastoneworld.apis.dto.ApiErrorResponse;
import com.katariastoneworld.apis.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object v = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (v != null) {
            return v.toString();
        }
        return UUID.randomUUID().toString();
    }

    private static ResponseEntity<ApiErrorResponse> body(HttpServletRequest request, HttpStatus status, String code,
            String message, Map<String, String> fieldErrors) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .code(code)
                .message(message)
                .requestId(resolveRequestId(request))
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).header(RequestIdFilter.REQUEST_ID_HEADER, body.getRequestId()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return body(request, HttpStatus.BAD_REQUEST, "ILLEGAL_ARGUMENT", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid",
                        (a, b) -> a + "; " + b,
                        LinkedHashMap::new));
        String msg = errors.isEmpty() ? "Validation failed" : errors.values().iterator().next();
        return body(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", msg, errors);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpServletRequest request) {
        String resourcePath = ex.getResourcePath();

        if (resourcePath == null || resourcePath.isEmpty() || resourcePath.equals(".") || resourcePath.equals("/")) {
            return body(request, HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "Invalid request path. Please check your API endpoint URL.", null);
        }

        if (resourcePath.startsWith("/auth/")) {
            String apiPath = resourcePath.replaceFirst("^/auth", "/api/auth");
            return body(request, HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "Use the API path: " + apiPath + " (e.g. /api/auth/login).", null);
        }

        if (resourcePath.startsWith("/api/") || resourcePath.startsWith("/inventory")
                || resourcePath.startsWith("/bills") || resourcePath.startsWith("/heroes")) {
            return body(request, HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "API endpoint not found: " + resourcePath, null);
        }

        return body(request, HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Resource not found: " + resourcePath, null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        String m = ex.getMessage() != null ? ex.getMessage() : "";
        if (m.toLowerCase().contains("not found")) {
            return body(request, HttpStatus.NOT_FOUND, "NOT_FOUND", m, null);
        }
        log.error("requestId={} runtime error", resolveRequestId(request), ex);
        return body(request, HttpStatus.INTERNAL_SERVER_ERROR, "RUNTIME_ERROR", m, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("requestId={} unhandled error", resolveRequestId(request), ex);
        return body(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", null);
    }
}
