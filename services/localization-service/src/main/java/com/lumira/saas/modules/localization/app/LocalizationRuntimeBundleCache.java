package com.lumira.saas.modules.localization.app;

import com.lumira.saas.modules.localization.vo.LocalizationVO;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

class LocalizationRuntimeBundleCache {

    private static final long MAX_ENTRIES = 256;
    private static final Duration ENTRY_TTL = Duration.ofMinutes(10);

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(128, 0.75f, true);
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    LocalizationVO.RuntimeBundleVO get(String localeCode, long readModelVersion, Long releaseVersion) {
        String fullKey = cacheKey(localeCode, readModelVersion, releaseVersion);
        Instant now = Instant.now();
        lock.lock();
        try {
            CacheEntry entry = cache.get(fullKey);
            if (entry == null || !entry.isAlive(now)) {
                if (entry != null) {
                    cache.remove(fullKey);
                }
                misses.incrementAndGet();
                return null;
            }
            hits.incrementAndGet();
            return entry.bundle;
        } finally {
            lock.unlock();
        }
    }

    void put(String localeCode, long readModelVersion, Long releaseVersion, LocalizationVO.RuntimeBundleVO bundle) {
        if (bundle == null) {
            return;
        }
        String key = cacheKey(localeCode, readModelVersion, releaseVersion);
        lock.lock();
        try {
            purgeExpired(Instant.now());
            while (cache.size() >= MAX_ENTRIES && !cache.containsKey(key)) {
                String lruKey = cache.keySet().iterator().next();
                cache.remove(lruKey);
            }
            cache.put(key, new CacheEntry(bundle, Instant.now().plus(ENTRY_TTL)));
        } finally {
            lock.unlock();
        }
    }

    void evictLocale(String localeCode) {
        String prefix = normalizeLocale(localeCode) + ":";
        lock.lock();
        try {
            cache.keySet().removeIf(key -> key.startsWith(prefix));
        } finally {
            lock.unlock();
        }
    }

    void evictStale(String localeCode, Long activeReleaseVersion) {
        String prefix = normalizeLocale(localeCode) + ":";
        lock.lock();
        try {
            cache.keySet().removeIf(key -> {
                if (!key.startsWith(prefix)) {
                    return false;
                }
                String[] parts = key.split(":");
                if (parts.length < 3) {
                    return false;
                }
                String releaseValue = parts[2];
                return !String.valueOf(activeReleaseVersion).equals(releaseValue);
            });
            purgeExpired(Instant.now());
        } finally {
            lock.unlock();
        }
    }

    int size() {
        lock.lock();
        try {
            purgeExpired(Instant.now());
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    long hits() {
        return hits.get();
    }

    long misses() {
        return misses.get();
    }

    double hitRatio() {
        long hitCount = hits.get();
        long total = hitCount + misses.get();
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    private void purgeExpired(Instant now) {
        cache.entrySet().removeIf(entry -> !entry.getValue().isAlive(now));
    }

    private String cacheKey(String localeCode, long readModelVersion, Long releaseVersion) {
        return normalizeLocale(localeCode) + ":" + readModelVersion + ":" + (releaseVersion == null ? 0L : releaseVersion);
    }

    private String normalizeLocale(String localeCode) {
        return localeCode == null || localeCode.isBlank() ? "zh-CN" : localeCode.trim();
    }

    private record CacheEntry(LocalizationVO.RuntimeBundleVO bundle, Instant expiresAt) {
        boolean isAlive(Instant now) {
            return expiresAt != null && now.isBefore(expiresAt);
        }
    }
}
