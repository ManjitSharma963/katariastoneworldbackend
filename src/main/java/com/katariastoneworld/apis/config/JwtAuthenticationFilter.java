package com.katariastoneworld.apis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katariastoneworld.apis.service.JwtUtil;
import com.katariastoneworld.apis.util.LedgerAuditContext;
import com.katariastoneworld.apis.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(2)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/me",
            "/auth/register",
            "/auth/login",
            "/auth/me",
            "/api/website-products",
            "/website-products",
            "/api/heroes",
            "/heroes",
            "/api/categories",
            "/categories",
            "/swagger-ui.html",
            "/swagger-ui",
            "/swagger-ui/",
            "/swagger-ui/index.html",
            "/api-docs",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus"
    );

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublicEndpoint(requestPath, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractTokenFromRequest(request);

        if (token == null) {
            sendUnauthorizedResponse(request, response, "Missing authorization token", false);
            return;
        }

        JwtUtil.TokenValidationResult validationResult = jwtUtil.validateTokenWithDetails(token);

        if (!validationResult.isValid()) {
            sendUnauthorizedResponse(request, response, validationResult.getMessage(), validationResult.isExpired());
            return;
        }

        final Long userId;
        try {
            userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            String location = jwtUtil.extractLocation(token);
            request.setAttribute("userId", userId);
            request.setAttribute("userEmail", email);
            request.setAttribute("userRole", role);
            request.setAttribute("userLocation", location);
        } catch (Exception e) {
            sendUnauthorizedResponse(request, response, "Invalid token format", false);
            return;
        }

        try {
            LedgerAuditContext.setUserId(userId);
            filterChain.doFilter(request, response);
        } finally {
            LedgerAuditContext.clear();
        }
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path == null) {
            return false;
        }
        String pathWithoutQuery = path.split("\\?")[0];
        final String normalizedPath;
        if (pathWithoutQuery.endsWith("/") && pathWithoutQuery.length() > 1) {
            normalizedPath = pathWithoutQuery.substring(0, pathWithoutQuery.length() - 1);
        } else {
            normalizedPath = pathWithoutQuery;
        }

        if (normalizedPath.startsWith("/actuator/health")
                || normalizedPath.startsWith("/actuator/info")
                || normalizedPath.startsWith("/actuator/prometheus")) {
            return true;
        }

        boolean isPublicPath = PUBLIC_ENDPOINTS.stream()
                .anyMatch(endpoint -> normalizedPath.equals(endpoint) || normalizedPath.startsWith(endpoint + "/"));

        if (normalizedPath.startsWith("/swagger-ui")
                || normalizedPath.startsWith("/api-docs")
                || normalizedPath.startsWith("/v3/api-docs")) {
            return true;
        }

        if (isPublicPath && (normalizedPath.startsWith("/api/website-products")
                || normalizedPath.startsWith("/website-products")
                || normalizedPath.startsWith("/api/heroes")
                || normalizedPath.startsWith("/heroes")
                || normalizedPath.startsWith("/api/categories")
                || normalizedPath.startsWith("/categories"))) {
            return "GET".equalsIgnoreCase(method);
        }

        return isPublicPath;
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, String message,
            boolean isExpired) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("error", "Unauthorized");
        json.put("message", message);
        json.put("tokenExpired", isExpired);
        json.put("code", isExpired ? "TOKEN_EXPIRED" : "INVALID_TOKEN");
        Object rid = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (rid != null) {
            json.put("requestId", rid.toString());
            response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, rid.toString());
        }

        response.getWriter().write(objectMapper.writeValueAsString(json));
    }
}
