package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.common.api.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URISyntaxException;

final class RemoteOwnerOutboxRelay implements OwnerOutboxRelayPort {
    private static final ParameterizedTypeReference<ApiResponse<Integer>> INTEGER_RESPONSE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOLEAN_RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final String owner;
    private final String token;
    private final String outboxPath;
    private final RestClient client;

    RemoteOwnerOutboxRelay(String owner, String configuredBaseUrl, String token, String outboxPath) {
        this.owner = requireText(owner, "owner");
        this.token = requireText(token, owner + " internal token");
        this.outboxPath = requireText(outboxPath, "outbox path");
        this.client = RestClient.builder()
                .baseUrl(requireTrustedBaseUrl(configuredBaseUrl))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public int dispatchPendingEvents() {
        ApiResponse<Integer> response = client.post()
                .uri(outboxPath + "/relay")
                .header("X-Job-Token", token)
                .retrieve()
                .body(INTEGER_RESPONSE);
        return response == null || response.getData() == null ? 0 : Math.max(0, response.getData());
    }

    @Override
    public boolean replay(Long eventId) {
        if (eventId == null || eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        ApiResponse<Boolean> response = client.post()
                .uri(outboxPath + "/" + eventId + "/replay")
                .header("X-Job-Token", token)
                .retrieve()
                .body(BOOLEAN_RESPONSE);
        return response != null && Boolean.TRUE.equals(response.getData());
    }

    static String requireTrustedBaseUrl(String value) {
        String normalized = requireText(value, "control-plane base URL");
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost())
                    || (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))
                    || StringUtils.hasText(uri.getUserInfo())
                    || StringUtils.hasText(uri.getQuery())
                    || StringUtils.hasText(uri.getFragment())) {
                throw new IllegalArgumentException("control-plane base URL must be an absolute http(s) origin");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("control-plane base URL is invalid", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
