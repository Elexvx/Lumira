package com.legendary.invention.saas.modules.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OpenApiSignatureFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiSignatureFilter.class);
    private static final String OPEN_API_PREFIX = "/openapi/v1/";

    private final OpenApiSignatureService signatureService;
    private final ObjectMapper objectMapper;

    public OpenApiSignatureFilter(OpenApiSignatureService signatureService, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(OPEN_API_PREFIX) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        try {
            signatureService.authenticate(wrappedRequest);
            filterChain.doFilter(wrappedRequest, response);
        } catch (RuntimeException exception) {
            logger.warn("开放接口签名验证失败: path={}, message={}", request.getRequestURI(), exception.getMessage());
            writeFailure(request, response, exception);
        }
    }

    private void writeFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException exception) throws IOException {
        ErrorCode errorCode = exception instanceof com.legendary.invention.saas.common.exception.BizException bizException
                ? bizException.getErrorCode()
                : ErrorCode.UNAUTHORIZED;
        ApiResponse<Void> body = exception instanceof com.legendary.invention.saas.common.exception.BizException bizException
                ? ApiResponse.fail(
                        bizException.getErrorCode(),
                        bizException.getErrorMessage(),
                        bizException.getUserMessage(),
                        TraceContext.getRequestId(),
                        request.getRequestURI()
                )
                : ApiResponse.fail(errorCode, TraceContext.getRequestId(), request.getRequestURI());
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
