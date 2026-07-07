package com.lumira.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.security.InternalServiceTokenPolicy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URISyntaxException;

@Component
@ConditionalOnLumiraAsyncEnabled
public class BackendJobClient {

    private static final ParameterizedTypeReference<ApiResponse<Integer>> INTEGER_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final RestClient messageRestClient;
    private final RestClient fileRestClient;
    private final RestClient paymentRestClient;
    private final RestClient pluginRestClient;
    private final JobExecutorProperties properties;

    public BackendJobClient(JobExecutorProperties properties) {
        this.properties = properties;
        String backendBaseUrl = requireTrustedBaseUrl(properties.getBackendBaseUrl(), "backendBaseUrl");
        String messageBaseUrl = requireTrustedBaseUrl(properties.getMessageServiceBaseUrl(), "messageServiceBaseUrl");
        String fileBaseUrl = optionalTrustedBaseUrl(properties.getFileServiceBaseUrl(), backendBaseUrl, "fileServiceBaseUrl");
        String paymentBaseUrl = optionalTrustedBaseUrl(properties.getPaymentServiceBaseUrl(), backendBaseUrl, "paymentServiceBaseUrl");
        String pluginBaseUrl = optionalTrustedBaseUrl(properties.getPluginServiceBaseUrl(), backendBaseUrl, "pluginServiceBaseUrl");
        this.restClient = RestClient.builder()
                .baseUrl(backendBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.messageRestClient = RestClient.builder()
                .baseUrl(messageBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.fileRestClient = RestClient.builder()
                .baseUrl(fileBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.paymentRestClient = RestClient.builder()
                .baseUrl(paymentBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.pluginRestClient = RestClient.builder()
                .baseUrl(pluginBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public int relayOutbox() {
        return postForInt("/internal/jobs/outbox/relay");
    }

    public void sendMessageHeartbeat() {
        post(messageRestClient, "/message/internal/jobs/message/heartbeat");
    }

    public int relayMessageOutbox() {
        return postForInt(messageRestClient, "/message/internal/jobs/outbox/relay");
    }

    public int relayFileOutbox() {
        return postForInt(fileRestClient, "/file/internal/jobs/outbox/relay");
    }

    public void processFileTasks() {
        post(fileRestClient, "/file/internal/jobs/processing/run?limit=20");
    }

    public int relayPaymentOutbox() {
        return postForInt(paymentRestClient, "/payment/internal/jobs/outbox/relay");
    }

    public int relayPluginOutbox() {
        return postForInt(pluginRestClient, "/plugin/internal/jobs/outbox/relay");
    }

    public void sendOnlineSessionHeartbeat() {
        post("/internal/jobs/online-session/heartbeat");
    }

    public int processExportTasks() {
        return postForInt("/internal/jobs/export/run?limit=20");
    }

    private void post(String path) {
        post(restClient, path);
    }

    private int postForInt(String path) {
        return postForInt(restClient, path);
    }

    private int postForInt(RestClient client, String path) {
        ApiResponse<Integer> response = client.post()
                .uri(path)
                .header("X-Job-Token", internalTokenFor(path))
                .retrieve()
                .body(INTEGER_RESPONSE);
        return response == null || response.getData() == null ? 0 : response.getData();
    }

    private void post(RestClient client, String path) {
        client.post()
                .uri(path)
                .header("X-Job-Token", internalTokenFor(path))
                .retrieve()
                .toBodilessEntity();
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
}
