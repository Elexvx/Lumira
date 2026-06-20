package com.lumira.saas.modules.system.sensitive.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordDictionaryVersionService {

    private final Map<Long, AtomicLong> localVersions = new ConcurrentHashMap<>();

    public long currentVersion(Long tenantId) {
        return localVersions.computeIfAbsent(normalizeTenantId(tenantId), ignored -> new AtomicLong(1L)).get();
    }

    public long bumpVersion(Long tenantId) {
        return localVersions.computeIfAbsent(normalizeTenantId(tenantId), ignored -> new AtomicLong(1L)).incrementAndGet();
    }

    private Long normalizeTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }
}
