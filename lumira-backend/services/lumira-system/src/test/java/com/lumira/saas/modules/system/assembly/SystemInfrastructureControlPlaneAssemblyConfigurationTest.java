package com.lumira.saas.modules.system.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.infrastructure.security.service.SessionTrustedCurrentUserResolver;
import com.lumira.saas.infrastructure.adapter.SystemEventCatalogProjectionBridgeConfiguration;
import com.lumira.saas.infrastructure.adapter.SystemExpertApprovalEventBridgeConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class SystemInfrastructureControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyImportsTrustedCurrentUserResolverPortAdapter() {
        Import imported = SystemInfrastructureControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(SessionTrustedCurrentUserResolver.class);
    }

    @Test
    void explicitlyImportsExpertApprovalEventBridgeWithoutDependingOnExpertImplementation() {
        Import imported = SystemInfrastructureControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(SystemExpertApprovalEventBridgeConfiguration.class);
    }

    @Test
    void explicitlyImportsCatalogBridgeWithoutDependingOnCatalogImplementation() {
        Import imported = SystemInfrastructureControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).contains(SystemEventCatalogProjectionBridgeConfiguration.class);
    }
}
