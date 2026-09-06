package com.lumira.saas.infrastructure.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.common.web.internal.RelayFenceValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalJobController {

    private final PlatformEventOutboxRelay platformEventOutboxRelay;
    private final ObjectProvider<OnlineSessionStreamService> onlineSessionStreamServiceProvider;
    private final String jobInternalToken;
    private final StringRedisTemplate runtimeRedis;

    public InternalJobController(
            PlatformEventOutboxRelay platformEventOutboxRelay,
            ObjectProvider<OnlineSessionStreamService> onlineSessionStreamServiceProvider,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobInternalToken
    ) {
        this(platformEventOutboxRelay, onlineSessionStreamServiceProvider, jobInternalToken, null);
    }

    @Autowired
    public InternalJobController(
            PlatformEventOutboxRelay platformEventOutboxRelay,
            ObjectProvider<OnlineSessionStreamService> onlineSessionStreamServiceProvider,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobInternalToken,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.platformEventOutboxRelay = platformEventOutboxRelay;
        this.onlineSessionStreamServiceProvider = onlineSessionStreamServiceProvider;
        this.jobInternalToken = jobInternalToken;
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
        ensureRelayFence("platform", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(platformEventOutboxRelay.dispatchPendingEvents(), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Integer> relayOutbox(String token) {
        ensureAuthorized(token);
        return ApiResponse.success(platformEventOutboxRelay.dispatchPendingEvents(), null);
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
        ensureRelayFence("platform", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(platformEventOutboxRelay.replay(id), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Boolean> replayOutbox(Long id, String token) {
        ensureAuthorized(token);
        requirePositiveId(id);
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
        if (!InternalJobTokenValidator.isConfigured(jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Internal job token is not configured");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, jobInternalToken)) {
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

}
