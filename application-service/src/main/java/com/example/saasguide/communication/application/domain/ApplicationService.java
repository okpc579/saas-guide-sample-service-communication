package com.example.saasguide.communication.application.domain;

import com.example.saasguide.communication.application.client.EligibilityClient;
import com.example.saasguide.communication.application.client.EligibilityResult;
import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.EligibilityDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {
    private final EligibilityClient client;
    private final InMemoryApplicationRepository repository;

    public ApplicationService(EligibilityClient client, InMemoryApplicationRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    public ApplicationRecord create(String applicantId, ServiceRequestContext context) {
        EligibilityResult result = client.checkEligibility(applicantId, context);
        if (!result.eligible()) {
            throw new EligibilityDeniedException();
        }
        ApplicationRecord record = new ApplicationRecord(
                UUID.randomUUID().toString(), applicantId, context.tenantId(), "ACCEPTED");
        repository.save(record);
        return record;
    }
}
