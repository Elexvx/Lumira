package com.lumira.asyncruntime;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.util.Map;

@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.alerting.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertingWorkerLoop {
    private static final Logger log = LoggerFactory.getLogger(AlertingWorkerLoop.class);
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> RESPONSE = new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final String token;
    private final MeterRegistry meterRegistry;

    public AlertingWorkerLoop(
            @Value("${lumira.async.owner-relay.control-plane-base-url:http://api-proxy:80}") String baseUrl,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String token,
            MeterRegistry meterRegistry
    ) {
        this.client = RestClient.builder().baseUrl(RemoteOwnerOutboxRelay.requireTrustedBaseUrl(baseUrl)).build();
        this.token = token == null ? "" : token.trim();
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            initialDelayString = "${lumira.alerting.worker.initial-delay-ms:10000}",
            fixedDelayString = "${lumira.alerting.worker.fixed-delay-ms:15000}"
    )
    public void run() {
        if (token.isBlank()) {
            meterRegistry.counter("alert_worker_failures", "reason", "token_missing").increment();
            return;
        }
        try {
            ApiResponse<Map<String, Object>> response = client.post()
                    .uri("/alerting/internal/jobs/run")
                    .header("X-Job-Token", token)
                    .retrieve()
                    .body(RESPONSE);
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
