package com.example.saasguide.communication.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore;
import com.example.saasguide.communication.eligibility.demo.DemoOnlyBehaviorStore.Scenario;
import com.example.saasguide.communication.eligibility.demo.ReceivedContextStore;
import com.example.saasguide.communication.eligibility.web.EligibilityController;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EligibilityControllerTest {
    @Test
    void supportsFourMinimalScenariosAndRecordsLastContext() throws Exception {
        var behaviors = new DemoOnlyBehaviorStore();
        var contexts = new ReceivedContextStore();
        var controller = new EligibilityController(behaviors, contexts);

        assertThat(controller.check("A", "tenant-a", "request-a", "trace-a").eligible()).isTrue();
        assertThat(contexts.get("A").tenantId()).isEqualTo("tenant-a");

        behaviors.put("B", Scenario.INELIGIBLE, 0);
        assertThat(controller.check("B", "tenant-b", "request-b", "trace-b").eligible()).isFalse();

        behaviors.put("C", Scenario.ERROR, 0);
        assertThatThrownBy(() -> controller.check("C", "tenant-c", "request-c", "trace-c"))
                .isInstanceOf(ResponseStatusException.class);

        behaviors.put("D", Scenario.DELAY, 10);
        assertThat(controller.check("D", "tenant-d", "request-d", "trace-d").eligible()).isTrue();
    }
}
