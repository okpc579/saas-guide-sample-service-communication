package com.example.saasguide.communication.application.client;
public record EligibilityResult(String applicantId, boolean eligible, String reason) { }
