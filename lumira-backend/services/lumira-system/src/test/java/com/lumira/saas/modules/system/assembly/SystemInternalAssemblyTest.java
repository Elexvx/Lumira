package com.lumira.saas.modules.system.assembly;

import java.util.Arrays;

import com.lumira.api.export.ExportTaskQueuePort;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemInternalApiService;
import com.lumira.saas.modules.system.SystemAsyncAssemblyConfiguration;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.controller.InternalSystemController;
import com.lumira.saas.modules.system.internal.app.InternalSystemApplicationService;
import com.lumira.saas.modules.system.internal.infrastructure.JdbcInternalSystemRepository;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.update.app.PlatformUpdateMaintenanceService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.modules.system.user.app.UserExportTaskWorkerService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SystemInternalAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void monolithKeepsLocalInternalApiAndControllerAvailable() {
        contextRunner.withPropertyValues("lumira.monolith=true").run(context -> {
            assertThat(context.getBeansOfType(SystemInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(SystemInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalSystemController.class)).hasSize(1);
        });
    }

    @Test
    void splitRuntimeExposesControllerAndLocalInternalApi() {
        contextRunner.withPropertyValues("lumira.monolith=false").run(context -> {
            assertThat(context.getBeansOfType(SystemInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(SystemInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalSystemController.class)).hasSize(1);
        });
    }

    @Test
    void controlPlaneAssemblyKeepsTheUserExportRenderingWorker() {
        Import imports = SystemOperationsControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);
        Import asyncImports = SystemAsyncAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(asyncImports).isNotNull();
        assertThat(imports.value()).contains(UserExportTaskWorkerService.class);
        assertThat(asyncImports.value()).doesNotContain(UserExportTaskWorkerService.class);
        assertThat(UserExportTaskWorkerService.class.isAnnotationPresent(ConditionalOnLumiraAsyncEnabled.class))
                .isFalse();

        var autowiredConstructors = Arrays.stream(UserExportAppService.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .toList();
        assertThat(autowiredConstructors).hasSize(1);
        assertThat(autowiredConstructors.getFirst().getParameterTypes()).contains(
                SystemInternalApi.class,
                SessionAuthenticationService.class
        );
        assertThat(Arrays.stream(autowiredConstructors.getFirst().getGenericParameterTypes())
                .map(type -> type.getTypeName())
                .toList()).anyMatch(type -> type.contains(UserExportTaskWorkerService.class.getName()));

        assertThat(Arrays.stream(UserExportTaskWorkerService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toList()).contains(ExportTaskQueuePort.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            SystemInternalApiService.class,
            JdbcInternalSystemRepository.class,
            InternalSystemApplicationService.class,
            InternalSystemController.class
    })
    static class TestConfiguration {

        @Bean
        UserDomainService userDomainService() {
            return mock(UserDomainService.class);
        }

        @Bean
        IamUserService iamUserService() {
            return mock(IamUserService.class);
        }

        @Bean
        PermissionSnapshotService permissionSnapshotService() {
            return mock(PermissionSnapshotService.class);
        }

        @Bean
        CaptchaService captchaService() {
            return mock(CaptchaService.class);
        }

        @Bean
        SystemVerificationAppService systemVerificationAppService() {
            return mock(SystemVerificationAppService.class);
        }

        @Bean
        WechatLoginSettingsService wechatLoginSettingsService() {
            return mock(WechatLoginSettingsService.class);
        }

        @Bean
        PasskeyCredentialAppService passkeyCredentialAppService() {
            return mock(PasskeyCredentialAppService.class);
        }

        @Bean
        MyBatisQueryOperations myBatisQueryOperations() {
            return mock(MyBatisQueryOperations.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        LoginAuditService loginAuditService() {
            return mock(LoginAuditService.class);
        }

        @Bean
        AuthSessionStore authSessionStore() {
            return mock(AuthSessionStore.class);
        }

        @Bean
        OperationAuditService operationAuditService() {
            return mock(OperationAuditService.class);
        }

        @Bean
        SecuritySettingsService securitySettingsService() {
            return mock(SecuritySettingsService.class);
        }

        @Bean
        PasswordPolicyService passwordPolicyService() {
            return mock(PasswordPolicyService.class);
        }

        @Bean
        ReadModelVersionService readModelVersionService() {
            return mock(ReadModelVersionService.class);
        }

        @Bean
        SystemConfigVersioningService systemConfigVersioningService() {
            return mock(SystemConfigVersioningService.class);
        }
    }

    @Test
    void controlPlaneAssemblyOwnsPlatformUpdateMaintenance() {
        Import imports = SystemOperationsControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(PlatformUpdateMaintenanceService.class);
    }
}
