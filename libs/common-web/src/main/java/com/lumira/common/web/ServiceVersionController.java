package com.lumira.common.web;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ServiceVersionInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceVersionController {

    private final ServiceVersionProvider serviceVersionProvider;

    public ServiceVersionController(ServiceVersionProvider serviceVersionProvider) {
        this.serviceVersionProvider = serviceVersionProvider;
    }

    @GetMapping({"/api/version", "/api/v1/version", "/api/v1/{scope}/version"})
    public ApiResponse<ServiceVersionInfo> version(HttpServletRequest request) {
        return ApiResponse.success(serviceVersionProvider.current(), TraceContext.getRequestId(), request.getRequestURI());
    }
}
