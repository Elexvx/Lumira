package com.yourcompany.saas.common.exception;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        log.warn("Validation failed requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        return buildResponse(ApiResponse.fail(ErrorCode.BAD_REQUEST, TraceContext.getRequestId()), ErrorCode.BAD_REQUEST.getHttpStatus());
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        log.warn("Business error requestId={} traceId={} msg={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception.getMessage());
        ApiResponse<Void> response = ApiResponse.fail(exception.getErrorCode(), TraceContext.getRequestId(), exception.getMessage());
        response.setUserTip(exception.getUserTip());
        response.setHttpStatus(exception.getHttpStatus());
        return buildResponse(response, exception.getHttpStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Forbidden requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return buildResponse(ApiResponse.fail(ErrorCode.FORBIDDEN, TraceContext.getRequestId()), ErrorCode.FORBIDDEN.getHttpStatus());
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationCredentialsNotFoundException exception) {
        log.warn("Unauthorized requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return buildResponse(ApiResponse.fail(ErrorCode.UNAUTHORIZED, TraceContext.getRequestId()), ErrorCode.UNAUTHORIZED.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception) {
        log.error("System error requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        return buildResponse(ApiResponse.fail(ErrorCode.SYSTEM_ERROR, TraceContext.getRequestId()), ErrorCode.SYSTEM_ERROR.getHttpStatus());
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(ApiResponse<Void> body, Integer httpStatus) {
        return ResponseEntity.status(httpStatus).body(body);
    }
}
