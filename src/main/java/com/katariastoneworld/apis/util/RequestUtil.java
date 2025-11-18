package com.katariastoneworld.apis.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtil {
    
    public static String getLocationFromRequest(HttpServletRequest request) {
        Object location = request.getAttribute("userLocation");
        if (location == null) {
            throw new RuntimeException("User location not found in request. User must be authenticated.");
        }
        return location.toString();
    }
    
    public static Long getUserIdFromRequest(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("User ID not found in request. User must be authenticated.");
        }
        return (Long) userId;
    }
    
    public static String getRoleFromRequest(HttpServletRequest request) {
        Object role = request.getAttribute("userRole");
        if (role == null) {
            throw new RuntimeException("User role not found in request. User must be authenticated.");
        }
        return role.toString();
    }
}

