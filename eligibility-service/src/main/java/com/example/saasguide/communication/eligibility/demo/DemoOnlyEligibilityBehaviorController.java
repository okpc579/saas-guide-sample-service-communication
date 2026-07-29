package com.example.saasguide.communication.eligibility.demo;

import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/testing")
public class DemoOnlyEligibilityBehaviorController {
    private final DemoOnlyBehaviorStore behaviors;
    private final ReceivedContextStore contexts;

    public DemoOnlyEligibilityBehaviorController(
            DemoOnlyBehaviorStore behaviors, ReceivedContextStore contexts) {
        this.behaviors = behaviors;
        this.contexts = contexts;
    }

    @PutMapping("/eligibility-behaviors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@PathVariable String id, @Valid @RequestBody BehaviorRequest request) {
        behaviors.put(id, request.scenario(), request.delayMillis());
    }

    @DeleteMapping("/eligibility-behaviors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        behaviors.delete(id);
    }

    @GetMapping("/received-contexts/{id}")
    public ReceivedContextStore.ReceivedContext received(@PathVariable String id) {
        return contexts.get(id);
    }

    public record BehaviorRequest(
            @NotNull Scenario scenario,
            @PositiveOrZero long delayMillis) {}
}
