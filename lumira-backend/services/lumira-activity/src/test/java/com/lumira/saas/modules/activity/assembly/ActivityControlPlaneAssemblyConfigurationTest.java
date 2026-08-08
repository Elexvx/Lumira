package com.lumira.saas.modules.activity.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.app.ActivityRegistrationAppService;
import com.lumira.saas.modules.activity.controller.ActivityRegistrationV2Controller;
import com.lumira.saas.modules.activity.controller.ActivityV2Controller;
import com.lumira.saas.modules.activity.controller.PublicActivityController;
import com.lumira.saas.modules.activity.infrastructure.JdbcActivityRepository;
import com.lumira.saas.modules.activity.integration.ActivityCatalogSourceSnapshotAdapter;
import com.lumira.saas.modules.activity.infrastructure.persistence.JdbcActivityRegistrationRepository;
import com.lumira.saas.modules.activity.infrastructure.persistence.JdbcActivitySqlOperations;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ActivityControlPlaneAssemblyConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ActivityControlPlaneAssemblyConfiguration.class, ActivityRuntimeContracts.class);

    @Test
    void explicitlyImportsEveryActivityRuntimeComponent() {
        Import imported = ActivityControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).containsExactlyInAnyOrder(
                JdbcActivitySqlOperations.class,
                JdbcActivityRepository.class,
                ActivityCatalogSourceSnapshotAdapter.class,
                JdbcActivityRegistrationRepository.class,
                ActivityManagementAppService.class,
                ActivityRegistrationAppService.class,
                ActivityV2Controller.class,
                ActivityRegistrationV2Controller.class,
                PublicActivityController.class
        );
    }

    @Test
    void enabledControlPlaneBuildsActivityContextThroughExplicitAssembly() {
        contextRunner
                .withPropertyValues("lumira.runtime.control-plane-enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(JdbcActivitySqlOperations.class)
                        .hasSingleBean(JdbcActivityRepository.class)
                        .hasSingleBean(JdbcActivityRegistrationRepository.class)
                        .hasSingleBean(ActivityManagementAppService.class)
                        .hasSingleBean(ActivityRegistrationAppService.class)
                        .hasSingleBean(ActivityV2Controller.class)
                        .hasSingleBean(ActivityRegistrationV2Controller.class)
                        .hasSingleBean(PublicActivityController.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActivityRuntimeContracts {

        @Bean
        JdbcTemplate activityJdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        TrustedCurrentUserResolver trustedCurrentUserResolver() {
            return mock(TrustedCurrentUserResolver.class);
        }

        @Bean
        SecurityContextFacade activitySecurityContextFacade() {
            return mock(SecurityContextFacade.class);
        }

        @Bean
        PermissionGuard activityPermissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        TransactionalEventOutboxPort transactionalEventOutboxPort() {
            return mock(TransactionalEventOutboxPort.class);
        }

        @Bean
        EventCatalogQueryPort eventCatalogQueryPort() {
            return mock(EventCatalogQueryPort.class);
        }

        @Bean
        DictionaryValueNormalizer dictionaryValueNormalizer() {
            return mock(DictionaryValueNormalizer.class);
        }
    }
}
