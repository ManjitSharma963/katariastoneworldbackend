package com.katariastoneworld.apis.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private RoleAuthorizationFilter roleAuthorizationFilter;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationFilter);
    }
    
    // CORS is handled by CustomCorsFilter and CorsConfig filter
    // Do not add CORS mapping here to avoid conflicts
    
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

