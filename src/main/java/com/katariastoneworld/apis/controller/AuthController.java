package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.dto.AuthResponseDTO;
import com.katariastoneworld.apis.dto.LoginRequestDTO;
import com.katariastoneworld.apis.dto.RegisterRequestDTO;
import com.katariastoneworld.apis.dto.RegisterResponseDTO;
import com.katariastoneworld.apis.dto.UserResponseDTO;
import com.katariastoneworld.apis.service.AuthService;
import com.katariastoneworld.apis.service.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Operation(
            summary = "Register a new user",
            description = "Register a new user account with username, email, password, location, and role"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        try {
            RegisterResponseDTO response = authService.register(registerRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Validation failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error", e.getMessage()));
        }
    }
    
    @Operation(
            summary = "Login and get JWT token",
            description = "Authenticate user credentials and receive JWT token for accessing protected endpoints"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        try {
            AuthResponseDTO response = authService.login(loginRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Authentication failed", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error", e.getMessage()));
        }
    }
    
    @Operation(
            summary = "Get current user details",
            description = "Get details of the currently authenticated user from JWT token"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or expired token")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            String token = extractTokenFromRequest(request);
            if (token == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenErrorResponse("Unauthorized", "Missing authorization token", false, "MISSING_TOKEN"));
            }
            
            JwtUtil.TokenValidationResult validationResult = jwtUtil.validateTokenWithDetails(token);
            if (!validationResult.isValid()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenErrorResponse("Unauthorized", validationResult.getMessage(), 
                                validationResult.isExpired(), 
                                validationResult.isExpired() ? "TOKEN_EXPIRED" : "INVALID_TOKEN"));
            }
            
            Long userId = jwtUtil.extractUserId(token);
            UserResponseDTO user = authService.getCurrentUser(userId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenErrorResponse("Unauthorized", "Invalid or expired token", false, "INVALID_TOKEN"));
        }
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    // Inner class for error responses
    private static class ErrorResponse {
        private String error;
        private String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    // Inner class for token error responses with expiration flag
    private static class TokenErrorResponse {
        private String error;
        private String message;
        private boolean tokenExpired;
        private String code;
        
        public TokenErrorResponse(String error, String message, boolean tokenExpired, String code) {
            this.error = error;
            this.message = message;
            this.tokenExpired = tokenExpired;
            this.code = code;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public boolean isTokenExpired() {
            return tokenExpired;
        }
        
        public void setTokenExpired(boolean tokenExpired) {
            this.tokenExpired = tokenExpired;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
    }
}

