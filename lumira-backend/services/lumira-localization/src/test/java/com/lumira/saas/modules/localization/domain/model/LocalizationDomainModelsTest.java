package com.lumira.saas.modules.localization.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.localization.domain.model.LocalizationDomainModels.BundleReadModel;
import com.lumira.saas.modules.localization.domain.model.LocalizationDomainModels.ReleaseAggregate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalizationDomainModelsTest {

    @Test
    void releasePublishEmitsPublishedEventOnce() {
        ReleaseAggregate release = new ReleaseAggregate(100L, 1L, "zh-CN", false);

        release.publish(12);
        release.publish(12);

        assertThat(release.domainEvents()).hasSize(1);
        assertThat(release.domainEvents().getFirst().eventType()).isEqualTo("LOCALIZATION_RELEASE_PUBLISHED");
        assertThat(release.domainEvents().getFirst().attributes()).containsEntry("entryCount", 12L);
    }

    @Test
    void bundleReadModelUsesStableCacheScope() {
        BundleReadModel bundle = new BundleReadModel(1L, "runtime", "zh-CN", 8L, Map.of("app.ok", "确定"));

        assertThat(bundle.version()).isEqualTo(8L);
        assertThat(bundle.cacheScope()).isEqualTo("localization.bundle:runtime:zh-CN");
    }
}
