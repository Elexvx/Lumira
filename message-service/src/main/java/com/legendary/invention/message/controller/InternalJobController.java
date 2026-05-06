package com.legendary.invention.message.controller;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.message.service.MessageWebSocketRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final MessageWebSocketRegistry messageWebSocketRegistry;
    private final String internalToken;

    public InternalJobController(
            MessageWebSocketRegistry messageWebSocketRegistry,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.messageWebSocketRegistry = messageWebSocketRegistry;
        this.internalToken = internalToken;
    }

    @PostMapping("/message/heartbeat")
    public ApiResponse<Boolean> messageHeartbeat(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        messageWebSocketRegistry.sendHeartbeat();
        return ApiResponse.success(Boolean.TRUE, null);
    }

    private void ensureAuthorized(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部任务令牌未配置");
        }
        if (token == null || !internalToken.equals(token)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问内部任务接口");
        }
    }
}
