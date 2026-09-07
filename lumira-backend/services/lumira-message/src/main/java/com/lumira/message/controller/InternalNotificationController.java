package com.lumira.message.controller;

import com.lumira.api.event.EventConsumptionPort;
import com.lumira.api.notification.NotificationCommand;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.SystemEventMessageCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-side command endpoint for durable notifications produced by events. */
@RestController
@RequestMapping("/message/internal/notifications")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalNotificationController {
    private static final String CONSUMER_NAME = "message-payment-notification-v1";

    private final MessageAppService messageAppService;
    private final EventConsumptionPort consumptionPort;
    private final String messageInternalToken;

    public InternalNotificationController(
            MessageAppService messageAppService,
            EventConsumptionPort consumptionPort,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageInternalToken
    ) {
        this.messageAppService = messageAppService;
        this.consumptionPort = consumptionPort;
        this.messageInternalToken = messageInternalToken;
    }

    @PostMapping("/commands")
    public ApiResponse<Boolean> publish(
            @RequestBody NotificationCommand command,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        if (command == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "notification command is required");
        }
        boolean consumed = consumptionPort.executeOnce(
                new EventConsumptionPort.EventIdentity(
                        CONSUMER_NAME,
                        command.eventId(),
                        command.eventType(),
                        command.sourceModule(),
                        command.aggregateId()
                ),
                () -> messageAppService.createSystemEventMessage(new SystemEventMessageCommand(
                        command.targetUserId(),
                        command.targetUserUuid(),
                        command.targetUserId(),
                        command.targetUserUuid(),
                        command.title(),
                        command.content()
                ))
        );
        return ApiResponse.success(consumed, null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(messageInternalToken)
                || !InternalJobTokenValidator.isAuthorized(token, messageInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized notification command access");
        }
    }
}
