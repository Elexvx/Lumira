package com.lumira.file.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.file.event.FileOutboxRelay;
import com.lumira.file.processing.FileProcessingTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("fileInternalJobController")
@RequestMapping("/file/internal/jobs")
public class InternalJobController {

    private final FileOutboxRelay fileOutboxRelay;
    private final FileProcessingTaskService fileProcessingTaskService;
    private final String internalToken;

    public InternalJobController(
            FileOutboxRelay fileOutboxRelay,
            FileProcessingTaskService fileProcessingTaskService,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.fileOutboxRelay = fileOutboxRelay;
        this.fileProcessingTaskService = fileProcessingTaskService;
        this.internalToken = internalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        return ApiResponse.success(fileOutboxRelay.dispatchPendingEvents(), null);
    }

    @PostMapping("/outbox/{id}/replay")
    public ApiResponse<Boolean> replayOutbox(
            @PathVariable("id") Long id,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(fileOutboxRelay.replay(id), null);
    }

    @PostMapping("/processing/run")
    public ApiResponse<Integer> processFileTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(fileProcessingTaskService.processPendingTasks(limit), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(internalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部任务令牌未配置");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, internalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问内部任务接口");
        }
    }
}
