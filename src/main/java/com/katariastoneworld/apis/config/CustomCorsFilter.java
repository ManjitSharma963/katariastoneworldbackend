package com.katariastoneworld.apis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// Disabled - using CorsFilterConfig instead
// @Component
// @Order(Ordered.HIGHEST_PRECEDENCE - 1)
public class CustomCorsFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "http://localhost:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3001"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        
        // Handle preflight requests (OPTIONS) first
        if ("OPTIONS".equalsIgnoreCase(method)) {
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
                response.setHeader("Access-Control-Allow-Headers", 
                    "Authorization, Content-Type, Accept, X-Requested-With, Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
                response.setHeader("Access-Control-Max-Age", "3600");
                response.setStatus(HttpServletResponse.SC_OK);
                return; // Don't continue to other filters for preflight
            } else {
                // Origin not allowed for preflight
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        
        // For actual requests, set CORS headers before the chain continues
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            // Set headers before calling filterChain to ensure they're applied
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type");
        }
        
        // Continue with the filter chain
        filterChain.doFilter(request, response);
        
        // Ensure CORS headers are still set after the chain (in case another filter modified them)
        if (origin != null && ALLOWED_ORIGINS.contains(origin) && !response.isCommitted()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
    }
}

