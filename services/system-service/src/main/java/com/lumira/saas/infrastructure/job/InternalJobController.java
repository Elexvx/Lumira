package com.lumira.saas.infrastructure.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final PlatformEventOutboxRelay platformEventOutboxRelay;
    private final OnlineSessionStreamService onlineSessionStreamService;
    private final AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private final String internalToken;

    public InternalJobController(
            PlatformEventOutboxRelay platformEventOutboxRelay,
            OnlineSessionStreamService onlineSessionStreamService,
            AiKnowledgeBaseAppService aiKnowledgeBaseAppService,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.platformEventOutboxRelay = platformEventOutboxRelay;
        this.onlineSessionStreamService = onlineSessionStreamService;
        this.aiKnowledgeBaseAppService = aiKnowledgeBaseAppService;
        this.internalToken = internalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Boolean> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        platformEventOutboxRelay.dispatchPendingEvents();
        return ApiResponse.success(Boolean.TRUE, null);
    }

    @PostMapping("/outbox/{id}/replay")
    public ApiResponse<Boolean> replayOutbox(
            @PathVariable("id") Long id,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(platformEventOutboxRelay.replay(id), null);
    }

    @PostMapping("/online-session/heartbeat")
    public ApiResponse<Boolean> onlineSessionHeartbeat(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        onlineSessionStreamService.heartbeat();
        return ApiResponse.success(Boolean.TRUE, null);
    }

    @PostMapping("/ai/knowledge-index")
    public ApiResponse<Integer> aiKnowledgeIndex(
            @RequestHeader(name = "X-Job-Token", required = false) String token,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(aiKnowledgeBaseAppService.processPendingIndexTasks(limit), null);
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
