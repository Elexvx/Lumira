package com.lumira.common.web;

import com.lumira.common.runtime.ServiceVersionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceVersionProviderTest {

    @Test
    void immutableImageIdentityWinsOverStaleHostDeploymentEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "lumira-server")
                .withProperty("LUMIRA_IMAGE_APP_VERSION", "main")
                .withProperty("LUMIRA_IMAGE_BUILD_VERSION", "main+new-commit")
                .withProperty("LUMIRA_IMAGE_FRONTEND_VERSION", "main+new-short")
                .withProperty("LUMIRA_IMAGE_BACKEND_VERSION", "main+new-short")
                .withProperty("LUMIRA_IMAGE_DATABASE_VERSION", "202608030002")
                .withProperty("LUMIRA_IMAGE_BUILD_TIME", "2026-08-03T06:00:00Z")
                .withProperty("LUMIRA_IMAGE_GIT_COMMIT", "new-commit")
                .withProperty("LUMIRA_IMAGE_GIT_BRANCH", "main")
                .withProperty("APP_VERSION", "old")
                .withProperty("BUILD_VERSION", "old-build")
                .withProperty("FRONTEND_VERSION", "old-frontend")
                .withProperty("BACKEND_VERSION", "old-backend")
                .withProperty("DATABASE_VERSION", "202608030002")
                .withProperty("BUILD_TIME", "2026-07-01T00:00:00Z")
                .withProperty("GIT_COMMIT", "old-commit")
                .withProperty("GIT_BRANCH", "old-branch");
        var buildPropertiesProvider = new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class);

        ServiceVersionInfo current = new ServiceVersionProvider(environment, buildPropertiesProvider).current();

        assertThat(current.version()).isEqualTo("main");
        assertThat(current.buildTime()).isEqualTo("2026-08-03T06:00:00Z");
        assertThat(current.commitId()).isEqualTo("new-commit");
        assertThat(current.branch()).isEqualTo("main");
        assertThat(current.frontendVersion()).isEqualTo("main+new-short");
        assertThat(current.backendVersion()).isEqualTo("main+new-short");
        assertThat(current.databaseVersion()).isEqualTo("202608030002");
    }
}
