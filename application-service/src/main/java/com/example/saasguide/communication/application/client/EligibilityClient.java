package com.example.saasguide.communication.application.client;

import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.RetryableEligibilityCallException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EligibilityClient {
    private final RestClient restClient;
    public EligibilityClient(RestClient eligibilityRestClient) { this.restClient = eligibilityRestClient; }
    public EligibilityResult checkEligibility(String applicantId, ServiceRequestContext context) {
        try {
            EligibilityResult result = restClient.get().uri("/internal/eligibilities/{applicantId}", applicantId)
                    .header("X-Tenant-Id", context.tenantId()).header("X-Request-Id", context.requestId())
                    .header("X-Trace-Id", context.traceId()).retrieve().body(EligibilityResult.class);
            if (result == null) throw new RetryableEligibilityCallException(new IllegalStateException("empty response"));
            return result;
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 502 || status.value() == 503 || status.value() == 504) throw new RetryableEligibilityCallException(ex);
            throw ex;
        } catch (ResourceAccessException ex) {
            if (hasCause(ex, SocketTimeoutException.class) || hasCause(ex, ConnectException.class)) throw new RetryableEligibilityCallException(ex);
            throw new RetryableEligibilityCallException(ex);
        }
    }
    private boolean hasCause(Throwable value, Class<? extends Throwable> type) {
        for (Throwable cause = value; cause != null; cause = cause.getCause()) if (type.isInstance(cause)) return true;
        return false;
    }
}
