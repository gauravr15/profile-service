package com.odin.profileservice.config;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract correlationId from the request header or generate a new one
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Add the correlationId to the MDC (for logging purposes)
        MDC.put("correlationId", correlationId);

        // Add the correlationId to the response headers
        response.setHeader("X-Correlation-ID", correlationId);

        return true; // Continue with the next interceptor or the actual handler
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clear the MDC after the request is complete
        MDC.clear();
    }
}
