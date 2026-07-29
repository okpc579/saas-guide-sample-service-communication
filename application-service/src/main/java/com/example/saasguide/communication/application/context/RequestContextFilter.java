package com.example.saasguide.communication.application.context;

import com.example.saasguide.communication.application.error.ContextInvalidException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestContextFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = ServiceRequestContext.class.getName();
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantId = request.getHeader("X-Tenant-Id");
        String requestId = valueOrUuid(request.getHeader("X-Request-Id"));
        String traceId = valueOrUuid(request.getHeader("X-Trace-Id"));
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            validate(tenantId, requestId, traceId);
            ServiceRequestContext context = new ServiceRequestContext(tenantId, requestId, traceId);
            request.setAttribute(ATTRIBUTE, context);
            MDC.put("tenantId", tenantId);
            MDC.put("requestId", requestId);
            MDC.put("traceId", traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }

    private void validate(String tenantId, String requestId, String traceId) {
        if (tenantId == null || !ID.matcher(tenantId).matches()
                || !ID.matcher(requestId).matches() || !ID.matcher(traceId).matches()) {
            throw new ContextInvalidException();
        }
    }

    private String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
