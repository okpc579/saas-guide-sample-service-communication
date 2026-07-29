package com.example.saasguide.communication.application.web;

import com.example.saasguide.communication.application.context.RequestContextFilter;
import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.domain.ApplicationRecord;
import com.example.saasguide.communication.application.domain.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(
            @Valid @RequestBody CreateApplicationRequest body, HttpServletRequest request) {
        return ApplicationResponse.from(service.create(body.applicantId(), context(request)));
    }

    private ServiceRequestContext context(HttpServletRequest request) {
        return (ServiceRequestContext) request.getAttribute(RequestContextFilter.ATTRIBUTE);
    }

    public record CreateApplicationRequest(@NotBlank String applicantId) {}

    public record ApplicationResponse(String applicationId, String applicantId, String status) {
        static ApplicationResponse from(ApplicationRecord record) {
            return new ApplicationResponse(
                    record.applicationId(), record.applicantId(), record.status());
        }
    }
}
