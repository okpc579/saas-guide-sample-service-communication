package com.example.saasguide.communication.eligibility.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class EligibilityContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            MDC.put("tenantId", request.getHeader("X-Tenant-Id"));
            MDC.put("requestId", request.getHeader("X-Request-Id"));
            MDC.put("traceId", request.getHeader("X-Trace-Id"));
            chain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }
}
