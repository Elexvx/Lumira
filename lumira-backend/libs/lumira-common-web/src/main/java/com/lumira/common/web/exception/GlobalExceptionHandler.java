package com.lumira.common.web.exception;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception, HttpServletRequest request) {
        log.warn("Validation failed requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        String validationMessage = extractValidationMessage(exception);
        ApiResponse<Void> response = ApiResponse.fail(
                ErrorCode.VALIDATION_ERROR,
                validationMessage,
                validationMessage,
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        return buildResponse(response, ErrorCode.VALIDATION_ERROR.getHttpStatus());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        log.warn("Upload size exceeded requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        String message = "文件过大，请上传符合大小限制的文件";
        ApiResponse<Void> response = ApiResponse.fail(
                ErrorCode.BAD_REQUEST,
                message,
                message,
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        return buildResponse(response, ErrorCode.BAD_REQUEST.getHttpStatus());
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception, HttpServletRequest request) {
        log.warn(
                "Business error requestId={} traceId={} method={} path={} code={}",
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getErrorCode().getCode()
        );
        ApiResponse<Void> response = ApiResponse.fail(
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        return buildResponse(response, exception.getErrorCode().getHttpStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        log.warn("Forbidden requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return buildResponse(
                ApiResponse.fail(ErrorCode.FORBIDDEN, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.FORBIDDEN.getHttpStatus()
        );
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationCredentialsNotFoundException exception,
            HttpServletRequest request
    ) {
        log.warn("Unauthorized requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId());
        return buildResponse(
                ApiResponse.fail(ErrorCode.UNAUTHORIZED, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.UNAUTHORIZED.getHttpStatus()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        log.warn(
                "Resource not found requestId={} traceId={} path={}",
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                request.getRequestURI()
        );
        return buildResponse(
                ApiResponse.fail(ErrorCode.NOT_FOUND, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.NOT_FOUND.getHttpStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception, HttpServletRequest request) {
        log.error("System error requestId={} traceId={}", TraceContext.getRequestId(), TraceContext.getTraceId(), exception);
        return buildResponse(
                ApiResponse.fail(ErrorCode.SYSTEM_ERROR, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.SYSTEM_ERROR.getHttpStatus()
        );
    }


    private ResponseEntity<ApiResponse<Void>> buildResponse(ApiResponse<Void> body, Integer httpStatus) {
        return ResponseEntity.status(httpStatus).body(body);
    }

    private String extractValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return extractBindingMessage(methodArgumentNotValidException.getBindingResult());
        }
        if (exception instanceof BindException bindException) {
            return extractBindingMessage(bindException.getBindingResult());
        }
        if (exception instanceof HttpMessageNotReadableException httpMessageNotReadableException) {
            Throwable mostSpecificCause = httpMessageNotReadableException.getMostSpecificCause();
            if (mostSpecificCause != null && mostSpecificCause.getMessage() != null && !mostSpecificCause.getMessage().isBlank()) {
                return mostSpecificCause.getMessage();
            }
        }
        return ErrorCode.VALIDATION_ERROR.getDefaultMessage();
    }

    private String extractBindingMessage(BindingResult bindingResult) {
        if (bindingResult.getFieldError() != null && bindingResult.getFieldError().getDefaultMessage() != null) {
            return bindingResult.getFieldError().getDefaultMessage();
        }
        if (bindingResult.getGlobalError() != null && bindingResult.getGlobalError().getDefaultMessage() != null) {
            return bindingResult.getGlobalError().getDefaultMessage();
        }
        return ErrorCode.VALIDATION_ERROR.getDefaultMessage();
    }
}
