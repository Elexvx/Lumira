package com.lumira.message.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageWebSocketRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("messageInternalJobController")
@RequestMapping("/message/internal/jobs")
public class InternalJobController {

    private final MessageWebSocketRegistry messageWebSocketRegistry;
    private final PlatformEventOutboxService platformEventOutboxService;
    private final MessageEventDeliveryService messageEventDeliveryService;
    private final MessageProperties messageProperties;
    private final String internalToken;

    public InternalJobController(
            MessageWebSocketRegistry messageWebSocketRegistry,
            PlatformEventOutboxService platformEventOutboxService,
            MessageEventDeliveryService messageEventDeliveryService,
            MessageProperties messageProperties,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.messageWebSocketRegistry = messageWebSocketRegistry;
        this.platformEventOutboxService = platformEventOutboxService;
        this.messageEventDeliveryService = messageEventDeliveryService;
        this.messageProperties = messageProperties;
        this.internalToken = internalToken;
    }

    @PostMapping("/message/heartbeat")
    public ApiResponse<Boolean> messageHeartbeat(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        messageWebSocketRegistry.sendHeartbeat();
        return ApiResponse.success(Boolean.TRUE, null);
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        int delivered = platformEventOutboxService.dispatchPending(messageEventDeliveryService, messageProperties.getOutboxRelayBatchSize());
        return ApiResponse.success(delivered, null);
    }

    @PostMapping("/outbox/{id}/replay")
    public ApiResponse<Boolean> replayOutbox(
            @PathVariable("id") Long id,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        boolean replayed = platformEventOutboxService.replayById(id, messageEventDeliveryService);
        return ApiResponse.success(replayed, null);
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
