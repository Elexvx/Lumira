package com.lumira.saas.modules.task;

import com.lumira.api.task.AsyncTaskDTO;
import com.lumira.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/tasks")
public class AsyncTaskV2Controller {
    private final AsyncTaskQueryService taskQueryService;

    public AsyncTaskV2Controller(AsyncTaskQueryService taskQueryService) {
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AsyncTaskDTO> get(@PathVariable String taskId) {
        return ApiResponse.success(taskQueryService.find(taskId), null);
    }
}
