package com.legendary.invention.saas.infrastructure.job;

import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.legendary.invention.saas.modules.system.online.OnlineSessionStreamService;
import com.legendary.invention.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final PlatformEventOutboxRelay platformEventOutboxRelay;
    private final OnlineSessionStreamService onlineSessionStreamService;
    private final String internalToken;

    public InternalJobController(
            PlatformEventOutboxRelay platformEventOutboxRelay,
            OnlineSessionStreamService onlineSessionStreamService,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.platformEventOutboxRelay = platformEventOutboxRelay;
        this.onlineSessionStreamService = onlineSessionStreamService;
        this.internalToken = internalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Boolean> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        platformEventOutboxRelay.dispatchPendingEvents();
        return ApiResponse.success(Boolean.TRUE, null);
    }

    @PostMapping("/online-session/heartbeat")
    public ApiResponse<Boolean> onlineSessionHeartbeat(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        onlineSessionStreamService.heartbeat();
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
