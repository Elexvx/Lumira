package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.api.event.RelayExecutionContext;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.util.StringUtils;

import java.util.Map;

final class RemoteOwnerOutboxRelay implements OwnerOutboxRelayPort {
    private static final TypeReference<ApiResponse<Integer>> INTEGER_RESPONSE = new TypeReference<>() {
    };
    private static final TypeReference<ApiResponse<Boolean>> BOOLEAN_RESPONSE = new TypeReference<>() {
    };

    private final String owner;
    private final String token;
    private final String outboxPath;
    private final InternalHttpClientFactory.InternalHttpClient client;

    RemoteOwnerOutboxRelay(
            String owner,
            InternalHttpClientFactory clientFactory,
            String configuredBaseUrl,
            String token,
            String outboxPath
    ) {
        this.owner = requireText(owner, "owner");
        this.token = requireText(token, owner + " internal token");
        this.outboxPath = requireText(outboxPath, "outbox path");
        this.client = clientFactory.create(configuredBaseUrl, this.token);
    }

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public int dispatchPendingEvents() {
        ApiResponse<Integer> response = client.post(
                outboxPath + "/relay",
                null,
                INTEGER_RESPONSE,
                InternalHttpClientFactory.RetryMode.NEVER
        );
        return response == null || response.getData() == null ? 0 : Math.max(0, response.getData());
    }

    @Override
    public int dispatchPendingEvents(RelayExecutionContext context) {
        ApiResponse<Integer> response = client.post(
                outboxPath + "/relay",
                null,
                INTEGER_RESPONSE,
                InternalHttpClientFactory.RetryMode.NEVER,
                relayHeaders(context)
        );
        return response == null || response.getData() == null ? 0 : Math.max(0, response.getData());
    }

    @Override
    public boolean replay(Long eventId) {
        if (eventId == null || eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        ApiResponse<Boolean> response = client.post(
                outboxPath + "/" + eventId + "/replay",
                null,
                BOOLEAN_RESPONSE,
                InternalHttpClientFactory.RetryMode.NEVER
        );
        return response != null && Boolean.TRUE.equals(response.getData());
    }

    @Override
    public boolean replay(Long eventId, RelayExecutionContext context) {
        if (eventId == null || eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        ApiResponse<Boolean> response = client.post(
                outboxPath + "/" + eventId + "/replay",
                null,
                BOOLEAN_RESPONSE,
                InternalHttpClientFactory.RetryMode.NEVER,
                relayHeaders(context)
        );
        return response != null && Boolean.TRUE.equals(response.getData());
    }

    static String requireTrustedBaseUrl(String value) {
        return InternalHttpClientFactory.requireTrustedBaseUrl(value).toString();
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private Map<String, String> relayHeaders(RelayExecutionContext context) {
        if (context == null) throw new IllegalArgumentException("relay execution context is required");
        if (!owner.equals(context.owner())) {
            throw new IllegalArgumentException("relay execution owner does not match target owner");
        }
        return Map.of(
                "X-Lumira-Relay-Owner", owner,
                "X-Lumira-Relay-Generation", Long.toString(context.generation()),
                "X-Lumira-Relay-Fence", context.fenceToken()
        );
    }
}
