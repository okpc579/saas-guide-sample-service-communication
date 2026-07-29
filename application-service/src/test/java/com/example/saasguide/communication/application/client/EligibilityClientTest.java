package com.example.saasguide.communication.application.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.DownstreamResponseInvalidException;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EligibilityClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://internal-eligibility");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final EligibilityClient client = new EligibilityClient(builder.build());
    private final ServiceRequestContext context =
            new ServiceRequestContext("tenant-a", "request-a", "trace-a");

    @Test
    void sendsContextHeadersAndReturnsEligibility() {
        server.expect(requestTo("http://internal-eligibility/internal/eligibilities/A"))
                .andExpect(header("X-Tenant-Id", "tenant-a"))
                .andExpect(header("X-Request-Id", "request-a"))
                .andExpect(header("X-Trace-Id", "trace-a"))
                .andRespond(withSuccess("{\"applicantId\":\"A\",\"eligible\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.checkEligibility("A", context).eligible()).isTrue();
        server.verify();
    }

    @Test
    void converts503ToGeneralAvailabilityError() {
        server.expect(requestTo("http://internal-eligibility/internal/eligibilities/A"))
                .andRespond(withServiceUnavailable().body("internal proxy detail"));

        assertThatThrownBy(() -> client.checkEligibility("A", context))
                .isExactlyInstanceOf(EligibilityServiceUnavailableException.class)
                .hasMessage("Eligibility unavailable");
    }

    @Test
    void convertsUnexpected4xxToInvalidDownstreamResponse() {
        server.expect(requestTo("http://internal-eligibility/internal/eligibilities/A"))
                .andRespond(withResourceNotFound().body("internal URL and exception"));

        assertThatThrownBy(() -> client.checkEligibility("A", context))
                .isExactlyInstanceOf(DownstreamResponseInvalidException.class)
                .hasMessage("Invalid downstream response");
    }
}
