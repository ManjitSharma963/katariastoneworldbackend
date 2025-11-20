package com.katariastoneworld.apis.config;

import com.katariastoneworld.apis.service.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@Order(2) // Run after CORS filter (order 1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/me",
        "/auth/register",
        "/auth/login",
        "/auth/me",
        "/api/inventory",
        "/inventory",
        "/api/heroes",
        "/heroes",
        "/api/categories",
        "/categories"
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip authentication for public endpoints (only GET requests for inventory/heroes/categories)
        if (isPublicEndpoint(requestPath, method)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract token from Authorization header
        String token = extractTokenFromRequest(request);
        
        if (token == null) {
            sendUnauthorizedResponse(response, "Missing authorization token", false);
            return;
        }
        
        // Validate token with detailed information
        JwtUtil.TokenValidationResult validationResult = jwtUtil.validateTokenWithDetails(token);
        
        if (!validationResult.isValid()) {
            // Send specific response for expired tokens to trigger automatic logout
            sendUnauthorizedResponse(response, validationResult.getMessage(), validationResult.isExpired());
            return;
        }
        
        // Token is valid, continue with the request
        // Add user info to request attributes
        try {
            Long userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            String location = jwtUtil.extractLocation(token);
            request.setAttribute("userId", userId);
            request.setAttribute("userEmail", email);
            request.setAttribute("userRole", role);
            request.setAttribute("userLocation", location);
        } catch (Exception e) {
            sendUnauthorizedResponse(response, "Invalid token format", false);
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path, String method) {
        if (path == null) {
            return false;
        }
        // Normalize path (remove trailing slash, handle query params)
        String pathWithoutQuery = path.split("\\?")[0];
        final String normalizedPath;
        if (pathWithoutQuery.endsWith("/") && pathWithoutQuery.length() > 1) {
            normalizedPath = pathWithoutQuery.substring(0, pathWithoutQuery.length() - 1);
        } else {
            normalizedPath = pathWithoutQuery;
        }
        
        // Check if path matches public endpoints
        boolean isPublicPath = PUBLIC_ENDPOINTS.stream()
                .anyMatch(endpoint -> normalizedPath.equals(endpoint) || normalizedPath.startsWith(endpoint + "/"));
        
        // For inventory, heroes, and categories - only GET requests are public
        // POST, PUT, DELETE require authentication
        if (isPublicPath && (normalizedPath.startsWith("/api/inventory") || 
                             normalizedPath.startsWith("/inventory") ||
                             normalizedPath.startsWith("/api/heroes") ||
                             normalizedPath.startsWith("/heroes") ||
                             normalizedPath.startsWith("/api/categories") ||
                             normalizedPath.startsWith("/categories"))) {
            return "GET".equalsIgnoreCase(method);
        }
        
        // For auth endpoints, all methods are public
        return isPublicPath;
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private void sendUnauthorizedResponse(HttpServletResponse response, String message, boolean isExpired) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Include tokenExpired flag so frontend can automatically logout
        String jsonResponse = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"tokenExpired\":%s,\"code\":\"%s\"}",
            message,
            isExpired,
            isExpired ? "TOKEN_EXPIRED" : "INVALID_TOKEN"
        );
        response.getWriter().write(jsonResponse);
    }
}

