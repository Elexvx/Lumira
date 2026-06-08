package com.legendary.invention.job;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BackendJobClient {

    private final RestClient restClient;
    private final RestClient messageRestClient;
    private final RestClient paymentRestClient;
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
        this.paymentRestClient = RestClient.builder()
                .baseUrl(properties.getPaymentServiceBaseUrl() == null || properties.getPaymentServiceBaseUrl().isBlank()
                        ? properties.getBackendBaseUrl()
                        : properties.getPaymentServiceBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void relayOutbox() {
        post("/internal/jobs/outbox/relay");
    }

    public void sendMessageHeartbeat() {
        post(messageRestClient, "/internal/jobs/message/heartbeat");
    }

    public void relayMessageOutbox() {
        post(messageRestClient, "/internal/jobs/outbox/relay");
    }

    public void relayPaymentOutbox() {
        post(paymentRestClient, "/internal/jobs/outbox/relay");
    }

    public void sendOnlineSessionHeartbeat() {
        post("/internal/jobs/online-session/heartbeat");
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
