package com.yourcompany.saas.modules.file.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @GetMapping("/upload-policy")
    public ApiResponse<String> uploadPolicy() {
        return ApiResponse.success("upload policy placeholder", TraceContext.getRequestId());
    }
}
