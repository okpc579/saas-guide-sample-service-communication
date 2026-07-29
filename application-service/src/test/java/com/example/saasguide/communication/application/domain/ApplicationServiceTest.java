package com.example.saasguide.communication.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.saasguide.communication.application.client.EligibilityClient;
import com.example.saasguide.communication.application.client.EligibilityResult;
import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.EligibilityDeniedException;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import org.junit.jupiter.api.Test;

class ApplicationServiceTest {
    private final EligibilityClient client = mock(EligibilityClient.class);
    private final InMemoryApplicationRepository repository = new InMemoryApplicationRepository();
    private final ApplicationService service = new ApplicationService(client, repository);
    private final ServiceRequestContext context = new ServiceRequestContext("tenant", "request", "trace");

    @Test
    void storesOnlyAfterEligibleResult() {
        when(client.checkEligibility("A", context)).thenReturn(new EligibilityResult("A", true, null));
        assertThat(service.create("A", context).status()).isEqualTo("ACCEPTED");
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void doesNotStoreIneligibleApplication() {
        when(client.checkEligibility("A", context)).thenReturn(new EligibilityResult("A", false, "denied"));
        assertThatThrownBy(() -> service.create("A", context)).isInstanceOf(EligibilityDeniedException.class);
        assertThat(repository.size()).isZero();
    }

    @Test
    void doesNotStoreWhenDownstreamIsUnavailable() {
        when(client.checkEligibility("A", context)).thenThrow(new EligibilityServiceUnavailableException());
        assertThatThrownBy(() -> service.create("A", context))
                .isInstanceOf(EligibilityServiceUnavailableException.class);
        assertThat(repository.size()).isZero();
    }
}
