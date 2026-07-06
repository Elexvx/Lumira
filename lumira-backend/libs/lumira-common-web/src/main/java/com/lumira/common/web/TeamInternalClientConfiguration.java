package com.lumira.common.web;

import com.lumira.team.api.TeamInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class TeamInternalClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @Lazy
    @ConditionalOnMissingBean(TeamInternalApi.class)
    public TeamInternalApi teamInternalApi(
            @Value("${saas.team.service-base-url:${TEAM_SERVICE_BASE_URL:http://localhost:8087}}") String teamServiceBaseUrl,
            @Value("${saas.internal.team-token:${SAAS_INTERNAL_TEAM_TOKEN:}}") String teamToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        if (!StringUtils.hasText(teamToken)) {
            throw new IllegalStateException("saas.internal.team-token is required");
        }
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder).clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(teamServiceBaseUrl, "saas.team.service-base-url"))
                .defaultHeader(INTERNAL_TOKEN_HEADER, teamToken.trim())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json");
        RestClient restClient = builder.build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        return proxyFactory.createClient(TeamInternalApi.class);
    }
}
