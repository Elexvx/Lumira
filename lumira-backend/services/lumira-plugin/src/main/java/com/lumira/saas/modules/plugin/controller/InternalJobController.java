package com.lumira.saas.modules.plugin.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.common.web.internal.RelayFenceValidator;
import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("pluginInternalJobController")
@RequestMapping("/plugin/internal/jobs")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalJobController {

    private final PluginOutboxRelay pluginOutboxRelay;
    private final String pluginInternalToken;
    private final StringRedisTemplate runtimeRedis;

    public InternalJobController(
            PluginOutboxRelay pluginOutboxRelay,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginInternalToken
    ) {
        this(pluginOutboxRelay, pluginInternalToken, null);
    }

    @Autowired
    public InternalJobController(
            PluginOutboxRelay pluginOutboxRelay,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginInternalToken,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.pluginOutboxRelay = pluginOutboxRelay;
        this.pluginInternalToken = pluginInternalToken;
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
        ensureRelayFence("plugin", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(pluginOutboxRelay.dispatchPendingEvents(), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Integer> relayOutbox(String token) {
        ensureAuthorized(token);
        return ApiResponse.success(pluginOutboxRelay.dispatchPendingEvents(), null);
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
        ensureRelayFence("plugin", relayOwner, relayGeneration, relayFence);
        return ApiResponse.success(pluginOutboxRelay.replay(id), null);
    }

    /** Source-compatible direct invocation used by narrow unit tests. */
    public ApiResponse<Boolean> replayOutbox(Long id, String token) {
        ensureAuthorized(token);
        requirePositiveId(id);
        return ApiResponse.success(pluginOutboxRelay.replay(id), null);
    }

    private void ensureAuthorized(String token) {
        String requiredToken = pluginInternalToken;
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
}
