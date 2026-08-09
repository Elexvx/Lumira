package com.lumira.asyncruntime;

import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.api.competition.CompetitionPaymentEventRequest;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Delegates the competition side effect to its control-plane owner adapter. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class RemoteCompetitionPaymentEventHandler implements CompetitionPaymentEventHandler {
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final String token;
    private final RestClient client;

    public RemoteCompetitionPaymentEventHandler(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token
    ) {
        this.token = requireText(token, "job internal token");
        this.client = RestClient.builder()
                .baseUrl(RemoteOwnerOutboxRelay.requireTrustedBaseUrl(baseUrl))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean handleOrderPaid(String eventId, String orderNo, Long registrationId, Long ownerUserId, String ownerUserUuid) {
        ApiResponse<Boolean> response = client.post()
                .uri("/internal/jobs/competition/payment-order-paid")
                .header("X-Job-Token", token)
                .body(new CompetitionPaymentEventRequest(eventId, orderNo, registrationId, ownerUserId, ownerUserUuid))
                .retrieve()
                .body(RESPONSE);
        return response != null && Boolean.TRUE.equals(response.getData());
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
