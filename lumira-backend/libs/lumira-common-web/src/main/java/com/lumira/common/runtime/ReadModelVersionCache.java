package com.lumira.common.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ReadModelVersionCache {

    private static final long DEFAULT_TTL_MILLIS = 2_000L;

    private final ConcurrentMap<String, CachedVersion> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Long>> inFlight = new ConcurrentHashMap<>();
    private final long defaultTtlMillis;

    public ReadModelVersionCache() {
        this(DEFAULT_TTL_MILLIS);
    }

    public ReadModelVersionCache(long defaultTtlMillis) {
        this.defaultTtlMillis = Math.max(1L, defaultTtlMillis);
    }

    public ReadResult read(String cacheKey, Supplier<Long> loader) {
        return read(cacheKey, defaultTtlMillis, loader);
    }

    public ReadResult read(String cacheKey, long ttlMillis, Supplier<Long> loader) {
        Objects.requireNonNull(cacheKey, "cacheKey");
        Objects.requireNonNull(loader, "loader");

        long now = System.currentTimeMillis();
        CachedVersion cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            return new ReadResult(cached.version(), true);
        }

        CompletableFuture<Long> created = new CompletableFuture<>();
        CompletableFuture<Long> existing = inFlight.putIfAbsent(cacheKey, created);
        if (existing == null) {
            try {
                // Another loader may have populated the cache after this thread observed
                // the initial miss but before it claimed the single-flight slot.
                long claimedAt = System.currentTimeMillis();
                CachedVersion refreshed = cache.get(cacheKey);
                if (refreshed != null && refreshed.expiresAtMillis() > claimedAt) {
                    created.complete(refreshed.version());
                    return new ReadResult(refreshed.version(), true);
                }
                Long loaded = loader.get();
                cache.put(cacheKey, new CachedVersion(
                        loaded,
                        System.currentTimeMillis() + Math.max(1L, ttlMillis)
                ));
                created.complete(loaded);
                return new ReadResult(loaded, false);
            } catch (Throwable throwable) {
                created.completeExceptionally(throwable);
                if (throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (throwable instanceof Error error) {
                    throw error;
                }
                throw new CompletionException(throwable);
            } finally {
                inFlight.remove(cacheKey, created);
            }
        }

        try {
            return new ReadResult(existing.join(), true);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    public Long readValue(String cacheKey, Supplier<Long> loader) {
        return read(cacheKey, loader).version();
    }

    public Long readValue(String cacheKey, long ttlMillis, Supplier<Long> loader) {
        return read(cacheKey, ttlMillis, loader).version();
    }

    public void put(String cacheKey, Long version, long ttlMillis) {
        Objects.requireNonNull(cacheKey, "cacheKey");
        cache.put(cacheKey, new CachedVersion(version, System.currentTimeMillis() + Math.max(1L, ttlMillis)));
    }

    public void invalidate(String cacheKey) {
        if (cacheKey == null) {
            return;
        }
        cache.remove(cacheKey);
        inFlight.remove(cacheKey);
    }

    public void clear() {
        cache.clear();
        inFlight.clear();
    }

    public record ReadResult(Long version, boolean cacheHit) {
    }

    private record CachedVersion(Long version, long expiresAtMillis) {
    }
}
