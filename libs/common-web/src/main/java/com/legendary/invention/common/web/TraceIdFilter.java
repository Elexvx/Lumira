package com.legendary.invention.common.web;

import com.legendary.invention.common.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter {

    private final boolean writeMdc;
    private final boolean useRequestIdAsTraceFallback;

    public TraceIdFilter() {
        this(true, false);
    }

    public TraceIdFilter(boolean writeMdc, boolean useRequestIdAsTraceFallback) {
        this.writeMdc = writeMdc;
        this.useRequestIdAsTraceFallback = useRequestIdAsTraceFallback;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveHeader(request, HeaderConstants.REQUEST_ID, null);
        String traceId = resolveHeader(request, HeaderConstants.TRACE_ID, useRequestIdAsTraceFallback ? requestId : null);

        TraceContext.setRequestId(requestId);
        TraceContext.setTraceId(traceId);
        if (writeMdc) {
            MDC.put("requestId", requestId);
            MDC.put("traceId", traceId);
        }
        response.setHeader(HeaderConstants.REQUEST_ID, requestId);
        response.setHeader(HeaderConstants.TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (writeMdc) {
                MDC.remove("requestId");
                MDC.remove("traceId");
            }
            TraceContext.clear();
        }
    }

    private String resolveHeader(HttpServletRequest request, String headerName, String fallback) {
        String value = request.getHeader(headerName);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return StringUtils.hasText(fallback) ? fallback : UUID.randomUUID().toString();
    }
}
