package com.example.saasguide.communication.application.context;

public record ServiceRequestContext(String tenantId, String requestId, String traceId) {}
