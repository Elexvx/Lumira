package com.lumira.saas.infrastructure.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
@ConditionalOnLumiraAsyncEnabled
public class InternalJobController {

    private final PlatformEventOutboxRelay platformEventOutboxRelay;
    private final ObjectProvider<OnlineSessionStreamService> onlineSessionStreamServiceProvider;
    private final String internalToken;

    public InternalJobController(
            PlatformEventOutboxRelay platformEventOutboxRelay,
            ObjectProvider<OnlineSessionStreamService> onlineSessionStreamServiceProvider,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.platformEventOutboxRelay = platformEventOutboxRelay;
        this.onlineSessionStreamServiceProvider = onlineSessionStreamServiceProvider;
        this.internalToken = internalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        return ApiResponse.success(platformEventOutboxRelay.dispatchPendingEvents(), null);
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
        OnlineSessionStreamService onlineSessionStreamService = onlineSessionStreamServiceProvider.getIfAvailable();
        if (onlineSessionStreamService != null) {
            onlineSessionStreamService.heartbeat();
        }
        return ApiResponse.success(Boolean.TRUE, null);
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
