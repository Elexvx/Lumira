package com.lumira.payment.config;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentAuthClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @ConditionalOnMissingBean(AuthInternalApi.class)
    public AuthInternalApi authInternalApi(
            @Value("${saas.payment.auth-service-base-url:${AUTH_SERVICE_BASE_URL:http://localhost:8082}}") String authServiceBaseUrl,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder)
                .baseUrl(authServiceBaseUrl);
        if (StringUtils.hasText(internalToken)) {
            builder.defaultHeader(INTERNAL_TOKEN_HEADER, internalToken);
        }
        RestClient restClient = builder.build();
        return sessionId -> restClient.get()
                .uri("/internal/auth/sessions/{sessionId}/current-user", sessionId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(CurrentUserDTO.class);
    }
}
