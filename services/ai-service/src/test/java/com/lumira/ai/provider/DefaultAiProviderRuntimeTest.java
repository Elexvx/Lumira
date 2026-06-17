package com.lumira.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.lumira.ai.config.AiProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAiProviderRuntimeTest {

    @Test
    void localProviderReturnsDeterministicChatAndEmbedding() {
        DefaultAiProviderRuntime runtime = new DefaultAiProviderRuntime(
                new AiProviderProperties(),
                RestClient.builder(),
                new ObjectMapper()
        );

        var completion = runtime.complete(new AiProviderRuntime.ChatPrompt("hello", List.of()));
        var embedding = runtime.embed("hello");
        var status = runtime.status();

        assertThat(completion.provider()).isEqualTo("lumira-local");
        assertThat(completion.model()).isEqualTo("knowledge-grounded-v1");
        assertThat(completion.remote()).isFalse();
        assertThat(embedding.model()).isEqualTo("local-hashing-v1");
        assertThat(embedding.dimension()).isEqualTo(16);
        assertThat(status.remoteConfigured()).isFalse();
        assertThat(status.degraded()).isTrue();
    }

    @Test
    void openAiCompatibleProviderParsesChatAndEmbeddingResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> json(exchange, calls, authorization, """
                {"choices":[{"message":{"content":"remote answer"}}]}
                """));
        server.createContext("/v1/embeddings", exchange -> json(exchange, calls, authorization, """
                {"data":[{"embedding":[0.1,0.2,0.3]}]}
                """));
        server.start();
        AiProviderProperties properties = new AiProviderProperties();
        properties.getOpenaiCompatible().setEnabled(true);
        properties.getOpenaiCompatible().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getOpenaiCompatible().setApiKey("test-key");
        properties.getOpenaiCompatible().setChatModel("chat-model");
        properties.getOpenaiCompatible().setEmbeddingModel("embedding-model");
        try {
            DefaultAiProviderRuntime runtime = new DefaultAiProviderRuntime(properties, RestClient.builder(), new ObjectMapper());

            var completion = runtime.complete(new AiProviderRuntime.ChatPrompt("hello", List.of()));
            var embedding = runtime.embed("hello");

            assertThat(calls).hasValue(2);
            assertThat(authorization).hasValue("Bearer test-key");
            assertThat(completion.text()).isEqualTo("remote answer");
            assertThat(completion.provider()).isEqualTo("openai-compatible");
            assertThat(completion.remote()).isTrue();
            assertThat(embedding.model()).isEqualTo("embedding-model");
            assertThat(embedding.values()).containsExactly(0.1d, 0.2d, 0.3d);
            assertThat(embedding.remote()).isTrue();
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private void json(HttpExchange exchange, AtomicInteger calls, AtomicReference<String> authorization, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        calls.incrementAndGet();
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
