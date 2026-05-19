package com.legendary.invention.file.exception;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.web.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode() == null ? ErrorCode.SYSTEM_ERROR : exception.getErrorCode();
        log.warn(
                "Business error requestId={} traceId={} code={} path={}",
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                errorCode.getCode(),
                request.getRequestURI()
        );
        return buildResponse(
                ApiResponse.fail(errorCode, exception.getMessage(), exception.getUserMessage(), TraceContext.getRequestId(), request.getRequestURI()),
                errorCode.getHttpStatus()
        );
    }

    @ExceptionHandler({
            BindException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = extractValidationMessage(exception);
        log.warn(
                "Bad request requestId={} traceId={} path={} message={}",
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                request.getRequestURI(),
                message
        );
        return buildResponse(
                ApiResponse.fail(ErrorCode.BAD_REQUEST, message, ErrorCode.BAD_REQUEST.getDefaultUserMessage(), TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.BAD_REQUEST.getHttpStatus()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        log.warn("Upload size exceeded requestId={} traceId={} path={}", TraceContext.getRequestId(), TraceContext.getTraceId(), request.getRequestURI());
        return buildResponse(
                ApiResponse.fail(ErrorCode.BAD_REQUEST, "上传文件超过服务允许的最大大小", ErrorCode.BAD_REQUEST.getDefaultUserMessage(), TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.BAD_REQUEST.getHttpStatus()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        log.warn("Forbidden requestId={} traceId={} path={}", TraceContext.getRequestId(), TraceContext.getTraceId(), request.getRequestURI());
        return buildResponse(
                ApiResponse.fail(ErrorCode.FORBIDDEN, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.FORBIDDEN.getHttpStatus()
        );
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationCredentialsNotFoundException exception, HttpServletRequest request) {
        log.warn("Unauthorized requestId={} traceId={} path={}", TraceContext.getRequestId(), TraceContext.getTraceId(), request.getRequestURI());
        return buildResponse(
                ApiResponse.fail(ErrorCode.UNAUTHORIZED, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.UNAUTHORIZED.getHttpStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception, HttpServletRequest request) {
        log.error("System error requestId={} traceId={} path={}", TraceContext.getRequestId(), TraceContext.getTraceId(), request.getRequestURI(), exception);
        return buildResponse(
                ApiResponse.fail(ErrorCode.SYSTEM_ERROR, TraceContext.getRequestId(), request.getRequestURI()),
                ErrorCode.SYSTEM_ERROR.getHttpStatus()
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(ApiResponse<Void> body, Integer httpStatus) {
        return ResponseEntity.status(httpStatus).body(body);
    }

    private String extractValidationMessage(Exception exception) {
        if (exception instanceof BindException bindException) {
            return extractBindingMessage(bindException.getBindingResult());
        }
        if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            return "缺少请求参数: " + missingParameterException.getParameterName();
        }
        if (exception instanceof MissingServletRequestPartException missingPartException) {
            return "缺少上传字段: " + missingPartException.getRequestPartName();
        }
        if (exception instanceof HttpMessageNotReadableException httpMessageNotReadableException) {
            Throwable mostSpecificCause = httpMessageNotReadableException.getMostSpecificCause();
            if (mostSpecificCause != null && mostSpecificCause.getMessage() != null && !mostSpecificCause.getMessage().isBlank()) {
                return mostSpecificCause.getMessage();
            }
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return ErrorCode.BAD_REQUEST.getDefaultMessage();
    }

    private String extractBindingMessage(BindingResult bindingResult) {
        if (bindingResult.getFieldError() != null && bindingResult.getFieldError().getDefaultMessage() != null) {
            return bindingResult.getFieldError().getDefaultMessage();
        }
        if (bindingResult.getGlobalError() != null && bindingResult.getGlobalError().getDefaultMessage() != null) {
            return bindingResult.getGlobalError().getDefaultMessage();
        }
        return ErrorCode.BAD_REQUEST.getDefaultMessage();
    }
}
