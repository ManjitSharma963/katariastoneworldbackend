package com.katariastoneworld.apis.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private RoleAuthorizationFilter roleAuthorizationFilter;
    
    @Value("${cors.allow-all-origins:true}")
    private boolean allowAllOrigins;
    
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}")
    private String allowedOriginsString;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationFilter);
    }
    
    // CORS is now handled by SimpleCorsFilter - disabled here to avoid conflicts
    /*
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Configure CORS using Spring's built-in support
        // IMPORTANT: When allowCredentials(true) is used, you MUST use allowedOrigins() with exact origins
        // You CANNOT use allowedOriginPatterns("*") with credentials
        var corsRegistration = registry.addMapping("/**");
        
        if (allowAllOrigins) {
            // When allowing credentials, we need to specify origins explicitly
            // Use the configured origins or default to common localhost ports
            String[] origins;
            if (allowedOriginsString != null && !allowedOriginsString.trim().isEmpty()) {
                origins = allowedOriginsString.split(",\\s*");
            } else {
                origins = new String[]{
                    "http://localhost:3000",
                    "http://localhost:3001",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:3001"
                };
            }
            corsRegistration.allowedOrigins(origins);
            System.out.println("[WebConfig] CORS configured to allow origins: " + java.util.Arrays.toString(origins));
        } else {
            String[] origins = allowedOriginsString.split(",\\s*");
            corsRegistration.allowedOrigins(origins);
            System.out.println("[WebConfig] CORS configured with specific origins: " + java.util.Arrays.toString(origins));
        }
        
        corsRegistration.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin", 
                               "Access-Control-Request-Method", "Access-Control-Request-Headers")
                .allowCredentials(true)
                .maxAge(0); // Don't cache preflight
    }
    */
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Exclude /auth/** and /api/** paths from static resource handling
        // This prevents Spring from trying to serve these as static resources
        // and generating warnings when they don't exist as static files
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/", "classpath:/META-INF/resources/")
                .resourceChain(false);
    }
}

