package com.example.saasguide.communication.application.web;

import com.example.saasguide.communication.application.context.RequestContextFilter;
import com.example.saasguide.communication.application.context.ServiceRequestContext;
import com.example.saasguide.communication.application.error.ContextInvalidException;
import com.example.saasguide.communication.application.error.DownstreamResponseInvalidException;
import com.example.saasguide.communication.application.error.EligibilityDeniedException;
import com.example.saasguide.communication.application.error.EligibilityServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ContextInvalidException.class)
    ResponseEntity<ApiError> context(ContextInvalidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "CONTEXT_INVALID", "요청 컨텍스트가 올바르지 않습니다.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "요청 내용이 올바르지 않습니다.", request);
    }

    @ExceptionHandler(EligibilityDeniedException.class)
    ResponseEntity<ApiError> denied(EligibilityDeniedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "ELIGIBILITY_DENIED", "자격 요건을 충족하지 않습니다.", request);
    }

    @ExceptionHandler(EligibilityServiceUnavailableException.class)
    ResponseEntity<ApiError> unavailable(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ELIGIBILITY_SERVICE_UNAVAILABLE",
                "자격 검증 서비스를 일시적으로 사용할 수 없습니다.", request);
    }

    @ExceptionHandler(DownstreamResponseInvalidException.class)
    ResponseEntity<ApiError> invalidDownstream(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_RESPONSE_INVALID",
                "자격 검증 서비스의 응답을 처리할 수 없습니다.", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                code, message, contextValue(request, true), contextValue(request, false), Instant.now()));
    }

    private String contextValue(HttpServletRequest request, boolean requestId) {
        Object value = request.getAttribute(RequestContextFilter.ATTRIBUTE);
        if (value instanceof ServiceRequestContext context) {
            return requestId ? context.requestId() : context.traceId();
        }
        return request.getHeader(requestId ? "X-Request-Id" : "X-Trace-Id");
    }

    public record ApiError(String code, String message, String requestId, String traceId, Instant timestamp) {}
}
