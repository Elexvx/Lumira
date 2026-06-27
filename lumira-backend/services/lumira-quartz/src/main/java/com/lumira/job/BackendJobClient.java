package com.lumira.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBackendBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.messageRestClient = RestClient.builder()
                .baseUrl(properties.getMessageServiceBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.fileRestClient = RestClient.builder()
                .baseUrl(properties.getFileServiceBaseUrl() == null || properties.getFileServiceBaseUrl().isBlank()
                        ? properties.getBackendBaseUrl()
                        : properties.getFileServiceBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.paymentRestClient = RestClient.builder()
                .baseUrl(properties.getPaymentServiceBaseUrl() == null || properties.getPaymentServiceBaseUrl().isBlank()
                        ? properties.getBackendBaseUrl()
                        : properties.getPaymentServiceBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.pluginRestClient = RestClient.builder()
                .baseUrl(properties.getPluginServiceBaseUrl() == null || properties.getPluginServiceBaseUrl().isBlank()
                        ? properties.getBackendBaseUrl()
                        : properties.getPluginServiceBaseUrl())
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

    public void processAiKnowledgeIndex() {
        post("/internal/jobs/ai/knowledge-index?limit=20");
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
                .header("X-Job-Token", properties.getInternalToken())
                .retrieve()
                .body(INTEGER_RESPONSE);
        return response == null || response.getData() == null ? 0 : response.getData();
    }

    private void post(RestClient client, String path) {
        client.post()
                .uri(path)
                .header("X-Job-Token", properties.getInternalToken())
                .retrieve()
                .toBodilessEntity();
    }
}
