package com.lumira.asyncruntime;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational contract for the stateless async runtime. Business owners retain
 * persistence and domain health; this endpoint reports only relay and consumer wiring.
 */
@RestController
@RequestMapping("/api/v2/async")
@ConditionalOnLumiraAsyncEnabled
public class AsyncRuntimeReadinessV2Controller {

    private final String controlPlaneBaseUrl;
    private final List<String> scopedTokens;
    private final BooleanSupplier redisAvailable;
    private final BooleanSupplier paymentConsumerRunning;

    public AsyncRuntimeReadinessV2Controller(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}")
            String controlPlaneBaseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this(
                controlPlaneBaseUrl,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                jobToken,
                () -> true,
                () -> true
        );
    }

    @Autowired
    public AsyncRuntimeReadinessV2Controller(
            @Value("${lumira.async.owner-relay.control-plane-base-url:${LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:http://api-proxy:80}}")
            String controlPlaneBaseUrl,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken,
            RedisConnectionFactory redisConnectionFactory,
            ObjectProvider<PaymentEventStreamConsumer> paymentConsumerProvider
    ) {
        this(
                controlPlaneBaseUrl,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                jobToken,
                () -> redisPing(redisConnectionFactory),
                () -> {
                    PaymentEventStreamConsumer consumer = paymentConsumerProvider.getIfAvailable();
                    return consumer != null && consumer.isRunning();
                }
        );
    }

    AsyncRuntimeReadinessV2Controller(
            String controlPlaneBaseUrl,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken,
            BooleanSupplier redisAvailable,
            BooleanSupplier paymentConsumerRunning
    ) {
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
        this.scopedTokens = List.of(fileToken, messageToken, paymentToken, pluginToken, jobToken);
        this.redisAvailable = redisAvailable;
        this.paymentConsumerRunning = paymentConsumerRunning;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Async",
                "lumira-async",
                healthy() ? "READY" : "DEGRADED",
                "stateless-relay-and-consumer",
                List.of("none"),
                List.of(
                        "/api/v2/async/readiness",
                        "/api/v2/async/health",
                        "/api/v2/async/metrics",
                        "/api/v1/async/version",
                        "/internal/jobs/outbox/recovery/{mode}/{owner}",
                        "/internal/jobs/payment-events/dead-letter",
                        "/internal/jobs/payment-events/dead-letter/stats",
                        "/internal/jobs/payment-events/dead-letter/{recordId}/replay"
                ),
                List.of("Redis Stream payment consumer", "owner Outbox relay requests"),
                List.of(
                        "async.control-plane-base-url.configured",
                        "async.scoped-internal-tokens.configured",
                        "async.redis.connected",
                        "async.payment-consumer.running",
                        "async.no-datasource-or-owner-beans"
                ),
                List.of(
                        "lumira.event.relay.published",
                        "lumira.event.relay.failure",
                        "lumira.payment.consumer.events.consumed",
                        "lumira.payment.consumer.events.failed",
                        "lumira.payment.consumer.stream.length",
                        "lumira.payment.consumer.pending.count",
                        "lumira.payment.consumer.pending.oldest.age.seconds",
                        "lumira.payment.consumer.dead-letter.count"
                ),
                List.of("Redis", "active control-plane slot through api-proxy", "owner-scoped internal tokens"),
                List.of(
                        "disable the relay loop before rolling back an owner endpoint",
                        "replay owner Outbox events through the owner API after a transport recovery",
                        "rotate only the affected scoped internal token if an owner relay is unauthorized"
                ),
                List.of("the async runtime has no datasource, MyBatis mapper, owner table, or owner bean graph")
        ), null);
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(observability(healthy() ? "UP" : "DEGRADED"), null);
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(observability("METRICS_DECLARED"), null);
    }

    private OwnerObservabilityDTO observability(String status) {
        return new OwnerObservabilityDTO(
                "Async",
                "lumira-async",
                status,
                OffsetDateTime.now(),
                List.of(
                        healthCheck(
                                "async.control-plane-base-url.configured",
                                StringUtils.hasText(controlPlaneBaseUrl) ? "CONFIGURED" : "MISSING",
                                "Owner relay requests are sent only through the active control-plane proxy."
                        ),
                        healthCheck(
                                "async.scoped-internal-tokens.configured",
                                scopedTokensConfigured() ? "CONFIGURED" : "MISSING",
                                "Each owner relay and the recovery endpoint use scoped internal tokens."
                        ),
                        healthCheck(
                                "async.redis.connected",
                                isRedisAvailable() ? "CONNECTED" : "UNAVAILABLE",
                                "Redis must accept commands before event relay and consumption are ready."
                        ),
                        healthCheck(
                                "async.payment-consumer.running",
                                isPaymentConsumerRunning() ? "RUNNING" : "STOPPED",
                                "The competition payment Redis Stream consumer must be actively polling."
                        ),
                        healthCheck(
                                "async.no-datasource-or-owner-beans",
                                "CONFIGURED",
                                "The runtime is assembled from narrow remote ports and Redis only."
                        )
                ),
                List.of(
                        metric("lumira.event.relay.published", "counter", "events", "Owner relay events published by the async worker."),
                        metric("lumira.event.relay.failure", "counter", "failures", "Owner relay call failures."),
                        metric("lumira.payment.consumer.events.consumed", "counter", "events", "Competition payment events consumed from Redis Streams."),
                        metric("lumira.payment.consumer.events.failed", "counter", "failures", "Payment consumer failures retained for retry or dead-letter handling."),
                        metric("lumira.payment.consumer.stream.length", "gauge", "messages", "Current payment source Stream length."),
                        metric("lumira.payment.consumer.pending.count", "gauge", "messages", "Current payment Stream pending count."),
                        metric("lumira.payment.consumer.pending.oldest.age.seconds", "gauge", "seconds", "Age of the oldest pending payment Stream entry."),
                        metric("lumira.payment.consumer.dead-letter.count", "gauge", "messages", "Current payment DLQ count.")
                )
        );
    }

    private boolean healthy() {
        return StringUtils.hasText(controlPlaneBaseUrl)
                && scopedTokensConfigured()
                && isRedisAvailable()
                && isPaymentConsumerRunning();
    }

    private boolean scopedTokensConfigured() {
        return scopedTokens.stream().allMatch(StringUtils::hasText);
    }

    private boolean isRedisAvailable() {
        return safeBoolean(redisAvailable);
    }

    private boolean isPaymentConsumerRunning() {
        return safeBoolean(paymentConsumerRunning);
    }

    private boolean safeBoolean(BooleanSupplier supplier) {
        try {
            return supplier != null && supplier.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean redisPing(RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            return false;
        }
        try (var connection = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }
}
