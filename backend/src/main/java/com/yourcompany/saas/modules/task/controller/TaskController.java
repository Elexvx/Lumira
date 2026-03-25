package com.yourcompany.saas.modules.task.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    @GetMapping("/jobs")
    public ApiResponse<List<String>> listJobs() {
        return ApiResponse.success(List.of(), TraceContext.getRequestId());
    }
}
