package com.katariastoneworld.apis.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katariastoneworld.apis.dto.ApiResponseDTO;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps successful and controller-produced error JSON in ApiResponseDTO.
 */
@RestControllerAdvice(basePackages = "com.katariastoneworld.apis.controller")
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public ApiResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        HttpStatusCode status = response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus() > 0
                ? HttpStatusCode.valueOf(servlet.getServletResponse().getStatus())
                : HttpStatus.OK
                : HttpStatus.OK;

        if (status.value() == HttpStatus.NO_CONTENT.value()) {
            return body;
        }

        if (body instanceof ApiResponseDTO<?>) {
            return body;
        }

        if (body instanceof byte[] || body instanceof Resource) {
            return body;
        }

        boolean success = status.is2xxSuccessful();
        String message = success ? "Request completed successfully" : "Request failed";
        String errorCode = success ? "" : "HTTP_" + status.value();
        ApiResponseDTO<Object> wrapped = new ApiResponseDTO<>(success, message, body, errorCode);

        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (Exception ex) {
                return "{\"success\":false,\"message\":\"Response serialization failed\",\"data\":null,\"errorCode\":\"SERIALIZATION_ERROR\"}";
            }
        }

        return wrapped;
    }
}
