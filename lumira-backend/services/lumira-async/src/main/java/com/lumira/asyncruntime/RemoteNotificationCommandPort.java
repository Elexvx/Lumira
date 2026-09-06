package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumira.api.notification.NotificationCommand;
import com.lumira.api.notification.NotificationCommandPort;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Sends notification commands to the message owner through the active slot. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class RemoteNotificationCommandPort implements NotificationCommandPort {
    private static final TypeReference<ApiResponse<Boolean>> RESPONSE = new TypeReference<>() {
    };

    private final InternalHttpClientFactory.InternalHttpClient client;

    public RemoteNotificationCommandPort(
            InternalHttpClientFactory clientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}")
            String baseUrl,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String token
    ) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("message internal token is required");
        }
        this.client = clientFactory.create(baseUrl, token.trim());
    }

    @Override
    public boolean publish(NotificationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("notification command is required");
        }
        ApiResponse<Boolean> response = client.post(
                "/message/internal/notifications/commands",
                command,
                RESPONSE,
                InternalHttpClientFactory.RetryMode.IDEMPOTENT
        );
        return response != null && Boolean.TRUE.equals(response.getData());
    }
}
