package com.lumira.job;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BackendJobClient {

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

    public void relayOutbox() {
        post("/internal/jobs/outbox/relay");
    }

    public void sendMessageHeartbeat() {
        post(messageRestClient, "/message/internal/jobs/message/heartbeat");
    }

    public void relayMessageOutbox() {
        post(messageRestClient, "/message/internal/jobs/outbox/relay");
    }

    public void relayFileOutbox() {
        post(fileRestClient, "/file/internal/jobs/outbox/relay");
    }

    public void processFileTasks() {
        post(fileRestClient, "/file/internal/jobs/processing/run?limit=20");
    }

    public void relayPaymentOutbox() {
        post(paymentRestClient, "/payment/internal/jobs/outbox/relay");
    }

    public void relayPluginOutbox() {
        post(pluginRestClient, "/plugin/internal/jobs/outbox/relay");
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

    private void post(RestClient client, String path) {
        client.post()
                .uri(path)
                .header("X-Job-Token", properties.getInternalToken())
                .retrieve()
                .toBodilessEntity();
    }
}
