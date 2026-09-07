package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.api.competition.CompetitionPaymentEventRequest;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Delegates the competition side effect to its control-plane owner adapter. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class RemoteCompetitionPaymentEventHandler implements CompetitionPaymentEventHandler {
    private static final TypeReference<ApiResponse<Boolean>> RESPONSE = new TypeReference<>() {
    };

    private final String token;
    private final InternalHttpClientFactory.InternalHttpClient client;

    public RemoteCompetitionPaymentEventHandler(
            InternalHttpClientFactory clientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token
    ) {
        this.token = requireText(token, "job internal token");
        this.client = clientFactory.create(baseUrl, this.token);
    }

    @Override
    public boolean handleOrderPaid(String eventId, String orderNo, Long registrationId, Long ownerUserId, String ownerUserUuid) {
        ApiResponse<Boolean> response = client.post(
                "/internal/jobs/competition/payment-order-paid",
                new CompetitionPaymentEventRequest(eventId, orderNo, registrationId, ownerUserId, ownerUserUuid),
                RESPONSE,
                InternalHttpClientFactory.RetryMode.IDEMPOTENT
        );
        return response != null && Boolean.TRUE.equals(response.getData());
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
