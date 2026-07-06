package com.lumira.common.web;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false")
public class AuthInternalClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @Lazy
    @ConditionalOnMissingBean(AuthInternalApi.class)
    public AuthInternalApi remoteAuthInternalApi(
            @Value("${saas.auth.service-base-url:${AUTH_SERVICE_BASE_URL:http://localhost:8082}}") String authServiceBaseUrl,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authInternalToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        if (!StringUtils.hasText(authInternalToken)) {
            throw new IllegalStateException("saas.internal.auth-token is required");
        }
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder).clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(authServiceBaseUrl, "saas.auth.service-base-url"))
                .defaultHeader(INTERNAL_TOKEN_HEADER, authInternalToken.trim());
        RestClient restClient = builder.build();
        return new RemoteAuthInternalApi(restClient);
    }

    private record RemoteAuthInternalApi(RestClient restClient) implements AuthInternalApi {

        @Override
        public CurrentUserDTO currentUser(
                String sessionId,
                Long expectedUserId,
                String expectedUserUuid,
                Integer expectedSessionVersion,
                String expectedPermissionsVersion,
                Long expectedSimulatedRoleId
        ) {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/auth/sessions/{sessionId}/current-user")
                            .queryParam("expectedUserId", expectedUserId)
                            .queryParam("expectedUserUuid", expectedUserUuid)
                            .queryParam("expectedSessionVersion", expectedSessionVersion)
                            .queryParam("expectedPermissionsVersion", expectedPermissionsVersion)
                            .queryParam("expectedSimulatedRoleId", expectedSimulatedRoleId)
                            .build(sessionId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CurrentUserDTO.class);
        }
    }
}
