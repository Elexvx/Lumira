package com.yourcompany.saas.modules.audit.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("audit module ready", TraceContext.getRequestId());
    }
}
