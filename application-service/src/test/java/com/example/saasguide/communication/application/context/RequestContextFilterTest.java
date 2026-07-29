package com.example.saasguide.communication.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestContextFilterTest {
    private final RequestContextFilter filter = new RequestContextFilter();

    @Test
    void generatesMissingIdsAndClearsMdcAfterRequest() throws Exception {
        HttpServletRequest request = request("tenant-a", null, null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<ServiceRequestContext> observed = new AtomicReference<>();
        doAnswer(invocation -> {
            observed.set((ServiceRequestContext) request.getAttribute(RequestContextFilter.ATTRIBUTE));
            assertThat(MDC.get("tenantId")).isEqualTo("tenant-a");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(observed.get().requestId()).isNotBlank();
        assertThat(observed.get().traceId()).isNotBlank();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void keepsTenantContextsSeparateBetweenCalls() throws Exception {
        assertThat(runAndObserve("tenant-a").tenantId()).isEqualTo("tenant-a");
        assertThat(runAndObserve("tenant-b").tenantId()).isEqualTo("tenant-b");
        assertThat(MDC.get("tenantId")).isNull();
    }

    private ServiceRequestContext runAndObserve(String tenantId) throws Exception {
        HttpServletRequest request = request(tenantId, "request-" + tenantId, "trace-" + tenantId);
        AtomicReference<ServiceRequestContext> observed = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                observed.set((ServiceRequestContext) request.getAttribute(RequestContextFilter.ATTRIBUTE));
        filter.doFilter(request, mock(HttpServletResponse.class), chain);
        return observed.get();
    }

    private HttpServletRequest request(String tenantId, String requestId, String traceId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
        when(request.getHeader("X-Request-Id")).thenReturn(requestId);
        when(request.getHeader("X-Trace-Id")).thenReturn(traceId);
        AtomicReference<Object> attribute = new AtomicReference<>();
        doAnswer(invocation -> {
            attribute.set(invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(org.mockito.ArgumentMatchers.eq(RequestContextFilter.ATTRIBUTE), any());
        when(request.getAttribute(RequestContextFilter.ATTRIBUTE)).thenAnswer(invocation -> attribute.get());
        return request;
    }
}
