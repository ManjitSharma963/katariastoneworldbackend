package com.katariastoneworld.apis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow specific origins - list both explicitly
        // When allowCredentials is true, we must use setAllowedOrigins (not patterns)
        // Order doesn't matter - Spring will match the request origin
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:3001"
        ));
        
        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow all methods including OPTIONS for preflight
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", 
            "OPTIONS", "PATCH", "HEAD"
        ));
        
        // Allow credentials - when true, origin matching is strict
        // Both origins are explicitly listed above, so this should work
        config.setAllowCredentials(true);
        
        // Allow exposed headers
        config.setExposedHeaders(Arrays.asList("*"));
        
        // Max age for preflight requests (1 hour)
        config.setMaxAge(3600L);
        
        // Apply CORS configuration to all paths
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

