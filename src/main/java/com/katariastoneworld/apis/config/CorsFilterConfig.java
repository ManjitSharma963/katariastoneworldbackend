package com.katariastoneworld.apis.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CorsFilterConfig {

    @Value("${cors.allow-all-origins:true}")
    private boolean allowAllOrigins;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}")
    private String allowedOriginsString;
    
    @PostConstruct
    public void init() {
        System.out.println("========================================");
        System.out.println("[CORS Config] CORS Configuration Loaded");
        System.out.println("[CORS Config] Allow all origins: " + allowAllOrigins);
        System.out.println("[CORS Config] Allowed origins string: " + allowedOriginsString);
        System.out.println("========================================");
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
        List<String> originsList = getAllowedOriginsList();
        System.out.println("[CORS Config] Initializing CORS Filter");
        System.out.println("[CORS Config] Allow all origins: " + allowAllOrigins);
        System.out.println("[CORS Config] Allowed origins list: " + originsList);
        registration.setFilter(new CorsFilter(allowAllOrigins, originsList));
        registration.addUrlPatterns("/*");
        // Use HIGHEST_PRECEDENCE so this filter runs FIRST to handle OPTIONS preflight requests
        // The response wrapper ensures headers can't be overridden by later filters
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("corsFilter");
        registration.setEnabled(true);
        return registration;
    }

    private List<String> getAllowedOriginsList() {
        if (allowAllOrigins) {
            System.out.println("[CORS Config] Global mode enabled - will allow all origins");
            return Collections.emptyList(); // Empty list means allow all
        }
        if (allowedOriginsString == null || allowedOriginsString.trim().isEmpty()) {
            System.out.println("[CORS Config] Using default allowed origins");
            return Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:3001"
            );
        }
        List<String> origins = Arrays.asList(allowedOriginsString.split(",\\s*"));
        System.out.println("[CORS Config] Using configured allowed origins: " + origins);
        return origins;
    }

    public static class CorsFilter implements Filter {
        private final boolean allowAllOrigins;
        private final List<String> allowedOrigins;

        public CorsFilter(boolean allowAllOrigins, List<String> allowedOrigins) {
            this.allowAllOrigins = allowAllOrigins;
            this.allowedOrigins = allowedOrigins;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String origin = httpRequest.getHeader("Origin");
            String method = httpRequest.getMethod();
            String requestURI = httpRequest.getRequestURI();
            
            // Debug logging
            System.out.println("========================================");
            System.out.println("[CORS Filter] Request: " + method + " " + requestURI);
            System.out.println("[CORS Filter] Origin from request: " + origin);
            System.out.println("[CORS Filter] Allow all origins: " + allowAllOrigins);
            if (!allowAllOrigins && !allowedOrigins.isEmpty()) {
                System.out.println("[CORS Filter] Allowed origins: " + allowedOrigins);
            }
            
            // Check if origin is allowed
            boolean isOriginAllowed;
            if (allowAllOrigins) {
                // Allow any origin (except null)
                isOriginAllowed = origin != null;
                System.out.println("[CORS Filter] Global CORS enabled - allowing all origins");
            } else {
                // Check against allowed list
                isOriginAllowed = origin != null && (allowedOrigins.isEmpty() || allowedOrigins.contains(origin));
            }
            System.out.println("[CORS Filter] Origin allowed: " + isOriginAllowed);
            
            // Handle preflight requests (OPTIONS)
            if ("OPTIONS".equalsIgnoreCase(method)) {
                System.out.println("[CORS Filter] Handling OPTIONS preflight request");
                if (isOriginAllowed) {
                    // Use wrapper to prevent any overrides
                    CorsResponseWrapper wrappedResponse = new CorsResponseWrapper(httpResponse, origin, allowAllOrigins, allowedOrigins);
                    
                    // DON'T use reset() - it clears headers. Just set them directly
                    // Set CORS headers for preflight - MUST use the request origin
                    wrappedResponse.setHeader("Access-Control-Allow-Origin", origin);
                    wrappedResponse.setHeader("Access-Control-Allow-Credentials", "true");
                    wrappedResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
                    wrappedResponse.setHeader("Access-Control-Allow-Headers", 
                        "Authorization, Content-Type, Accept, X-Requested-With, Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
                    // Reduce cache time to prevent stale CORS responses
                    wrappedResponse.setHeader("Access-Control-Max-Age", "0");
                    // Also add cache control to prevent browser caching
                    wrappedResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                    wrappedResponse.setHeader("Pragma", "no-cache");
                    wrappedResponse.setHeader("Expires", "0");
                    wrappedResponse.setStatus(HttpServletResponse.SC_OK);
                    
                    // Verify what was actually set
                    String actualHeader = wrappedResponse.getHeader("Access-Control-Allow-Origin");
                    System.out.println("[CORS Filter] Set Access-Control-Allow-Origin to: " + origin);
                    System.out.println("[CORS Filter] Actual header value after setting: " + actualHeader);
                    
                    // Double-check and force if needed
                    if (actualHeader == null || !origin.equals(actualHeader)) {
                        System.out.println("[CORS Filter] WARNING: Header mismatch! Forcing correct value...");
                        wrappedResponse.setHeader("Access-Control-Allow-Origin", origin);
                        actualHeader = wrappedResponse.getHeader("Access-Control-Allow-Origin");
                        System.out.println("[CORS Filter] After force, header value: " + actualHeader);
                    }
                    
                    System.out.println("========================================");
                    return; // Don't continue to other filters
                } else {
                    System.out.println("[CORS Filter] Origin not allowed, returning 403");
                    System.out.println("========================================");
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
            
            // For actual requests (GET, POST, etc.), wrap the response to ensure headers can't be overridden
            if (isOriginAllowed) {
                // Create a wrapper that ensures CORS headers are always set correctly
                CorsResponseWrapper wrappedResponse = new CorsResponseWrapper(httpResponse, origin, allowAllOrigins, allowedOrigins);
                
                // Set CORS headers before the chain
                wrappedResponse.setHeader("Access-Control-Allow-Origin", origin);
                wrappedResponse.setHeader("Access-Control-Allow-Credentials", "true");
                wrappedResponse.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type");
                
                System.out.println("[CORS Filter] Set Access-Control-Allow-Origin to: " + origin);
                
                chain.doFilter(request, wrappedResponse);
                
                // Verify headers are still correct after the chain
                String finalHeader = wrappedResponse.getHeader("Access-Control-Allow-Origin");
                if (finalHeader == null || !origin.equals(finalHeader)) {
                    System.out.println("[CORS Filter] WARNING: Header was changed! Fixing...");
                    wrappedResponse.setHeader("Access-Control-Allow-Origin", origin);
                }
                
                System.out.println("========================================");
            } else {
                // Origin not allowed, continue without CORS headers
                System.out.println("[CORS Filter] Origin not allowed, continuing without CORS headers");
                System.out.println("========================================");
                chain.doFilter(request, response);
            }
        }
    }
    
    // Helper class for response wrapper
    private static class CorsResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private final String allowedOrigin;
        private final boolean allowAllOrigins;
        private final List<String> allowedOrigins;
        
        public CorsResponseWrapper(HttpServletResponse response, String allowedOrigin, boolean allowAllOrigins, List<String> allowedOrigins) {
            super(response);
            this.allowedOrigin = allowedOrigin;
            this.allowAllOrigins = allowAllOrigins;
            this.allowedOrigins = allowedOrigins;
        }
        
        @Override
        public void setHeader(String name, String value) {
            // Prevent overriding Access-Control-Allow-Origin with wrong value
            if ("Access-Control-Allow-Origin".equals(name)) {
                if (allowAllOrigins) {
                    // In global mode, always use the request origin (not any value)
                    System.out.println("[CORS Filter] Global mode - forcing origin to: " + allowedOrigin);
                    super.setHeader(name, allowedOrigin); // Always use request origin in global mode
                    return;
                } else {
                    // In restricted mode, only allow from the list
                    if (!allowedOrigin.equals(value) && !allowedOrigins.contains(value)) {
                        System.out.println("[CORS Filter] Blocked attempt to set Access-Control-Allow-Origin to: " + value);
                        super.setHeader(name, allowedOrigin); // Force correct origin
                        return;
                    }
                }
            }
            super.setHeader(name, value);
        }
        
        @Override
        public void addHeader(String name, String value) {
            if ("Access-Control-Allow-Origin".equals(name)) {
                if (allowAllOrigins) {
                    // In global mode, always use the request origin
                    System.out.println("[CORS Filter] Global mode - forcing origin to: " + allowedOrigin);
                    super.setHeader(name, allowedOrigin); // Always use request origin
                    return;
                } else {
                    // In restricted mode, only allow from the list
                    if (!allowedOrigin.equals(value) && !allowedOrigins.contains(value)) {
                        System.out.println("[CORS Filter] Blocked attempt to add Access-Control-Allow-Origin: " + value);
                        System.out.println("[CORS Filter] Forcing correct origin: " + allowedOrigin);
                        super.setHeader(name, allowedOrigin); // Force correct origin
                        return;
                    }
                }
            }
            super.addHeader(name, value);
        }
        
        // Override reset methods to preserve CORS headers
        @Override
        public void reset() {
            String savedOrigin = super.getHeader("Access-Control-Allow-Origin");
            String savedCredentials = super.getHeader("Access-Control-Allow-Credentials");
            super.reset();
            // Restore CORS headers after reset
            if (savedOrigin == null || !allowedOrigin.equals(savedOrigin)) {
                super.setHeader("Access-Control-Allow-Origin", allowedOrigin);
            } else {
                super.setHeader("Access-Control-Allow-Origin", savedOrigin);
            }
            if (savedCredentials != null) {
                super.setHeader("Access-Control-Allow-Credentials", savedCredentials);
            } else {
                super.setHeader("Access-Control-Allow-Credentials", "true");
            }
        }
    }
}

