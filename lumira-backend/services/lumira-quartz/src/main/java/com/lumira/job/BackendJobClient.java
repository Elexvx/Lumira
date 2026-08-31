package com.lumira.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.security.InternalServiceTokenPolicy;
import com.lumira.common.web.internal.InternalHttpClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnLumiraAsyncEnabled
public class BackendJobClient {

    private static final TypeReference<ApiResponse<Integer>> INTEGER_RESPONSE =
            new TypeReference<>() {
            };

    private static final Set<String> OUTBOX_OWNERS = Set.of("platform", "message", "file", "payment", "plugin");

    private final String backendBaseUrl;
    private final String systemServiceBaseUrl;
    private final String messageBaseUrl;
    private final String fileBaseUrl;
    private final String paymentBaseUrl;
    private final String pluginBaseUrl;
    private final InternalHttpClientFactory httpClients;
    private final JobExecutorProperties properties;
    private final JobRuntimeDrainCoordinator drainCoordinator;

    public BackendJobClient(JobExecutorProperties properties) {
        this(properties, new JobRuntimeDrainCoordinator(), new ObjectMapper());
    }

    public BackendJobClient(JobExecutorProperties properties, JobRuntimeDrainCoordinator drainCoordinator) {
        this(properties, drainCoordinator, new ObjectMapper());
    }

    BackendJobClient(JobExecutorProperties properties, JobRuntimeDrainCoordinator drainCoordinator, ObjectMapper objectMapper) {
        this.properties = properties;
        this.drainCoordinator = drainCoordinator;
        this.backendBaseUrl = requireTrustedBaseUrl(properties.getBackendBaseUrl(), "backendBaseUrl");
        this.systemServiceBaseUrl = optionalTrustedBaseUrl(
                properties.getSystemServiceBaseUrl(),
                this.backendBaseUrl,
                "systemServiceBaseUrl"
        );
        this.messageBaseUrl = requireTrustedBaseUrl(properties.getMessageServiceBaseUrl(), "messageServiceBaseUrl");
        this.fileBaseUrl = optionalTrustedBaseUrl(properties.getFileServiceBaseUrl(), this.backendBaseUrl, "fileServiceBaseUrl");
        this.paymentBaseUrl = optionalTrustedBaseUrl(properties.getPaymentServiceBaseUrl(), this.backendBaseUrl, "paymentServiceBaseUrl");
        this.pluginBaseUrl = optionalTrustedBaseUrl(properties.getPluginServiceBaseUrl(), this.backendBaseUrl, "pluginServiceBaseUrl");
        JobExecutorProperties.InternalHttp http = properties.getInternalHttp();
        this.httpClients = new InternalHttpClientFactory(
                objectMapper,
                new InternalHttpClientFactory.Settings(
                        http.getConnectTimeout(), http.getResponseTimeout(), http.getMaxResponseBytes(),
                        http.getMaxAttempts(), http.getRetryBackoff()
                ),
                new InternalHttpClientFactory.Identity(http.getReleaseId(), http.getSchemaVersion())
        );
    }

    @Autowired
    public BackendJobClient(
            JobExecutorProperties properties,
            ObjectProvider<JobRuntimeDrainCoordinator> drainCoordinatorProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this(
                properties,
                drainCoordinatorProvider.getIfAvailable(JobRuntimeDrainCoordinator::new),
                objectMapperProvider.getIfAvailable(ObjectMapper::new)
        );
    }

    public void sendMessageHeartbeat() {
        post(messageBaseUrl, "/message/internal/jobs/message/heartbeat");
    }

    public void processFileTasks() {
        post(fileBaseUrl, "/file/internal/jobs/processing/run?limit=20");
    }

    public void sendOnlineSessionHeartbeat() {
        post(backendBaseUrl, "/internal/jobs/online-session/heartbeat");
    }

    public int processExportTasks() {
        return postForInt(systemServiceBaseUrl, "/internal/jobs/user-export/run?limit=20");
    }

    public int processRegistrationExportTasks() {
        return postForInt(systemServiceBaseUrl, "/internal/jobs/registration-export/run?limit=20");
    }

    public int expireReviewAssignments() {
        return postForInt(systemServiceBaseUrl, "/internal/jobs/reviews/assignments/expire");
    }

    /** Invokes the control-plane catalog owner; this runtime never reads catalog or source tables. */
    public int rebuildEventCatalogSource(String sourceType) {
        return postForInt(systemServiceBaseUrl, eventCatalogRebuildPath(sourceType));
    }

    public int replayOutboxEvent(String owner, long eventId, long operationEpoch, String fenceToken) {
        if (eventId <= 0L) throw new IllegalArgumentException("eventId must be positive");
        return recover(owner, "specified-replay", eventId, operationEpoch, fenceToken);
    }

    public int recoverStaleOutbox(String owner, long operationEpoch, String fenceToken) {
        return recover(owner, "stale", null, operationEpoch, fenceToken);
    }

    public int recoverOutboxManually(String owner, long operationEpoch, String fenceToken) {
        return recover(owner, "manual", null, operationEpoch, fenceToken);
    }

    public int fencedTakeover(String owner, long operationEpoch, String fenceToken) {
        return recover(owner, "takeover", null, operationEpoch, fenceToken);
    }

    private int recover(String owner, String mode, Long eventId, long operationEpoch, String fenceToken) {
        String normalizedOwner = normalizeOwner(owner);
        if (operationEpoch <= 0L) throw new IllegalArgumentException("operationEpoch must be positive");
        if (!StringUtils.hasText(fenceToken) || fenceToken.trim().length() < 24) {
            throw new IllegalArgumentException("fenceToken must contain at least 24 characters");
        }
        String path = "/internal/jobs/outbox/recovery/" + mode + "/" + normalizedOwner;
        if (eventId != null) path += "?eventId=" + eventId;
        return postForInt(
                backendBaseUrl,
                path,
                Map.of(
                        "X-Lumira-Operation-Epoch", Long.toString(operationEpoch),
                        "X-Lumira-Fence-Token", fenceToken.trim()
                )
        );
    }

    static String eventCatalogRebuildPath(String sourceType) {
        String normalized = StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : "";
        if (!Set.of("ACTIVITY", "COMPETITION").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported event catalog source: " + sourceType);
        }
        return "/internal/jobs/event-catalog/rebuild/" + normalized;
    }

    private int postForInt(String baseUrl, String path) {
        return postForInt(baseUrl, path, Map.of());
    }

    private int postForInt(String baseUrl, String path, Map<String, String> headers) {
        var lease = requireLease();
        try (lease) {
            ApiResponse<Integer> response = client(baseUrl, path).post(
                    path,
                    null,
                    INTEGER_RESPONSE,
                    InternalHttpClientFactory.RetryMode.IDEMPOTENT,
                    headers
            );
            return response == null || response.getData() == null ? 0 : response.getData();
        }
    }

    private void post(String baseUrl, String path) {
        var lease = requireLease();
        try (lease) {
            client(baseUrl, path).post(
                    path,
                    null,
                    new TypeReference<ApiResponse<Object>>() { },
                    InternalHttpClientFactory.RetryMode.IDEMPOTENT
            );
        }
    }

    private InternalHttpClientFactory.InternalHttpClient client(String baseUrl, String path) {
        return httpClients.create(baseUrl, internalTokenFor(path));
    }

    private com.lumira.common.runtime.RuntimeDrainGate.Lease requireLease() {
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            throw new IllegalStateException("Job executor is quiescing and is not accepting new work");
        }
        return lease;
    }

    private String internalTokenFor(String path) {
        JobExecutorProperties.Internal internal = properties.getInternal();
        String token = InternalServiceTokenPolicy.tokenForPath(
                path,
                null,
                null,
                null,
                internal.getFileToken(),
                internal.getMessageToken(),
                internal.getPaymentToken(),
                internal.getPluginToken(),
                internal.getJobToken()
        );
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Scoped internal job token is not configured for " + path);
        }
        return token;
    }

    private String optionalTrustedBaseUrl(String value, String fallback, String propertyName) {
        return StringUtils.hasText(value) ? requireTrustedBaseUrl(value, propertyName) : fallback;
    }

    private String requireTrustedBaseUrl(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        String normalized = value.trim();
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(propertyName + " is invalid", exception);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(propertyName + " must use http or https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(propertyName + " host is required");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalStateException(propertyName + " must not include user info");
        }
        if (StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
            throw new IllegalStateException(propertyName + " must not include query or fragment");
        }
        return normalized;
    }

    private String normalizeOwner(String owner) {
        String normalized = StringUtils.hasText(owner) ? owner.trim().toLowerCase(Locale.ROOT) : "";
        if (!OUTBOX_OWNERS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported outbox owner: " + owner);
        }
        return normalized;
    }
}
