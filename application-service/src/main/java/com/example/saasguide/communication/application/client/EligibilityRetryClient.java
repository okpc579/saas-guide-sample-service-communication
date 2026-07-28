package com.example.saasguide.communication.application.client;

import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import com.example.saasguide.communication.application.error.RetryableEligibilityCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

@Service
public class EligibilityRetryClient {
    private static final Logger log = LoggerFactory.getLogger(EligibilityRetryClient.class);
    private final EligibilityClient client;
    public EligibilityRetryClient(EligibilityClient client) { this.client = client; }
    @Retryable(retryFor = RetryableEligibilityCallException.class,
            maxAttemptsExpression = "${clients.eligibility.max-attempts}",
            backoff = @Backoff(delayExpression = "#{@eligibilityClientProperties.retryDelay().toMillis()}"))
    public EligibilityResult check(String applicantId, ServiceRequestContext context) {
        int attempt = RetrySynchronizationManager.getContext() == null ? 1 : RetrySynchronizationManager.getContext().getRetryCount() + 1;
        long start = System.nanoTime();
        try { return client.checkEligibility(applicantId, context); }
        finally { log.info("downstream=eligibility-service applicantId={} attempt={} elapsedMs={}", applicantId, attempt, (System.nanoTime()-start)/1_000_000); }
    }
    @Recover
    public EligibilityResult recover(RetryableEligibilityCallException exception, String applicantId, ServiceRequestContext context) {
        log.warn("downstream=eligibility-service applicantId={} result=exhausted", applicantId);
        throw new EligibilityServiceUnavailableException();
    }
}
