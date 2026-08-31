package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;

@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.alerting.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertingWorkerLoop {
    private static final Logger log = LoggerFactory.getLogger(AlertingWorkerLoop.class);
    private static final TypeReference<ApiResponse<Map<String, Object>>> RESPONSE = new TypeReference<>() { };

    private final InternalHttpClientFactory.InternalHttpClient client;
    private final String token;
    private final MeterRegistry meterRegistry;
    private final AsyncRuntimeDrainCoordinator drainCoordinator;

    public AlertingWorkerLoop(
            @Value("${lumira.async.owner-relay.control-plane-base-url:http://api-proxy:80}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token,
            MeterRegistry meterRegistry
    ) {
        this(defaultFactory(), baseUrl, token, meterRegistry, new AsyncRuntimeDrainCoordinator());
    }

    @Autowired
    public AlertingWorkerLoop(
            InternalHttpClientFactory clientFactory,
            @Value("${lumira.async.owner-relay.control-plane-base-url:http://api-proxy:80}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token,
            MeterRegistry meterRegistry,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.token = token == null ? "" : token.trim();
        this.client = this.token.isBlank() ? null : clientFactory.create(baseUrl, this.token);
        this.meterRegistry = meterRegistry;
        this.drainCoordinator = drainCoordinator;
    }

    @Scheduled(
            initialDelayString = "${lumira.alerting.worker.initial-delay-ms:10000}",
            fixedDelayString = "${lumira.alerting.worker.fixed-delay-ms:15000}"
    )
    public void run() {
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            return;
        }
        try (lease) {
        if (token.isBlank()) {
            meterRegistry.counter("alert_worker_failures", "reason", "token_missing").increment();
            return;
        }
        try {
            ApiResponse<Map<String, Object>> response = client.post(
                    "/alerting/internal/jobs/run",
                    null,
                    RESPONSE,
                    InternalHttpClientFactory.RetryMode.NEVER
            );
            Map<String, Object> data = response == null ? null : response.getData();
            if (data != null && Boolean.TRUE.equals(data.get("pluginEnabled"))) {
                meterRegistry.counter("alert_worker_runs", "result", "success").increment();
            } else {
                meterRegistry.counter("alert_worker_runs", "result", "plugin_disabled").increment();
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter("alert_worker_failures", "reason", "control_plane").increment();
            log.warn("alert worker run failed: {}", exception.getMessage());
        }
        }
    }

    private static InternalHttpClientFactory defaultFactory() {
        return new InternalHttpClientFactory(
                new ObjectMapper(),
                InternalHttpClientFactory.Settings.defaults(),
                new InternalHttpClientFactory.Identity("unknown", 1)
        );
    }
}
