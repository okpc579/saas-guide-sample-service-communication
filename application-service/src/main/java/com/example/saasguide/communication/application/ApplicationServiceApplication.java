package com.example.saasguide.communication.application;

import com.example.saasguide.communication.application.config.EligibilityClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableConfigurationProperties(EligibilityClientProperties.class)
public class ApplicationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ApplicationServiceApplication.class, args); }
}
