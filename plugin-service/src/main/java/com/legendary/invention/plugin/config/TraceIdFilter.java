package com.legendary.invention.plugin.config;

import com.legendary.invention.common.constant.HeaderConstants;
import com.legendary.invention.common.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HeaderConstants.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        String traceId = request.getHeader(HeaderConstants.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = requestId;
        }
        TraceContext.setRequestId(requestId);
        TraceContext.setTraceId(traceId);
        response.setHeader(HeaderConstants.REQUEST_ID, requestId);
        response.setHeader(HeaderConstants.TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
