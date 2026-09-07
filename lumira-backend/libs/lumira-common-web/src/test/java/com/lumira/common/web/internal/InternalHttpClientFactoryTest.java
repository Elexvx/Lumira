package com.lumira.common.web.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalHttpClientFactoryTest {
    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    void enforcesResponseTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(250L); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            byte[] body = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var client = factory(Duration.ofMillis(40), 1024).create(baseUrl(), "job-token");

        assertThatThrownBy(() -> client.post(
                "/slow", null, new TypeReference<java.util.Map<String, Object>>() { },
                InternalHttpClientFactory.RetryMode.NEVER
        )).isInstanceOf(InternalHttpClientFactory.InternalHttpException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void rejectsOversizedResponseAndSendsRuntimeIdentityHeaders() throws Exception {
        AtomicReference<String> releaseHeader = new AtomicReference<>();
        AtomicReference<String> schemaHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            releaseHeader.set(exchange.getRequestHeaders().getFirst(InternalHttpClientFactory.RELEASE_ID_HEADER));
            schemaHeader.set(exchange.getRequestHeaders().getFirst(InternalHttpClientFactory.SCHEMA_VERSION_HEADER));
            byte[] body = new byte[2048];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var client = factory(Duration.ofSeconds(1), 1024).create(baseUrl(), "job-token");
        assertThatThrownBy(() -> client.post(
                "/large", null, new TypeReference<java.util.Map<String, Object>>() { },
                InternalHttpClientFactory.RetryMode.NEVER
        )).isInstanceOf(InternalHttpClientFactory.InternalHttpException.class)
                .hasMessageContaining("exceeds configured maximum");
        assertThat(releaseHeader.get()).isEqualTo("release-test");
        assertThat(schemaHeader.get()).isEqualTo("7");
    }

    @Test
    void supportsTheScopedFileOwnerTokenHeader() throws Exception {
        AtomicReference<String> tokenHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            tokenHeader.set(exchange.getRequestHeaders().getFirst(InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER));
            byte[] body = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var client = factory(Duration.ofSeconds(1), 1024).create(
                baseUrl(),
                "file-token",
                InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER
        );
        client.post(
                "/file",
                null,
                new TypeReference<java.util.Map<String, Object>>() { },
                InternalHttpClientFactory.RetryMode.NEVER
        );

        assertThat(tokenHeader.get()).isEqualTo("file-token");
    }

    @Test
    void retriesAfterOwnerCommitBeforeResponseWithoutDuplicatingProjection() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger projectionWrites = new AtomicInteger();
        AtomicReference<String> tokenHeader = new AtomicReference<>();
        Set<String> receipts = java.util.concurrent.ConcurrentHashMap.newKeySet();
        ObjectMapper objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/file", exchange -> {
            try {
                tokenHeader.set(exchange.getRequestHeaders().getFirst(InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER));
                Map<?, ?> request = objectMapper.readValue(
                        exchange.getRequestBody().readAllBytes(), Map.class
                );
                String eventId = String.valueOf(request.get("eventId"));
                if (receipts.add(eventId)) {
                    projectionWrites.incrementAndGet();
                }
                if (attempts.incrementAndGet() == 1) {
                    Thread.sleep(250L);
                    return;
                }
                byte[] body = "{\"data\":false}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        var client = factory(Duration.ofMillis(40), 1024, 2).create(
                baseUrl(),
                "file-token",
                InternalHttpClientFactory.FILE_OWNER_TOKEN_HEADER
        );
        Map<String, Object> response = client.post(
                "/file",
                Map.of("eventId", "file-event-timeout"),
                new TypeReference<Map<String, Object>>() { },
                InternalHttpClientFactory.RetryMode.IDEMPOTENT
        );

        assertThat(response).containsEntry("data", false);
        assertThat(attempts).hasValue(2);
        assertThat(receipts).containsExactly("file-event-timeout");
        assertThat(projectionWrites).hasValue(1);
        assertThat(tokenHeader).hasValue("file-token");
    }

    private InternalHttpClientFactory factory(Duration responseTimeout, int maxResponseBytes) {
        return factory(responseTimeout, maxResponseBytes, 1);
    }

    private InternalHttpClientFactory factory(Duration responseTimeout, int maxResponseBytes, int maxAttempts) {
        return new InternalHttpClientFactory(
                new ObjectMapper(),
                new InternalHttpClientFactory.Settings(
                        Duration.ofMillis(100), responseTimeout, maxResponseBytes, maxAttempts, Duration.ofMillis(1)
                ),
                new InternalHttpClientFactory.Identity("release-test", 7)
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
