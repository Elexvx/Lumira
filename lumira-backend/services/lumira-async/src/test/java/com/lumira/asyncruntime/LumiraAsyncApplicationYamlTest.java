package com.lumira.asyncruntime;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class LumiraAsyncApplicationYamlTest {

    @Test
    void runtimeConfigurationContainsOnlyRelayConsumerRedisAndObservabilitySettings() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("spring.application.name"))
                .isEqualTo("${SPRING_APPLICATION_NAME:lumira-async}");
        assertThat(properties.getProperty("server.port")).isEqualTo("${SERVER_PORT:8080}");
        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("${REDIS_HOST:localhost}");
        assertThat(properties.getProperty("lumira.runtime.control-plane-enabled"))
                .isEqualTo("${LUMIRA_RUNTIME_CONTROL_PLANE_ENABLED:false}");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo("${MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED:true}");
        assertThat(properties).doesNotContainKeys(
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.datasource.driver-class-name",
                "mybatis-plus.mapper-locations[0]",
                "spring.flyway.enabled",
                "spring.cache.type",
                "spring.servlet.multipart.max-file-size",
                "springdoc.api-docs.enabled",
                "saas.security.jwt-secret",
                "saas.web.cors-allowed-origin-patterns",
                "saas.plugin.storage-root",
                "saas.upload.storage-root",
                "saas.auth.wechat.enabled",
                "saas.message.ws-ticket-expires-in-seconds",
                "saas.verification.issuer",
                "saas.event.outbox.dispatcher",
                "saas.internal.system-token",
                "saas.internal.team-token"
        );
    }

    @Test
    void paymentConsumerHasBoundedRecoveryAndDeadLetterDefaults() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("lumira.event.payment-consumer.enabled"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_ENABLED:true}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.stream-key"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_STREAM_KEY:lumira.events.payment.v1}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.group-name"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_GROUP_NAME:competition-payment-v1}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.pending-recovery-minimum-idle"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_PENDING_RECOVERY_MINIMUM_IDLE:30s}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.pending-recovery-interval"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_PENDING_RECOVERY_INTERVAL:30s}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.max-delivery-count"))
                .isEqualTo("${LUMIRA_PAYMENT_EVENT_CONSUMER_MAX_DELIVERY_COUNT:8}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.stream-max-length"))
                .isEqualTo("${REDIS_RUNTIME_STREAM_MAXLEN:100000}");
        assertThat(properties.getProperty("lumira.event.payment-consumer.dead-letter-max-length"))
                .isEqualTo("${REDIS_RUNTIME_DLQ_MAXLEN:50000}");
    }

    @Test
    void ownerRelayUsesTheActiveControlPlaneSlotInsteadOfOwnerServiceBaseUrls() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("lumira.async.owner-relay.control-plane-base-url"))
                .isEqualTo("${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}");
        assertThat(properties).doesNotContainKeys(
                "SYSTEM_SERVICE_BASE_URL",
                "FILE_SERVICE_BASE_URL",
                "MESSAGE_SERVICE_BASE_URL",
                "PAYMENT_SERVICE_BASE_URL",
                "PLUGIN_SERVICE_BASE_URL",
                "COMPETITION_SERVICE_BASE_URL"
        );
    }

    @Test
    void ownerRelayAndInternalHttpHaveBoundedDefaults() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("lumira.event.relay-loop.queue-capacity")).isNotBlank();
        assertThat(properties.getProperty("lumira.event.relay-loop.max-concurrency")).isNotBlank();
        assertThat(properties.getProperty("lumira.event.relay-loop.retry-budget")).isNotBlank();
        assertThat(properties.getProperty("lumira.event.relay-loop.circuit-failure-threshold")).isNotBlank();
        assertThat(properties.getProperty("lumira.internal-http.connect-timeout")).isNotBlank();
        assertThat(properties.getProperty("lumira.internal-http.response-timeout")).isNotBlank();
        assertThat(properties.getProperty("lumira.internal-http.max-response-bytes")).isNotBlank();
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
