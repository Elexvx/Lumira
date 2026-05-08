package com.legendary.invention.auth.exception;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.web.TraceContext;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        ApiResponse<Void> body = ApiResponse.fail(
                errorCode,
                exception.getMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(FeignException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.SYSTEM_ERROR;
        log.warn(
                "Downstream service error requestId={} traceId={} status={} path={}",
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                exception.status(),
                request.getRequestURI()
        );
        ApiResponse<Void> body = ApiResponse.fail(
                errorCode,
                "依赖服务暂时不可用，请稍后重试",
                errorCode.getDefaultUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        return ResponseEntity.status(503).body(body);
    }
}
