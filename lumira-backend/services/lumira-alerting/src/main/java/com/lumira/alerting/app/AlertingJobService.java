package com.lumira.alerting.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.model.AlertingModels;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AlertingJobService {
    private static final Map<String, String> PROMQL = Map.ofEntries(
            Map.entry("service.up", "min(up{job=~\"lumira-server|lumira-async|lumira-job-executor\"})"),
            Map.entry("http.5xx.rate", "100 * sum(rate(http_server_requests_seconds_count{job=\"lumira-server\",status=~\"5..\"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count{job=\"lumira-server\"}[5m])), 0.001)"),
            Map.entry("http.p95", "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"lumira-server\"}[5m])) by (le))"),
            Map.entry("jvm.heap.usage", "100 * sum(jvm_memory_used_bytes{job=\"lumira-server\",area=\"heap\"}) / clamp_min(sum(jvm_memory_max_bytes{job=\"lumira-server\",area=\"heap\"}), 1)"),
            Map.entry("mysql.up", "max(mysql_up{job=\"mysql\"})"),
            Map.entry("mysql.connections", "100 * max(mysql_global_status_threads_connected{job=\"mysql\"}) / clamp_min(max(mysql_global_variables_max_connections{job=\"mysql\"}), 1)"),
            Map.entry("redis.up", "max(redis_up{job=\"redis\"})"),
            Map.entry("outbox.backlog", "max(platform_event_outbox_pending{job=\"lumira-server\"})"),
            Map.entry("alert.delivery.backlog", "max(alert_delivery_pending{job=\"lumira-server\"})"),
            Map.entry("backup.age", "time() - max(lumira_mysql_backup_last_success_timestamp_seconds{job=\"backup-metrics\"})"),
            Map.entry("host.disk.usage", "max(lumira_host_disk_usage_percent{job=\"backup-metrics\"})")
    );

    private final AlertingRepository repository;
    private final AlertingSecretCrypto crypto;
    private final AlertDeliveryGateway gateway;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String prometheusBaseUrl;
    private final String detailsBaseUrl;
    private final String workerId;
    private final AtomicLong pendingDeliveryGauge = new AtomicLong();
    private final AtomicLong deadLetterGauge = new AtomicLong();
    private final AtomicLong evaluationErrorGauge = new AtomicLong();
    private final AtomicLong workerLastSuccessEpochGauge = new AtomicLong();
    private final AtomicLong pluginEnabledGauge = new AtomicLong();
    private final Timer evaluationTimer;
    private final Timer deliveryTimer;

    public AlertingJobService(
            AlertingRepository repository,
            AlertingSecretCrypto crypto,
            AlertDeliveryGateway gateway,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${alerting.prometheus.base-url:${ALERTING_PROMETHEUS_BASE_URL:http://prometheus:9090}}") String prometheusBaseUrl,
            @Value("${alerting.ui.base-url:${ALERTING_UI_BASE_URL:}}") String detailsBaseUrl,
            @Value("${alerting.worker.id:${ALERTING_WORKER_ID:}}") String configuredWorkerId
    ) {
        this.repository = repository;
        this.crypto = crypto;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.prometheusBaseUrl = stripSlash(prometheusBaseUrl);
        this.detailsBaseUrl = stripSlash(detailsBaseUrl);
        this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? "alert-worker-" + UUID.randomUUID() : configuredWorkerId.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        Gauge.builder("alert_delivery_pending", pendingDeliveryGauge, AtomicLong::get)
                .description("Pending or retrying built-in alert deliveries").register(meterRegistry);
        Gauge.builder("alert_delivery_dead_letter", deadLetterGauge, AtomicLong::get)
                .description("Built-in alert deliveries in dead letter state").register(meterRegistry);
        Gauge.builder("alert_evaluation_errors", evaluationErrorGauge, AtomicLong::get)
                .description("Alert rule evaluation errors in the last worker run").register(meterRegistry);
        Gauge.builder("alert_worker_last_success_timestamp_seconds", workerLastSuccessEpochGauge, AtomicLong::get)
                .description("Unix timestamp of the last successful lease-owning alert worker run").register(meterRegistry);
        Gauge.builder("alert_plugin_enabled", pluginEnabledGauge, AtomicLong::get)
                .description("Whether the built-in alerting plugin is enabled").register(meterRegistry);
        this.evaluationTimer = Timer.builder("alert_evaluation_duration").register(meterRegistry);
        this.deliveryTimer = Timer.builder("alert_delivery_duration").register(meterRegistry);
    }

    public AlertingModels.JobRunResult runOnce() {
        boolean pluginEnabled = repository.pluginEnabled();
        pluginEnabledGauge.set(pluginEnabled ? 1 : 0);
        if (!pluginEnabled) {
            refreshGauges();
            return new AlertingModels.JobRunResult(false, 0, 0, 0, 0);
        }
        if (!repository.acquireLease(workerId, 45)) {
            refreshGauges();
            return new AlertingModels.JobRunResult(true, 0, 0, 0, 0);
        }

        int evaluated = 0;
        long errors = 0;
        for (AlertingModels.RuleView rule : repository.dueRules(100)) {
            if (!continueLease()) break;
            try {
                evaluationTimer.record(() -> evaluateRule(rule));
            } catch (RuntimeException exception) {
                errors++;
                repository.recordEvaluation(rule.id(), safeMessage(exception));
            }
            evaluated++;
        }
        for (AlertingRepository.RepeatCandidate candidate : repository.repeatCandidates(100)) {
            if (!continueLease()) break;
            notifyTransition(candidate.instanceId(), candidate.rule(), "REMINDER", candidate.lastValue());
        }
        evaluationErrorGauge.set(errors);

        int processed = 0;
        int sent = 0;
        int failed = 0;
        while (processed < 50 && continueLease()) {
            var jobs = repository.claimDeliveries(repository.newClaimToken(), 1);
            if (jobs.isEmpty()) break;
            AlertingRepository.DeliveryJob job = jobs.get(0);
            boolean success = deliveryTimer.record(() -> deliver(job));
            if (success) sent++; else failed++;
            processed++;
        }
        refreshGauges();
        workerLastSuccessEpochGauge.set(java.time.Instant.now().getEpochSecond());
        return new AlertingModels.JobRunResult(true, evaluated, processed, sent, failed);
    }

    private void evaluateRule(AlertingModels.RuleView rule) {
        BigDecimal value = "PROMETHEUS".equals(rule.sourceType())
                ? prometheusValue(rule.signalKey())
                : repository.businessSignalValue(rule.signalKey(), rule.windowSeconds());
        boolean breached = compare(value, rule.threshold(), rule.comparator());
        var active = repository.activeInstance(rule.id());

        if (breached) {
            if (active.isEmpty()) {
                long instanceId = repository.createPendingInstance(rule.id(), value);
                if (rule.pendingSeconds() == 0 && repository.promoteToFiring(instanceId, value)) {
                    notifyTransition(instanceId, rule, "FIRING", value);
                }
            } else if ("PENDING".equals(active.get().status())) {
                AlertingRepository.InstanceRecord instance = active.get();
                repository.updatePendingValue(instance.id(), value);
                if (instance.pendingSince() != null
                        && !instance.pendingSince().plusSeconds(rule.pendingSeconds()).isAfter(LocalDateTime.now())
                        && repository.promoteToFiring(instance.id(), value)) {
                    notifyTransition(instance.id(), rule, "FIRING", value);
                }
            } else {
                repository.recordBreached(active.get().id(), value);
            }
        } else if (active.isPresent()) {
            AlertingRepository.InstanceRecord instance = active.get();
            if ("PENDING".equals(instance.status())) {
                repository.removePending(instance.id());
            } else if (repository.recordHealthyEvaluation(instance, value)) {
                notifyTransition(instance.id(), rule, "RESOLVED", value);
            }
        }
        repository.recordEvaluation(rule.id(), null);
    }

    private void notifyTransition(long instanceId, AlertingModels.RuleView rule, String eventType, BigDecimal value) {
        if (!repository.pluginEnabled() || repository.isSilenced(rule.id())) return;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", rule.name(),
                    "severity", rule.severity(),
                    "eventType", eventType,
                    "summary", ("FIRING".equals(eventType) ? "告警已触发"
                            : "REMINDER".equals(eventType) ? "告警仍在持续" : "告警已恢复")
                            + "：" + rule.signalKey() + " 当前值 " + value + "，阈值 " + rule.comparator() + " " + rule.threshold(),
                    "detailsUrl", detailsBaseUrl.isBlank() ? "/settings/alerting?instanceId=" + instanceId
                            : detailsBaseUrl + "/settings/alerting?instanceId=" + instanceId
            ));
            repository.createEventAndDeliveries(instanceId, rule.contactGroupId(), eventType, payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create alert transition event", exception);
        }
    }

    private boolean deliver(AlertingRepository.DeliveryJob job) {
        if (!repository.pluginEnabled()) {
            repository.pauseClaimedDelivery(job.id());
            return false;
        }
        AlertingRepository.ChannelRecord channel = repository.findChannel(job.channelId()).orElse(null);
        if (channel == null || !channel.enabled()) {
            repository.failDelivery(job, false, "Alert channel is missing or disabled", null);
            return false;
        }
        String recipient = repository.resolveMappedRecipient(channel.id(), job.recipient()).orElse(null);
        if (recipient == null || recipient.isBlank()) {
            repository.failDelivery(job, false, "Enterprise directory mapping is unmatched or ambiguous", null);
            return false;
        }
        try {
            Map<String, Object> config = crypto.decrypt(channel.encryptedConfig());
            AlertDeliveryGateway.ProviderResult result = gateway.send(
                    channel, config, job.memberType(), recipient, job.eventType(), job.payloadJson());
            if (result.success()) {
                repository.completeDelivery(job.id(), result.providerMessageId(), result.responseSummary());
                return true;
            }
            repository.failDelivery(job, result.retryable(), result.error(), result.responseSummary());
            return false;
        } catch (RuntimeException exception) {
            repository.failDelivery(job, true, safeMessage(exception), null);
            return false;
        }
    }

    private boolean continueLease() {
        boolean enabled = repository.pluginEnabled();
        pluginEnabledGauge.set(enabled ? 1 : 0);
        return enabled && repository.acquireLease(workerId, 45);
    }

    private BigDecimal prometheusValue(String signalKey) {
        String query = PROMQL.get(signalKey);
        if (query == null) throw new IllegalArgumentException("Unsupported Prometheus signal: " + signalKey);
        try {
            URI uri = URI.create(prometheusBaseUrl + "/api/v1/query?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("Prometheus returned HTTP " + response.statusCode());
            JsonNode json = objectMapper.readTree(response.body());
            if (!"success".equals(json.path("status").asText())) throw new IllegalStateException("Prometheus query failed");
            JsonNode results = json.path("data").path("result");
            if (!results.isArray() || results.isEmpty()) throw new IllegalStateException("Prometheus query returned No Data");
            String raw = results.get(0).path("value").path(1).asText();
            if (raw.isBlank() || "NaN".equalsIgnoreCase(raw) || raw.contains("Inf")) {
                throw new IllegalStateException("Prometheus query returned an invalid value");
            }
            return new BigDecimal(raw);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prometheus query was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegal) throw illegal;
            throw new IllegalStateException("Prometheus query failed", exception);
        }
    }

    private static boolean compare(BigDecimal value, BigDecimal threshold, String comparator) {
        int comparison = value.compareTo(threshold);
        return switch (comparator) {
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            default -> throw new IllegalArgumentException("Unsupported comparator");
        };
    }

    private void refreshGauges() {
        AlertingModels.HealthView health = repository.health();
        pendingDeliveryGauge.set(health.pendingDeliveries());
        deadLetterGauge.set(health.deadLetters());
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String stripSlash(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
