package com.lumira.saas.modules.system.sensitive.app;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordDictionaryVersionService {

    private final AtomicLong localVersion = new AtomicLong(1L);

    public long currentVersion() {
        return localVersion.get();
    }

    public long bumpVersion() {
        return localVersion.incrementAndGet();
    }
}
