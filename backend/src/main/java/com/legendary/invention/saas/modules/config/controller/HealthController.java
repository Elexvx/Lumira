package com.legendary.invention.saas.modules.config.controller;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.common.web.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"), TraceContext.getRequestId());
    }
}
