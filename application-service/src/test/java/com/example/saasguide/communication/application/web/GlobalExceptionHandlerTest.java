package com.example.saasguide.communication.application.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.saasguide.communication.application.context.RequestContextFilter;
import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.DownstreamResponseInvalidException;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {
    @Test
    void availabilityResponseDoesNotExposeInternalDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestContextFilter.ATTRIBUTE,
                new ServiceRequestContext("tenant", "request", "trace"));
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.unavailable(new EligibilityServiceUnavailableException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("ELIGIBILITY_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message())
                .doesNotContain("http://", "internal proxy detail", "Exception", "Envoy", "stackTrace");
    }

    @Test
    void invalidDownstreamResponseDoesNotExposeDownstreamBodyOrExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestContextFilter.ATTRIBUTE,
                new ServiceRequestContext("tenant", "request", "trace"));
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.invalidDownstream(new DownstreamResponseInvalidException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().code()).isEqualTo("DOWNSTREAM_RESPONSE_INVALID");
        assertThat(response.getBody().message()).doesNotContain(
                "http://", "internal downstream body", "Exception", "stack trace", "stackTrace");
    }
}
