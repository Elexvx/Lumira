package com.yourcompany.saas.common.exception;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleValidationException(Exception exception) {
        log.warn("Validation failed requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, TraceContext.getRequestId());
    }

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException exception) {
        log.warn("Business error requestId={} traceId={} msg={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception.getMessage());
        return ApiResponse.fail(exception.getErrorCode(), TraceContext.getRequestId(), exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Forbidden requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return ApiResponse.fail(ErrorCode.FORBIDDEN, TraceContext.getRequestId());
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationCredentialsNotFoundException exception) {
        log.warn("Unauthorized requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED, TraceContext.getRequestId());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleSystemException(Exception exception) {
        log.error("System error requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR, TraceContext.getRequestId());
    }
}
