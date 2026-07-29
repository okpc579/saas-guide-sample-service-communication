package com.example.saasguide.communication.eligibility.web;

import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore;
import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore.Behavior;
import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore.Scenario;
import com.example.saasguide.communication.eligibility.demo.ReceivedContextStore;
import com.example.saasguide.communication.eligibility.domain.EligibilityResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class EligibilityController {
    private final DemoOnlyBehaviorStore behaviors;
    private final ReceivedContextStore contexts;

    public EligibilityController(DemoOnlyBehaviorStore behaviors, ReceivedContextStore contexts) {
        this.behaviors = behaviors;
        this.contexts = contexts;
    }

    @GetMapping("/internal/eligibilities/{id}")
    public EligibilityResult check(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader("X-Trace-Id") String traceId) throws InterruptedException {
        contexts.record(id, tenantId, requestId, traceId);
        Behavior behavior = behaviors.get(id);
        if (behavior.scenario() == Scenario.DELAY) {
            Thread.sleep(behavior.delayMillis());
        }
        if (behavior.scenario() == Scenario.ERROR) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        boolean eligible = behavior.scenario() != Scenario.INELIGIBLE;
        String reason = eligible ? null : "자격 요건을 충족하지 않습니다.";
        return new EligibilityResult(id, eligible, reason);
    }
}
