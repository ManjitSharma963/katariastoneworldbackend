package com.katariastoneworld.apis.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

@Configuration
public class SimpleCorsFilter implements Filter {

    @Value("${cors.allow-all-origins:true}")
    private boolean allowAllOrigins;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}")
    private String allowedOriginsString;

    @Bean
    public FilterRegistrationBean<SimpleCorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<SimpleCorsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(this);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("simpleCorsFilter");
        return registration;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String origin = httpRequest.getHeader("Origin");
        String method = httpRequest.getMethod();
        String requestURI = httpRequest.getRequestURI();
        
        // Log the request details
        System.out.println("========================================");
        System.out.println("[SimpleCorsFilter] Request: " + method + " " + requestURI);
        System.out.println("[SimpleCorsFilter] Origin from request header: " + origin);
        System.out.println("[SimpleCorsFilter] Allow all origins: " + allowAllOrigins);
        
        // Determine if origin is allowed
        boolean isOriginAllowed = false;
        if (origin != null) {
            if (allowAllOrigins) {
                isOriginAllowed = true;
                System.out.println("[SimpleCorsFilter] Allowing origin (global mode): " + origin);
            } else {
                String[] allowedOrigins = allowedOriginsString.split(",\\s*");
                for (String allowed : allowedOrigins) {
                    if (allowed.trim().equals(origin)) {
                        isOriginAllowed = true;
                        System.out.println("[SimpleCorsFilter] Origin is in allowed list: " + origin);
                        break;
                    }
                }
                if (!isOriginAllowed) {
                    System.out.println("[SimpleCorsFilter] Origin NOT in allowed list: " + origin);
                }
            }
        } else {
            System.out.println("[SimpleCorsFilter] No Origin header in request");
        }
        
        // Handle preflight OPTIONS request
        if ("OPTIONS".equalsIgnoreCase(method)) {
            if (isOriginAllowed && origin != null) {
                // CRITICAL: Always use the exact origin from the request, never cache or reuse
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
                httpResponse.setHeader("Access-Control-Allow-Headers", 
                    "Authorization, Content-Type, Accept, X-Requested-With, Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
                httpResponse.setHeader("Access-Control-Max-Age", "0"); // Don't cache preflight
                httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setHeader("Expires", "0");
                httpResponse.setStatus(HttpServletResponse.SC_OK);
                httpResponse.setContentLength(0);
                
                // Verify the header was set correctly before committing
                String setHeader = httpResponse.getHeader("Access-Control-Allow-Origin");
                System.out.println("[SimpleCorsFilter] Set Access-Control-Allow-Origin to: " + origin);
                System.out.println("[SimpleCorsFilter] Verified header value: " + setHeader);
                
                if (!origin.equals(setHeader)) {
                    System.out.println("[SimpleCorsFilter] ERROR: Header mismatch! Forcing correct value...");
                    httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                }
                
                httpResponse.flushBuffer();
                System.out.println("[SimpleCorsFilter] OPTIONS request handled successfully");
                System.out.println("========================================");
                return; // Don't continue filter chain
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.flushBuffer();
                System.out.println("[SimpleCorsFilter] OPTIONS request rejected - origin not allowed");
                System.out.println("========================================");
                return;
            }
        }
        
        // For actual requests, add CORS headers
        if (isOriginAllowed && origin != null) {
            // Use response wrapper to prevent any overrides
            CORSResponseWrapper wrappedResponse = new CORSResponseWrapper(httpResponse, origin);
            wrappedResponse.setHeader("Access-Control-Allow-Origin", origin);
            wrappedResponse.setHeader("Access-Control-Allow-Credentials", "true");
            wrappedResponse.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type");
            
            System.out.println("[SimpleCorsFilter] Added CORS headers for " + method + " request from: " + origin);
            chain.doFilter(request, wrappedResponse);
        } else {
            chain.doFilter(request, response);
        }
        
        System.out.println("========================================");
    }
    
    // Response wrapper to prevent CORS header overrides
    private static class CORSResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private final String allowedOrigin;
        
        public CORSResponseWrapper(HttpServletResponse response, String allowedOrigin) {
            super(response);
            this.allowedOrigin = allowedOrigin;
        }
        
        @Override
        public void setHeader(String name, String value) {
            if ("Access-Control-Allow-Origin".equals(name)) {
                // Always use the request origin, never allow it to be changed
                if (!allowedOrigin.equals(value)) {
                    System.out.println("[SimpleCorsFilter] BLOCKED attempt to change Access-Control-Allow-Origin from " + allowedOrigin + " to " + value);
                }
                super.setHeader(name, allowedOrigin);
            } else {
                super.setHeader(name, value);
            }
        }
        
        @Override
        public void addHeader(String name, String value) {
            if ("Access-Control-Allow-Origin".equals(name)) {
                // Always use the request origin
                if (!allowedOrigin.equals(value)) {
                    System.out.println("[SimpleCorsFilter] BLOCKED attempt to add Access-Control-Allow-Origin with wrong value: " + value);
                }
                super.setHeader(name, allowedOrigin); // Use setHeader to replace any existing value
            } else {
                super.addHeader(name, value);
            }
        }
    }
}

