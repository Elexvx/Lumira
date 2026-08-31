package com.lumira.common.web.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.constant.HeaderConstants;
import com.lumira.common.web.TraceContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates bounded clients for runtime-to-runtime HTTP calls.
 *
 * <p>The factory deliberately uses the JDK client so connect and response
 * deadlines are explicit and response bodies can be rejected before Jackson
 * allocates an unbounded buffer. Callers must opt in to retries and should only
 * do so for idempotent operations.</p>
 */
public final class InternalHttpClientFactory {

    public static final String RELEASE_ID_HEADER = "X-Lumira-Release-Id";
    public static final String SCHEMA_VERSION_HEADER = "X-Lumira-Event-Schema-Version";

    private final ObjectMapper objectMapper;
    private final Settings settings;
    private final Identity identity;
    private final HttpClient httpClient;

    public InternalHttpClientFactory(ObjectMapper objectMapper, Settings settings, Identity identity) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.settings = Objects.requireNonNull(settings, "settings").validated();
        this.identity = Objects.requireNonNull(identity, "identity").validated();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public InternalHttpClient create(String baseUrl, String token) {
        URI baseUri = requireTrustedBaseUrl(baseUrl);
        String normalizedToken = requireText(token, "internal token");
        return new InternalHttpClient(baseUri, normalizedToken);
    }

    public static URI requireTrustedBaseUrl(String value) {
        String normalized = requireText(value, "control-plane base URL");
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("control-plane base URL must be an absolute http(s) origin");
            }
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            return URI.create(origin);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("control-plane base URL is invalid", exception);
        }
    }

    public enum RetryMode {
        NEVER,
        IDEMPOTENT
    }

    public final class InternalHttpClient {
        private final URI baseUri;
        private final String token;

        private InternalHttpClient(URI baseUri, String token) {
            this.baseUri = baseUri;
            this.token = token;
        }

        public <T> T post(String path, Object body, TypeReference<T> responseType, RetryMode retryMode) {
            return post(path, body, responseType, retryMode, Map.of());
        }

        public <T> T post(
                String path,
                Object body,
                TypeReference<T> responseType,
                RetryMode retryMode,
                Map<String, String> additionalHeaders
        ) {
            Objects.requireNonNull(responseType, "responseType");
            Objects.requireNonNull(retryMode, "retryMode");
            URI uri = resolvePath(baseUri, path);
            byte[] requestBody = serialize(body);
            int attempts = retryMode == RetryMode.IDEMPOTENT ? settings.maxAttempts() : 1;
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    return send(uri, requestBody, responseType, additionalHeaders);
                } catch (InternalHttpException exception) {
                    lastFailure = exception;
                    if (!exception.retryable() || attempt >= attempts) {
                        throw exception;
                    }
                }
                pauseBeforeRetry(attempt);
            }
            throw lastFailure == null ? new InternalHttpException("internal HTTP request failed", false) : lastFailure;
        }

        private <T> T send(
                URI uri,
                byte[] requestBody,
                TypeReference<T> responseType,
                Map<String, String> additionalHeaders
        ) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(settings.responseTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Job-Token", token)
                    .header(HeaderConstants.TRACE_ID, currentTraceId())
                    .header(RELEASE_ID_HEADER, identity.releaseId())
                    .header(SCHEMA_VERSION_HEADER, Integer.toString(identity.schemaVersion()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            additionalHeaders.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                    request.header(name, value);
                }
            });
            try {
                HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    byte[] bytes = readBounded(body, response.headers().firstValueAsLong("Content-Length").orElse(-1L));
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                        throw new InternalHttpException(
                                "internal HTTP status " + response.statusCode() + " from " + uri,
                                retryable
                        );
                    }
                    if (bytes.length == 0) {
                        return null;
                    }
                    return objectMapper.readValue(bytes, responseType);
                }
            } catch (HttpTimeoutException exception) {
                throw new InternalHttpException("internal HTTP response timeout from " + uri, true, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new InternalHttpException("internal HTTP request interrupted", false, exception);
            } catch (IOException exception) {
                throw new InternalHttpException("internal HTTP transport failure to " + uri, true, exception);
            }
        }

        private byte[] readBounded(InputStream input, long declaredLength) throws IOException {
            if (declaredLength > settings.maxResponseBytes()) {
                throw new InternalHttpException("internal HTTP response exceeds configured maximum", false);
            }
            byte[] bytes = input.readNBytes(settings.maxResponseBytes() + 1);
            if (bytes.length > settings.maxResponseBytes()) {
                throw new InternalHttpException("internal HTTP response exceeds configured maximum", false);
            }
            return bytes;
        }
    }

    public static final class InternalHttpException extends RuntimeException {
        private final boolean retryable;

        public InternalHttpException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public InternalHttpException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    public record Settings(
            Duration connectTimeout,
            Duration responseTimeout,
            int maxResponseBytes,
            int maxAttempts,
            Duration retryBackoff
    ) {
        public static Settings defaults() {
            return new Settings(Duration.ofSeconds(2), Duration.ofSeconds(5), 1024 * 1024, 2, Duration.ofMillis(100));
        }

        private Settings validated() {
            requirePositive(connectTimeout, "connectTimeout");
            requirePositive(responseTimeout, "responseTimeout");
            requirePositive(retryBackoff, "retryBackoff");
            if (maxResponseBytes < 1024 || maxResponseBytes > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("maxResponseBytes must be between 1024 and 16777216");
            }
            if (maxAttempts < 1 || maxAttempts > 5) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
            }
            return this;
        }
    }

    public record Identity(String releaseId, int schemaVersion) {
        private Identity validated() {
            requireText(releaseId, "releaseId");
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            return this;
        }
    }

    private byte[] serialize(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            throw new InternalHttpException("internal HTTP request body is not serializable", false, exception);
        }
    }

    private void pauseBeforeRetry(int attempt) {
        long delayMs;
        try {
            delayMs = Math.multiplyExact(settings.retryBackoff().toMillis(), attempt);
        } catch (ArithmeticException exception) {
            delayMs = settings.retryBackoff().toMillis();
        }
        try {
            Thread.sleep(Math.max(1L, delayMs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InternalHttpException("internal HTTP retry interrupted", false, exception);
        }
    }

    private static URI resolvePath(URI baseUri, String path) {
        String normalized = requireText(path, "internal path");
        if (!normalized.startsWith("/") || normalized.startsWith("//")) {
            throw new IllegalArgumentException("internal path must be an absolute path without an authority");
        }
        URI resolved = baseUri.resolve(normalized);
        if (!Objects.equals(baseUri.getScheme(), resolved.getScheme())
                || !Objects.equals(baseUri.getRawAuthority(), resolved.getRawAuthority())) {
            throw new IllegalArgumentException("internal path must remain on the configured origin");
        }
        return resolved;
    }

    private static String currentTraceId() {
        String traceId = TraceContext.getTraceId();
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
