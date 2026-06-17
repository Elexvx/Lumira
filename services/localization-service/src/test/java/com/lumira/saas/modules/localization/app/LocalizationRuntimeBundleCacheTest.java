package com.lumira.saas.modules.localization.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.localization.vo.LocalizationVO;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalizationRuntimeBundleCacheTest {

    @Test
    void evictStaleRemovesOldVersionsForLocaleOnly() {
        LocalizationRuntimeBundleCache cache = new LocalizationRuntimeBundleCache();
        cache.put("zh-CN", 7L, 1L, bundle("zh-CN", 1L));
        cache.put("zh-CN", 7L, 2L, bundle("zh-CN", 2L));
        cache.put("en-US", 8L, 1L, bundle("en-US", 1L));

        cache.evictStale("zh-CN", 2L);

        assertThat(cache.get("zh-CN", 7L, 1L)).isNull();
        assertThat(cache.get("zh-CN", 7L, 2L)).isNotNull();
        assertThat(cache.get("en-US", 8L, 1L)).isNotNull();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void evictLocaleClearsAllVersionsForSingleLocale() {
        LocalizationRuntimeBundleCache cache = new LocalizationRuntimeBundleCache();
        cache.put("zh-CN", 7L, 1L, bundle("zh-CN", 1L));
        cache.put("zh-CN", 7L, 2L, bundle("zh-CN", 2L));
        cache.put("en-US", 8L, 1L, bundle("en-US", 1L));

        cache.evictLocale("zh-CN");

        assertThat(cache.get("zh-CN", 7L, 1L)).isNull();
        assertThat(cache.get("zh-CN", 7L, 2L)).isNull();
        assertThat(cache.get("en-US", 8L, 1L)).isNotNull();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void metrics_shouldTrackHitsMissesAndHitRatio() {
        LocalizationRuntimeBundleCache cache = new LocalizationRuntimeBundleCache();
        cache.put("zh-CN", 7L, 1L, bundle("zh-CN", 1L));

        assertThat(cache.get("zh-CN", 7L, 1L)).isNotNull();
        assertThat(cache.get("zh-CN", 7L, 2L)).isNull();
        assertThat(cache.hits()).isEqualTo(1L);
        assertThat(cache.misses()).isEqualTo(1L);
        assertThat(cache.hitRatio()).isEqualTo(0.5);
    }

    @Test
    void maxEntriesEvictionKeepsCacheBounded() {
        LocalizationRuntimeBundleCache cache = new LocalizationRuntimeBundleCache();

        for (int idx = 0; idx < 300; idx++) {
            cache.put("zh-CN", 7L, (long) idx, bundle("zh-CN", (long) idx));
        }

        assertThat(cache.size()).isLessThanOrEqualTo(256);
    }

    @Test
    void evictLocaleKeepsOtherLocaleEntries() {
        LocalizationRuntimeBundleCache cache = new LocalizationRuntimeBundleCache();
        cache.put("zh-CN", 7L, 1L, bundle("zh-CN", 1L));
        cache.put("zh-CN", 7L, 2L, bundle("zh-CN", 2L));
        cache.put("en-US", 8L, 1L, bundle("en-US", 1L));

        cache.evictLocale("en-US");

        assertThat(cache.get("zh-CN", 7L, 1L)).isNotNull();
        assertThat(cache.get("zh-CN", 7L, 2L)).isNotNull();
        assertThat(cache.get("en-US", 8L, 1L)).isNull();
    }

    private LocalizationVO.RuntimeBundleVO bundle(String localeCode, Long releaseVersion) {
        LocalizationVO.RuntimeBundleVO bundle = new LocalizationVO.RuntimeBundleVO();
        bundle.setLocaleCode(localeCode);
        bundle.setReleaseVersion(releaseVersion);
        bundle.setMessages(Map.of("common.ok", "OK"));
        return bundle;
    }
}
