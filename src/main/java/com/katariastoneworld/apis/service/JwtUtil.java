package com.katariastoneworld.apis.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtil {
    
    @Value("${jwt.secret:your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-characters-long}")
    private String secret;
    
    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long expiration;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String email, Long userId, String role, String location) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("location", location);
        return createToken(claims, email);
    }
    
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }
    
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }
    
    public String extractLocation(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("location", String.class);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public Boolean validateToken(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return (tokenEmail.equals(email) && !isTokenExpired(token));
    }
    
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validates token and returns a result indicating if token is expired or invalid
     * @return TokenValidationResult with isValid and isExpired flags
     */
    public TokenValidationResult validateTokenWithDetails(String token) {
        if (token == null || token.isEmpty()) {
            return new TokenValidationResult(false, false, "Token is null or empty");
        }
        
        try {
            Claims claims = extractAllClaims(token);
            boolean expired = isTokenExpired(token);
            return new TokenValidationResult(!expired, expired, expired ? "Token has expired" : null);
        } catch (ExpiredJwtException e) {
            return new TokenValidationResult(false, true, "Token has expired");
        } catch (Exception e) {
            return new TokenValidationResult(false, false, "Invalid token: " + e.getMessage());
        }
    }
    
    /**
     * Result class for token validation with detailed information
     */
    public static class TokenValidationResult {
        private final boolean isValid;
        private final boolean isExpired;
        private final String message;
        
        public TokenValidationResult(boolean isValid, boolean isExpired, String message) {
            this.isValid = isValid;
            this.isExpired = isExpired;
            this.message = message;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public boolean isExpired() {
            return isExpired;
        }
        
        public String getMessage() {
            return message;
        }
    }
}

