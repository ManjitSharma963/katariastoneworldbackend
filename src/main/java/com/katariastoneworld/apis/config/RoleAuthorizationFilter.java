package com.katariastoneworld.apis.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@Order(3) // Run after JWT authentication filter
public class RoleAuthorizationFilter implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
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
            sendForbiddenResponse(response, "User role not found");
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
        sendForbiddenResponse(response, "Insufficient permissions. Required role: " + String.join(" or ", requiredRoles));
        return false;
    }
    
    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"" + message + "\"}");
    }
}

