package com.katariastoneworld.apis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katariastoneworld.apis.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(3)
public class RoleAuthorizationFilter implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public RoleAuthorizationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow Swagger UI and API docs - they are static resources
        String requestPath = request.getRequestURI();
        if (requestPath != null && (requestPath.startsWith("/swagger-ui") || 
                                    requestPath.startsWith("/api-docs") || 
                                    requestPath.startsWith("/v3/api-docs"))) {
            return true;
        }
        
        // Only process method handlers
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // Check for @RequiresRole annotation on method
        RequiresRole methodAnnotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
        
        // Check for @RequiresRole annotation on class
        RequiresRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        
        RequiresRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
        
        // If no role requirement, allow access
        if (annotation == null || annotation.value().length == 0) {
            return true;
        }
        
        // Get user role from request attributes (set by JwtAuthenticationFilter)
        String userRole = (String) request.getAttribute("userRole");
        
        if (userRole == null) {
            sendForbiddenResponse(request, response, "User role not found");
            return false;
        }
        
        // Check if user role matches any of the required roles
        String[] requiredRoles = annotation.value();
        for (String requiredRole : requiredRoles) {
            if (userRole.equals(requiredRole)) {
                return true; // User has required role
            }
        }
        
        // User doesn't have required role
        sendForbiddenResponse(request, response,
                "Insufficient permissions. Required role: " + String.join(" or ", requiredRoles));
        return false;
    }
    
    private void sendForbiddenResponse(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Forbidden");
        body.put("message", message);
        body.put("code", "FORBIDDEN");
        Object rid = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (rid != null) {
            body.put("requestId", rid.toString());
            response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, rid.toString());
        }
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

