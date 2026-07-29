package com.example.saasguide.communication.eligibility.demo;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DemoOnlyBehaviorStore {
    public enum Scenario {
        ELIGIBLE,
        INELIGIBLE,
        DELAY,
        ERROR
    }

    public record Behavior(Scenario scenario, long delayMillis) {}

    private final ConcurrentHashMap<String, Behavior> behaviors = new ConcurrentHashMap<>();

    public Behavior get(String applicantId) {
        return behaviors.getOrDefault(applicantId, new Behavior(Scenario.ELIGIBLE, 0));
    }

    public void put(String applicantId, Scenario scenario, long delayMillis) {
        behaviors.put(applicantId, new Behavior(scenario, delayMillis));
    }

    public void delete(String applicantId) {
        behaviors.remove(applicantId);
    }
}
