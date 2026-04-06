package com.katariastoneworld.apis.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link SimpleCorsFilter} as a servlet filter. Kept separate so the filter class is not
 * also a {@code @Configuration} (that pattern breaks CGLIB enhancement and can cause startup NPEs).
 */
@Configuration
public class CorsFilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<SimpleCorsFilter> corsFilterRegistration(SimpleCorsFilter filter) {
        FilterRegistrationBean<SimpleCorsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("simpleCorsFilter");
        return registration;
    }
}
