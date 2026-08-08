package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Binds the async scheduler to the small internal relay surface of the active
 * control-plane slot. Owner implementations keep their durable outbox state
 * machines; this worker only orchestrates them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
public class ControlPlaneOwnerRelayClientConfiguration {

    @Bean
    OwnerOutboxRelayPort systemOwnerOutboxRelay(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("platform", baseUrl, token, "/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort fileOwnerOutboxRelay(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("file", baseUrl, token, "/file/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort messageOwnerOutboxRelay(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("message", baseUrl, token, "/message/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort paymentOwnerOutboxRelay(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("payment", baseUrl, token, "/payment/internal/jobs/outbox");
    }

    @Bean
    OwnerOutboxRelayPort pluginOwnerOutboxRelay(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}") String baseUrl,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String token
    ) {
        return new RemoteOwnerOutboxRelay("plugin", baseUrl, token, "/plugin/internal/jobs/outbox");
    }
}
