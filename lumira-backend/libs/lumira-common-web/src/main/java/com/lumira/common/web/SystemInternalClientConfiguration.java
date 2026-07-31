package com.lumira.common.web;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.security.InternalServiceTokenPolicy;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false")
public class SystemInternalClientConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";

    @Bean
    @Lazy
    @ConditionalOnMissingBean(SystemInternalApi.class)
    public SystemInternalApi remoteSystemInternalApi(
            @Value("${saas.system.service-base-url:${SYSTEM_SERVICE_BASE_URL:http://localhost:8081}}") String systemServiceBaseUrl,
            @Value("${saas.internal.system-token:${SAAS_INTERNAL_SYSTEM_TOKEN:}}") String systemToken,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authToken,
            @Value("${saas.internal.auth-system-token:${SAAS_INTERNAL_AUTH_SYSTEM_TOKEN:}}") String authSystemToken,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider
    ) {
        RestClient.Builder builder = restClientBuilderProvider.getIfAvailable(RestClient::builder).clone()
                .baseUrl(TrustedServiceBaseUrlValidator.requireHttpBaseUrl(systemServiceBaseUrl, "saas.system.service-base-url"))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> TrustedInternalApiErrorDecoder.handle(response)
                )
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().remove(INTERNAL_TOKEN_HEADER);
                    request.getHeaders().remove(HttpHeaders.AUTHORIZATION);
                    String token = tokenFor(request.getURI(), systemToken, authToken, authSystemToken,
                            fileToken, messageToken, paymentToken, pluginToken, jobToken);
                    if (!StringUtils.hasText(token)) {
                        throw new IllegalStateException("Scoped internal token is required for " + pathWithQuery(request.getURI()));
                    }
                    request.getHeaders().set(INTERNAL_TOKEN_HEADER, token.trim());
                    return execution.execute(request, body);
                });
        RestClient restClient = builder.build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        return proxyFactory.createClient(SystemInternalApi.class);
    }

    private static String tokenFor(
            URI uri,
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        return InternalServiceTokenPolicy.tokenForPath(
                pathWithQuery(uri),
                systemToken,
                authToken,
                authSystemToken,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                jobToken
        );
    }

    private static String pathWithQuery(URI uri) {
        String path = uri == null ? "" : uri.getRawPath();
        String query = uri == null ? null : uri.getRawQuery();
        if (StringUtils.hasText(query)) {
            return path + "?" + query;
        }
        return path;
    }
}
