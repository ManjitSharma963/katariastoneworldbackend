package com.katariastoneworld.apis.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFoundException(NoResourceFoundException ex) {
        Map<String, String> error = new HashMap<>();
        String resourcePath = ex.getResourcePath();
        
        // Handle empty or null paths
        if (resourcePath == null || resourcePath.isEmpty() || resourcePath.equals(".") || resourcePath.equals("/")) {
            error.put("error", "Invalid request path. Please check your API endpoint URL.");
            error.put("message", "Available API endpoints: /api/inventory, /api/bills, /api/heroes");
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
        
        // Provide helpful message for API endpoints
        if (resourcePath.startsWith("/api/") || resourcePath.startsWith("/inventory") || 
            resourcePath.startsWith("/bills") || resourcePath.startsWith("/heroes")) {
            error.put("error", "API endpoint not found: " + resourcePath + ". Please check the endpoint URL.");
            error.put("message", "Make sure you're calling the correct API endpoint. Available endpoints: /api/inventory, /api/bills, /api/heroes");
        } else {
            error.put("error", "Resource not found: " + resourcePath);
            error.put("message", "This is an API server. Static resources are not available.");
        }
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

