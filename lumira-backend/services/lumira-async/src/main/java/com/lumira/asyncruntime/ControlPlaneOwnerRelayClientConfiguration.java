package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Binds the async scheduler to the small internal relay surface of the active
 * control-plane slot. Owner implementations keep their durable outbox state
 * machines; this worker only orchestrates them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@EnableConfigurationProperties(OwnerRelayLaneProperties.class)
public class ControlPlaneOwnerRelayClientConfiguration {

    @Bean
    InternalHttpClientFactory internalHttpClientFactory(
            ObjectProvider<ObjectMapper> objectMapper,
            @Value("${lumira.internal-http.connect-timeout:2s}") String connectTimeout,
            @Value("${lumira.internal-http.response-timeout:5s}") String responseTimeout,
            @Value("${lumira.internal-http.max-response-bytes:1048576}") int maxResponseBytes,
            @Value("${lumira.internal-http.max-attempts:2}") int maxAttempts,
            @Value("${lumira.internal-http.retry-backoff:100ms}") String retryBackoff,
            @Value("${lumira.release-id:${LUMIRA_RELEASE_ID:unknown}}") String releaseId,
            @Value("${lumira.event.schema.write-version:${LUMIRA_EVENT_SCHEMA_WRITE_VERSION:1}}") int schemaVersion
    ) {
        return new InternalHttpClientFactory(
                objectMapper.getIfAvailable(ObjectMapper::new),
                new InternalHttpClientFactory.Settings(
                        DurationStyle.detectAndParse(connectTimeout),
                        DurationStyle.detectAndParse(responseTimeout),
                        maxResponseBytes,
                        maxAttempts,
                        DurationStyle.detectAndParse(retryBackoff)
                ),
                new InternalHttpClientFactory.Identity(releaseId, schemaVersion)
        );
    }

    @Bean
    OwnerOutboxRelayPort systemOwnerOutboxRelay(
            InternalHttpClientFactory httpClientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("platform", httpClientFactory, baseUrl, token, "/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort fileOwnerOutboxRelay(
            InternalHttpClientFactory httpClientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("file", httpClientFactory, baseUrl, token, "/file/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort messageOwnerOutboxRelay(
            InternalHttpClientFactory httpClientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("message", httpClientFactory, baseUrl, token, "/message/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort paymentOwnerOutboxRelay(
            InternalHttpClientFactory httpClientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("payment", httpClientFactory, baseUrl, token, "/payment/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort pluginOwnerOutboxRelay(
            InternalHttpClientFactory httpClientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("plugin", httpClientFactory, baseUrl, token, "/plugin/internal/jobs/outbox");
    }
}
