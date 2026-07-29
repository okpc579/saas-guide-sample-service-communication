package com.example.saasguide.communication.application.client;

import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.DownstreamResponseInvalidException;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EligibilityClient {
    private final RestClient restClient;

    public EligibilityClient(RestClient eligibilityRestClient) {
        this.restClient = eligibilityRestClient;
    }

    public EligibilityResult checkEligibility(String applicantId, ServiceRequestContext context) {
        try {
            EligibilityResult result = restClient.get()
                    .uri("/internal/eligibilities/{applicantId}", applicantId)
                    .header("X-Tenant-Id", context.tenantId())
                    .header("X-Request-Id", context.requestId())
                    .header("X-Trace-Id", context.traceId())
                    .retrieve()
                    .body(EligibilityResult.class);
            if (result == null) {
                throw new DownstreamResponseInvalidException();
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw translateResponseError(exception.getStatusCode());
        } catch (ResourceAccessException exception) {
            // RestClient exposes connect/read timeouts and other transport failures through this type.
            // They share the same public availability response; the original cause remains server-side only.
            throw new EligibilityServiceUnavailableException();
        }
    }

    private RuntimeException translateResponseError(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return new EligibilityServiceUnavailableException();
        }
        return new DownstreamResponseInvalidException();
    }
}
