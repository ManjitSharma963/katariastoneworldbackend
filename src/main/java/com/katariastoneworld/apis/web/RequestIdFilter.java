package com.katariastoneworld.apis.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns {@value #REQUEST_ID_ATTRIBUTE} for tracing; mirrors to {@code X-Request-Id} response header and MDC {@code requestId}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "com.katariastoneworld.requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String rid = (incoming != null && !incoming.isBlank()) ? incoming.trim()
                : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, rid);
        MDC.put(MDC_KEY, rid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            response.setHeader(REQUEST_ID_HEADER, rid);
            MDC.remove(MDC_KEY);
        }
    }
}
