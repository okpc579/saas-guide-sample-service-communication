package com.example.saasguide.communication.application.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clients.eligibility")
public record EligibilityClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
