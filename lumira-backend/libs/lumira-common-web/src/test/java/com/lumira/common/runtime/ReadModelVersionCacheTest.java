package com.lumira.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReadModelVersionCacheTest {

    @Test
    void ttlStartsWhenTheLoadCompletes() {
        ReadModelVersionCache cache = new ReadModelVersionCache(200L);
        AtomicInteger loads = new AtomicInteger();

        Long first = cache.readValue("permission-snapshot", 200L, () -> {
            loads.incrementAndGet();
            try {
                Thread.sleep(300L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return 7L;
        });
        Long second = cache.readValue("permission-snapshot", 200L, () -> {
            loads.incrementAndGet();
            return 8L;
        });

        assertEquals(7L, first);
        assertEquals(7L, second);
        assertEquals(1, loads.get());
    }
}
