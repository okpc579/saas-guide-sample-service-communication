package com.example.saasguide.communication.eligibility.demo;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ReceivedContextStore {
    public record ReceivedContext(String applicantId, String tenantId, String requestId, String traceId) {}

    private final ConcurrentHashMap<String, ReceivedContext> values = new ConcurrentHashMap<>();

    public void record(String applicantId, String tenantId, String requestId, String traceId) {
        values.put(applicantId, new ReceivedContext(applicantId, tenantId, requestId, traceId));
    }

    public ReceivedContext get(String applicantId) {
        return values.get(applicantId);
    }
}
