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
    private final BooleanSupplier notificationConsumerRunning;
    private final BooleanSupplier iamConsumerRunning;
    private final BooleanSupplier recoveryFenceDurable;

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
                () -> true,
                () -> true,
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
            ObjectProvider<PaymentEventStreamConsumer> paymentConsumerProvider,
            ObjectProvider<PaymentNotificationConsumer> notificationConsumerProvider,
            ObjectProvider<IamAuthorizationInvalidationConsumer> iamConsumerProvider,
            RecoveryFenceRegistry recoveryFenceRegistry
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
                },
                () -> {
                    PaymentNotificationConsumer consumer = notificationConsumerProvider.getIfAvailable();
                    return consumer != null && consumer.isRunning();
                },
                () -> {
                    IamAuthorizationInvalidationConsumer consumer = iamConsumerProvider.getIfAvailable();
                    return consumer != null && consumer.isRunning();
                },
                recoveryFenceRegistry::isDurable
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
        this(
                controlPlaneBaseUrl,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                jobToken,
                redisAvailable,
                paymentConsumerRunning,
                () -> true,
                () -> true,
                () -> true
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
            BooleanSupplier paymentConsumerRunning,
            BooleanSupplier recoveryFenceDurable
    ) {
        this(
                controlPlaneBaseUrl,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                jobToken,
                redisAvailable,
                paymentConsumerRunning,
                () -> true,
                () -> true,
                recoveryFenceDurable
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
            BooleanSupplier paymentConsumerRunning,
            BooleanSupplier notificationConsumerRunning,
            BooleanSupplier iamConsumerRunning,
            BooleanSupplier recoveryFenceDurable
    ) {
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
        this.scopedTokens = List.of(fileToken, messageToken, paymentToken, pluginToken, jobToken);
        this.redisAvailable = redisAvailable;
        this.paymentConsumerRunning = paymentConsumerRunning;
        this.notificationConsumerRunning = notificationConsumerRunning;
        this.iamConsumerRunning = iamConsumerRunning;
        this.recoveryFenceDurable = recoveryFenceDurable;
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
                        "/internal/jobs/payment-events/dead-letter/{recordId}/replay",
                        "/internal/jobs/payment-notifications/dead-letter",
                        "/internal/jobs/payment-notifications/dead-letter/stats",
                        "/internal/jobs/payment-notifications/dead-letter/{recordId}/replay",
                        "/internal/jobs/iam-authz/dead-letter"
                ),
                List.of("Redis Stream payment consumer", "Redis Stream notification consumer", "Redis Stream IAM authorization consumer", "owner Outbox relay requests"),
                List.of(
                        "async.control-plane-base-url.configured",
                        "async.scoped-internal-tokens.configured",
                        "async.redis.connected",
                        "async.recovery-fence.durable",
                        "async.payment-consumer.running",
                        "async.notification-consumer.running",
                        "async.iam-consumer.running",
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
                        "redis_runtime_stream_pending",
                        "redis_runtime_stream_oldest_pending_age",
                        "lumira.payment.consumer.dead-letter.count",
                        "lumira.notification.consumer.pending.count",
                        "lumira.notification.consumer.pending.oldest.age.seconds",
                        "redis_runtime_notification_stream_pending",
                        "redis_runtime_notification_stream_oldest_pending_age",
                        "lumira.notification.consumer.dead-letter.count",
                        "lumira.iam.authz.consumer.events.consumed",
                        "lumira.iam.authz.consumer.events.failed",
                        "lumira.iam.authz.consumer.pending.count",
                        "lumira.iam.authz.consumer.pending.oldest.age.seconds",
                        "lumira.iam.authz.consumer.dead-letter.count",
                        "iam_event_invalidation_success_total",
                        "iam_event_duplicate_total",
                        "iam_event_dlq_total",
                        "iam_event_schema_reject_total"
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
                                "async.recovery-fence.durable",
                                isRecoveryFenceDurable() ? "DURABLE" : "IN_MEMORY_FALLBACK",
                                "Recovery fencing must use the durable runtime Redis in production."
                        ),
                        healthCheck(
                                "async.payment-consumer.running",
                                isPaymentConsumerRunning() ? "RUNNING" : "STOPPED",
                                "The competition payment Redis Stream consumer must be actively polling."
                        ),
                        healthCheck(
                                "async.notification-consumer.running",
                                isNotificationConsumerRunning() ? "RUNNING" : "STOPPED",
                                "The payment notification Redis Stream consumer must be actively polling."
                        ),
                        healthCheck(
                                "async.iam-consumer.running",
                                isIamConsumerRunning() ? "RUNNING" : "STOPPED",
                                "The IAM authorization Redis Stream consumer must be actively polling."
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
                        metric("redis_runtime_stream_pending", "gauge", "messages", "Current pending entries in the runtime Redis payment Stream."),
                        metric("redis_runtime_stream_oldest_pending_age", "gauge", "seconds", "Age of the oldest pending runtime Redis Stream entry."),
                        metric("lumira.payment.consumer.dead-letter.count", "gauge", "messages", "Current payment DLQ count."),
                        metric("lumira.notification.consumer.pending.count", "gauge", "messages", "Current pending notification Stream entries."),
                        metric("lumira.notification.consumer.pending.oldest.age.seconds", "gauge", "seconds", "Age of the oldest pending notification Stream entry."),
                        metric("redis_runtime_notification_stream_pending", "gauge", "messages", "Current pending entries in the runtime Redis notification group."),
                        metric("redis_runtime_notification_stream_oldest_pending_age", "gauge", "seconds", "Age of the oldest pending notification entry."),
                        metric("lumira.notification.consumer.dead-letter.count", "gauge", "messages", "Current notification DLQ count."),
                        metric("lumira.iam.authz.consumer.events.consumed", "counter", "events", "IAM authorization invalidation events applied."),
                        metric("lumira.iam.authz.consumer.events.failed", "counter", "failures", "IAM authorization consumer failures retained for retry or dead letter."),
                        metric("lumira.iam.authz.consumer.pending.count", "gauge", "messages", "Current pending IAM authorization Stream entries."),
                        metric("lumira.iam.authz.consumer.pending.oldest.age.seconds", "gauge", "seconds", "Age of the oldest pending IAM authorization entry."),
                        metric("lumira.iam.authz.consumer.dead-letter.count", "gauge", "messages", "Current IAM authorization DLQ count."),
                        metric("iam_event_invalidation_success_total", "counter", "events", "IAM authorization invalidations successfully applied."),
                        metric("iam_event_duplicate_total", "counter", "events", "IAM events skipped because an event receipt already exists."),
                        metric("iam_event_dlq_total", "counter", "events", "IAM events copied to the consumer dead-letter stream."),
                        metric("iam_event_schema_reject_total", "counter", "events", "IAM events rejected for unsupported or invalid schema.")
                )
        );
    }

    private boolean healthy() {
        return StringUtils.hasText(controlPlaneBaseUrl)
                && scopedTokensConfigured()
                && isRedisAvailable()
                && isRecoveryFenceDurable()
                && isPaymentConsumerRunning()
                && isNotificationConsumerRunning()
                && isIamConsumerRunning();
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

    private boolean isNotificationConsumerRunning() {
        return safeBoolean(notificationConsumerRunning);
    }

    private boolean isIamConsumerRunning() {
        return safeBoolean(iamConsumerRunning);
    }

    private boolean isRecoveryFenceDurable() {
        return safeBoolean(recoveryFenceDurable);
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
