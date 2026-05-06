package com.legendary.invention.saas.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.io.IOException;

@Configuration
public class SentinelBlockExceptionHandlerConfig {

    @Bean
    public BlockExceptionHandler sentinelBlockExceptionHandler(ObjectMapper objectMapper) {
        return new BlockExceptionHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, String resource, BlockException ex) throws Exception {
                writeBlockedResponse(request, response, objectMapper);
            }
        };
    }

    private void writeBlockedResponse(HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        response.setStatus(ErrorCode.TRAFFIC_LIMITED.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.fail(
                                ErrorCode.TRAFFIC_LIMITED,
                                ErrorCode.TRAFFIC_LIMITED.getDefaultMessage(),
                                ErrorCode.TRAFFIC_LIMITED.getDefaultUserMessage(),
                                TraceContext.getRequestId(),
                                request.getRequestURI()
                        )
                )
        );
    }
}
