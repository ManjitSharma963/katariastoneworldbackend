package com.katariastoneworld.apis.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsFilterConfig {

    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "http://localhost:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3001"
    );

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorsFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("corsFilter");
        return registration;
    }

    public static class CorsFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String origin = httpRequest.getHeader("Origin");
            String method = httpRequest.getMethod();
            
            // Handle preflight requests
            if ("OPTIONS".equalsIgnoreCase(method)) {
                if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                    httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                    httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                    httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
                    httpResponse.setHeader("Access-Control-Allow-Headers", 
                        "Authorization, Content-Type, Accept, X-Requested-With, Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
                    httpResponse.setHeader("Access-Control-Max-Age", "3600");
                    httpResponse.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
            }
            
            // Set CORS headers for all requests
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                httpResponse.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type");
            }
            
            chain.doFilter(request, response);
        }
    }
}

