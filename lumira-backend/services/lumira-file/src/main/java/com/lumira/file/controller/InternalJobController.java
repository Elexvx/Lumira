package com.lumira.file.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.common.web.internal.RelayFenceValidator;
import com.lumira.file.event.FileOutboxRelay;
import com.lumira.file.processing.FileProcessingTaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("fileInternalJobController")
@RequestMapping("/file/internal/jobs")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalJobController {

    private final FileOutboxRelay fileOutboxRelay;
    private final FileProcessingTaskService fileProcessingTaskService;
    private final String fileInternalToken;
    private final StringRedisTemplate runtimeRedis;

    public InternalJobController(
            FileOutboxRelay fileOutboxRelay,
            FileProcessingTaskService fileProcessingTaskService,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileInternalToken
    ) {
        this(fileOutboxRelay, fileProcessingTaskService, fileInternalToken, null);
    }

    @Autowired
    public InternalJobController(
            FileOutboxRelay fileOutboxRelay,
            FileProcessingTaskService fileProcessingTaskService,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileInternalToken,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.fileOutboxRelay = fileOutboxRelay;
        this.fileProcessingTaskService = fileProcessingTaskService;
        this.fileInternalToken = fileInternalToken;
        this.runtimeRedis = redisProvider == null ? null : redisProvider.getIfAvailable();
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(
            @RequestHeader(name = "X-Job-Token", required = false) String token,
            @RequestHeader(name = RelayFenceValidator.OWNER_HEADER, required = false) String relayOwner,
            @RequestHeader(name = RelayFenceValidator.GENERATION_HEADER, required = false) Long relayGeneration,
            @RequestHeader(name = RelayFenceValidator.FENCE_HEADER, required = false) String relayFence
    ) {
        ensureAuthorized(token);
        ensureRelayFence("file", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(fileOutboxRelay.dispatchPendingEvents(), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Integer> relayOutbox(String token) {
        ensureAuthorized(token);
        return ApiResponse.success(fileOutboxRelay.dispatchPendingEvents(), null);
    }

    @PostMapping("/outbox/{id}/replay")
    public ApiResponse<Boolean> replayOutbox(
            @PathVariable("id") Long id,
            @RequestHeader(name = "X-Job-Token", required = false) String token,
            @RequestHeader(name = RelayFenceValidator.OWNER_HEADER, required = false) String relayOwner,
            @RequestHeader(name = RelayFenceValidator.GENERATION_HEADER, required = false) Long relayGeneration,
            @RequestHeader(name = RelayFenceValidator.FENCE_HEADER, required = false) String relayFence
    ) {
        ensureAuthorized(token);
        requirePositiveId(id);
        ensureRelayFence("file", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(fileOutboxRelay.replay(id), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Boolean> replayOutbox(Long id, String token) {
        ensureAuthorized(token);
        requirePositiveId(id);
        return ApiResponse.success(fileOutboxRelay.replay(id), null);
    }

    @PostMapping("/processing/run")
    public ApiResponse<Integer> processFileTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        requireProcessingLimit(limit);
        return ApiResponse.success(fileProcessingTaskService.processPendingTasks(limit), null);
    }

    private void ensureAuthorized(String token) {
        String requiredToken = fileInternalToken;
        if (!InternalJobTokenValidator.isConfigured(requiredToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Internal job token is not configured");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, requiredToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }

    private void ensureRelayFence(String owner, String relayOwner, Long generation, String fenceToken) {
        RelayFenceValidator.assertCurrent(runtimeRedis, owner, relayOwner, generation, fenceToken);
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Valid outbox event id is required");
        }
    }

    private void requireProcessingLimit(int limit) {
        if (limit < 1 || limit > FileProcessingTaskService.MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid file processing task limit");
        }
    }
}
