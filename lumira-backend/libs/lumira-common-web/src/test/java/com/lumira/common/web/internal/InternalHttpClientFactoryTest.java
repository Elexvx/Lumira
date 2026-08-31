package com.lumira.common.web.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalHttpClientFactoryTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
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

    private InternalHttpClientFactory factory(Duration responseTimeout, int maxResponseBytes) {
        return new InternalHttpClientFactory(
                new ObjectMapper(),
                new InternalHttpClientFactory.Settings(
                        Duration.ofMillis(100), responseTimeout, maxResponseBytes, 1, Duration.ofMillis(1)
                ),
                new InternalHttpClientFactory.Identity("release-test", 7)
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
