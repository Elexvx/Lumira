package com.lumira.saas.modules.plugin.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.plugin.BuiltinPluginLifecycleHook;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.lumira.saas.modules.plugin.loader.PluginArtifactLoader;
import com.lumira.saas.modules.plugin.loader.PluginRuntimeLoader;
import com.lumira.saas.modules.plugin.registry.PluginRegistry;
import com.lumira.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.lumira.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import com.lumira.saas.modules.plugin.service.PluginMigrationService;
import com.lumira.saas.modules.plugin.service.PluginPersistenceService;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagementAppServiceTest {

    @Mock
    private PluginArtifactLoader pluginArtifactLoader;

    @Mock
    private PluginPersistenceService pluginPersistenceService;

    @Mock
    private PluginMigrationService pluginMigrationService;

    @Mock
    private PluginRuntimeLoader pluginRuntimeLoader;

    @Mock
    private PluginRegistry pluginRegistry;

    @Mock
    private SystemInternalApi systemInternalApi;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @TempDir
    Path tempDir;

    private PluginManagementAppService pluginManagementAppService;

    @BeforeEach
    void setUp() {
        lenient().when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "alice", "ENABLED"));
        lenient().when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100")).thenReturn(permissionSnapshot("permissions-1"));
        pluginManagementAppService = new PluginManagementAppService(
                pluginArtifactLoader,
                pluginPersistenceService,
                pluginMigrationService,
                pluginRuntimeLoader,
                pluginRegistry,
                new PluginSemver(),
                systemInternalApi,
                transactionManager,
                new ObjectMapper(),
                domainEventPublisher
        );
    }

    @Test
    void upload_shouldRejectUntrustedOperatorBeforeStagingPackage() {
        CurrentUser untrusted = new CurrentUser(100L, "alice", null, 3, true, Set.of("plugin:management:upload"));

        assertThatThrownBy(() -> pluginManagementAppService.upload(null, untrusted))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(pluginArtifactLoader);
    }

    @Test
    void upload_shouldRejectWhenLivePermissionsLoseUploadPermission() {
        when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100"))
                .thenReturn(permissionSnapshot("permissions-2", "plugin:management:enable"));

        assertThatThrownBy(() -> pluginManagementAppService.upload(null, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.lumira.common.enums.ErrorCode.FORBIDDEN)
                .hasMessageContaining("plugin:management:upload");

        verifyNoInteractions(pluginArtifactLoader);
    }

    @Test
    void enable_shouldInvalidatePermissionSnapshotAfterGlobalBinding() {
        PluginRuntimeDescriptor descriptor = new PluginRuntimeDescriptor(
                "sms",
                "1.0.0",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
        when(pluginRegistry.find("sms", "1.0.0")).thenReturn(Optional.of(descriptor));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        doNothing().when(pluginPersistenceService).enablePlugin("sms", "1.0.0", "{\"level\":\"basic\"}", 100L, "user-uuid-100");
        doNothing().when(pluginPersistenceService).registerPluginPermissions("sms", "1.0.0");

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{\"level\":\"basic\"}");

        pluginManagementAppService.enable(request, currentUser());

        verify(pluginPersistenceService).enablePlugin("sms", "1.0.0", "{\"level\":\"basic\"}", 100L, "user-uuid-100");
        verify(pluginPersistenceService).registerPluginPermissions("sms", "1.0.0");
        verify(pluginPersistenceService).bumpBootstrapVersion("plugin.enabled");
        verify(pluginMigrationService).executeUpMigrations("sms", "1.0.0", null, 100L, "user-uuid-100");
    }

    @Test
    void builtinMockEnableShouldProvisionThroughItsLifecycleHookInsideTheEnableTransaction() {
        BuiltinPluginLifecycleHook hook = mock(BuiltinPluginLifecycleHook.class);
        when(hook.pluginCode()).thenReturn("builtin-mock-payment");
        ObjectProvider<BuiltinPluginLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(hookProvider.orderedStream()).thenReturn(Stream.of(hook));
        pluginManagementAppService.setBuiltinPluginLifecycleHooks(hookProvider);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("builtin-mock-payment");
        request.setVersion("1.0.0");

        pluginManagementAppService.enable(request, currentUser());

        InOrder ordered = inOrder(pluginPersistenceService, hook);
        ordered.verify(pluginPersistenceService).enablePlugin(
                "builtin-mock-payment", "1.0.0", null, 100L, "user-uuid-100"
        );
        ordered.verify(hook).onEnable(any(BuiltinPluginLifecycleHook.PluginLifecycleContext.class));
    }

    @Test
    void builtinMockSmsEnableShouldProvisionThroughItsLifecycleHookInsideTheEnableTransaction() {
        BuiltinPluginLifecycleHook hook = mock(BuiltinPluginLifecycleHook.class);
        when(hook.pluginCode()).thenReturn("builtin-mock-sms");
        ObjectProvider<BuiltinPluginLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(hookProvider.orderedStream()).thenReturn(Stream.of(hook));
        pluginManagementAppService.setBuiltinPluginLifecycleHooks(hookProvider);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("builtin-mock-sms");
        request.setVersion("1.0.0");

        pluginManagementAppService.enable(request, currentUser());

        InOrder ordered = inOrder(pluginPersistenceService, hook);
        ordered.verify(pluginPersistenceService).enablePlugin(
                "builtin-mock-sms", "1.0.0", null, 100L, "user-uuid-100"
        );
        ordered.verify(hook).onEnable(any(BuiltinPluginLifecycleHook.PluginLifecycleContext.class));
    }

    @Test
    void builtinMockSmsEnableShouldFailClosedWhenLifecycleHookIsMissing() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("builtin-mock-sms");
        request.setVersion("1.0.0");

        assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.lumira.common.enums.ErrorCode.DEPENDENCY_UNAVAILABLE
                )
                .hasMessageContaining("builtin-mock-sms");
    }

    @Test
    void builtinAlertingEnableShouldResumeWorkersThroughItsLifecycleHook() {
        BuiltinPluginLifecycleHook hook = mock(BuiltinPluginLifecycleHook.class);
        when(hook.pluginCode()).thenReturn("builtin-alerting");
        ObjectProvider<BuiltinPluginLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(hookProvider.orderedStream()).thenReturn(Stream.of(hook));
        pluginManagementAppService.setBuiltinPluginLifecycleHooks(hookProvider);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("builtin-alerting");
        request.setVersion("1.0.0");

        pluginManagementAppService.enable(request, currentUser());

        InOrder ordered = inOrder(pluginPersistenceService, hook);
        ordered.verify(pluginPersistenceService).enablePlugin(
                "builtin-alerting", "1.0.0", null, 100L, "user-uuid-100"
        );
        ordered.verify(hook).onEnable(any(BuiltinPluginLifecycleHook.PluginLifecycleContext.class));
    }

    @Test
    void builtinAlertingEnableFailsClosedWhenLifecycleHookIsMissing() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("builtin-alerting");
        request.setVersion("1.0.0");

        assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.lumira.common.enums.ErrorCode.DEPENDENCY_UNAVAILABLE
                )
                .hasMessageContaining("builtin-alerting");
    }

    @Test
    void enable_shouldCheckEmailWithUserProfileOnly() {
        PluginSecondFactorProvider secondFactorProvider = org.mockito.Mockito.mock(PluginSecondFactorProvider.class);
        when(secondFactorProvider.requiresEmail()).thenReturn(true);
        PluginRuntimeDescriptor descriptor = new PluginRuntimeDescriptor(
                "email-mfa",
                "1.0.0",
                null,
                null,
                null,
                null,
                null,
                secondFactorProvider,
                List.of(),
                List.of(),
                List.of()
        );
        when(pluginRegistry.find("email-mfa", "1.0.0")).thenReturn(Optional.of(descriptor));
        when(systemInternalApi.userHasEmail(100L, "user-uuid-100")).thenReturn(true);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        doNothing().when(pluginPersistenceService).enablePlugin("email-mfa", "1.0.0", null, 100L, "user-uuid-100");
        doNothing().when(pluginPersistenceService).registerPluginPermissions("email-mfa", "1.0.0");

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("email-mfa");
        request.setVersion("1.0.0");

        pluginManagementAppService.enable(request, currentUser());

        verify(systemInternalApi).userHasEmail(100L, "user-uuid-100");
        verify(systemInternalApi, never()).findUserById(100L);
    }

    @Test
    void enable_shouldRejectUserProfileWhenUserUuidDoesNotMatchOperator() {
        PluginSecondFactorProvider secondFactorProvider = org.mockito.Mockito.mock(PluginSecondFactorProvider.class);
        when(secondFactorProvider.requiresEmail()).thenReturn(true);
        PluginRuntimeDescriptor descriptor = new PluginRuntimeDescriptor(
                "email-mfa",
                "1.0.0",
                null,
                null,
                null,
                null,
                null,
                secondFactorProvider,
                List.of(),
                List.of(),
                List.of()
        );
        when(pluginRegistry.find("email-mfa", "1.0.0")).thenReturn(Optional.of(descriptor));
        when(systemInternalApi.userHasEmail(100L, "user-uuid-100")).thenReturn(false);

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("email-mfa");
        request.setVersion("1.0.0");

        assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.lumira.common.enums.ErrorCode.BIZ_ERROR);

        verify(pluginPersistenceService, never()).enablePlugin(any(), any(), any(), any(), any());
        verify(pluginMigrationService, never()).executeUpMigrations(any(), any(), any(), any(), any());
    }

    @Test
    void enable_shouldRejectMissingSessionVersionBeforePersistence() {
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{}");
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.lumira.common.enums.ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(pluginPersistenceService, pluginRegistry, transactionManager);
    }

    @Test
    void enable_shouldRejectDisabledTrustedOperatorBeforePersistence() {
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "alice", "DISABLED"));
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{}");

        assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.lumira.common.enums.ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(pluginPersistenceService, pluginRegistry, transactionManager);
    }

    @Test
    void enable_shouldRejectWhenLivePermissionsLoseEnablePermissionBeforePersistence() {
        when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100"))
                .thenReturn(permissionSnapshot("permissions-2", "plugin:management:view"));
        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{}");

        assertThatThrownBy(() -> pluginManagementAppService.enable(request, currentUser()))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.lumira.common.enums.ErrorCode.FORBIDDEN)
                .hasMessageContaining("plugin:management:enable");

        verifyNoInteractions(pluginPersistenceService, pluginRegistry, transactionManager);
    }

    @Test
    void disable_shouldPurgeSchemaWhenRequested() {
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("sms");
        enabledVersion.setVersion("1.0.0");

        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(true);
        when(pluginPersistenceService.findEnabledVersion("sms")).thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("sms")).thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("sms");
        request.setPurgeData(true);

        pluginManagementAppService.disable(request, currentUser());

        verify(pluginPersistenceService).disablePlugin("sms", 100L, "user-uuid-100");
        verify(pluginPersistenceService).bumpBootstrapVersion("plugin.disabled");
        verify(pluginMigrationService).executeDownMigrations("sms", "1.0.0", null, 100L, "user-uuid-100");
        verify(systemInternalApi).invalidatePermissionSnapshot();
        verify(domainEventPublisher).publishAll(any());
    }

    @Test
    void builtinMockDisableShouldBlockPluginStateBeforeCancellingPendingPayments() {
        BuiltinPluginLifecycleHook hook = mock(BuiltinPluginLifecycleHook.class);
        when(hook.pluginCode()).thenReturn("builtin-mock-payment");
        ObjectProvider<BuiltinPluginLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(hookProvider.orderedStream()).thenReturn(Stream.of(hook));
        pluginManagementAppService.setBuiltinPluginLifecycleHooks(hookProvider);
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("builtin-mock-payment");
        enabledVersion.setVersion("1.0.0");
        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(false);
        when(pluginPersistenceService.findEnabledVersion("builtin-mock-payment"))
                .thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("builtin-mock-payment"))
                .thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("builtin-mock-payment");
        request.setPurgeData(false);

        pluginManagementAppService.disable(request, currentUser());

        InOrder ordered = inOrder(pluginPersistenceService, hook);
        ordered.verify(pluginPersistenceService).disablePlugin(
                "builtin-mock-payment", 100L, "user-uuid-100"
        );
        ordered.verify(hook).onDisable(any(BuiltinPluginLifecycleHook.PluginLifecycleContext.class));
    }

    @Test
    void builtinMockSmsDisableShouldBlockPluginStateBeforeInvalidatingVerificationSettings() {
        BuiltinPluginLifecycleHook hook = mock(BuiltinPluginLifecycleHook.class);
        when(hook.pluginCode()).thenReturn("builtin-mock-sms");
        ObjectProvider<BuiltinPluginLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(hookProvider.orderedStream()).thenReturn(Stream.of(hook));
        pluginManagementAppService.setBuiltinPluginLifecycleHooks(hookProvider);
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("builtin-mock-sms");
        enabledVersion.setVersion("1.0.0");
        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(false);
        when(pluginPersistenceService.findEnabledVersion("builtin-mock-sms"))
                .thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("builtin-mock-sms"))
                .thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("builtin-mock-sms");
        request.setPurgeData(false);

        pluginManagementAppService.disable(request, currentUser());

        InOrder ordered = inOrder(pluginPersistenceService, hook);
        ordered.verify(pluginPersistenceService).disablePlugin(
                "builtin-mock-sms", 100L, "user-uuid-100"
        );
        ordered.verify(hook).onDisable(any(BuiltinPluginLifecycleHook.PluginLifecycleContext.class));
    }

    @Test
    void disable_shouldPurgeSensitiveWordsSchemaWhenCapabilityIsEnabled() {
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("sensitive-words");
        enabledVersion.setVersion("1.0.0");

        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(true);
        when(pluginPersistenceService.findEnabledVersion("sensitive-words")).thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("sensitive-words")).thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("sensitive-words");
        request.setPurgeData(true);

        pluginManagementAppService.disable(request, currentUser());

        verify(pluginMigrationService).executeDownMigrations("sensitive-words", "1.0.0", null, 100L, "user-uuid-100");
        verify(pluginPersistenceService).updateVersionStatus(
                "sensitive-words", "1.0.0", "LOADED", "UNLOADED", "HEALTHY", "DISABLED", "REMOVED",
                100L, "user-uuid-100"
        );
    }

    @Test
    void disable_shouldRejectPurgeWhenPluginDoesNotSupportDataPurge() {
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("work-order-feedback");
        enabledVersion.setVersion("1.0.0");

        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(false);
        when(pluginPersistenceService.findEnabledVersion("work-order-feedback")).thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("work-order-feedback")).thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("work-order-feedback");
        request.setPurgeData(true);

        assertThatThrownBy(() -> pluginManagementAppService.disable(request, currentUser()))
                .hasMessageContaining("does not support data purge");

        verify(pluginPersistenceService, never()).disablePlugin(any(), any(), any());
    }

    @Test
    void currentMenus_shouldMergeBuiltinMenusAndPluginMenus() throws Exception {
        Path manifest = tempDir.resolve("manifest.json");
        Path versionHome = tempDir.resolve("sms").resolve("1.0.0");
        Files.createDirectories(versionHome.resolve("lumira-ui"));
        Files.writeString(versionHome.resolve("lumira-ui/index.js"), "console.log('sms');");
        Files.writeString(manifest, """
                {
                  "pluginCode": "sms",
                  "version": "1.0.0",
                  "entry": "index.js",
                  "assets": ["index.js"],
                  "routes": ["/plugins/sms"],
                  "sharedDeps": ["react"]
                }
                """);

        PluginVO.PluginAvailabilityVO availablePlugin = new PluginVO.PluginAvailabilityVO();
        availablePlugin.setPluginCode("sms");
        availablePlugin.setPluginName("SMS Plugin");
        availablePlugin.setVersion("1.0.0");
        availablePlugin.setManifestPath(manifest.toString());
        availablePlugin.setSharedDeps(new ArrayList<>());
        availablePlugin.setRoutes(new ArrayList<>());
        availablePlugin.setMenus(new ArrayList<>());

        PluginVersionEntity versionEntity = new PluginVersionEntity();
        versionEntity.setPluginCode("sms");
        versionEntity.setVersion("1.0.0");
        versionEntity.setArtifactPath(versionHome.toString());
        versionEntity.setFrontendManifestPath(manifest.toString());

        PluginMenuRelEntity menuRelation = new PluginMenuRelEntity();
        menuRelation.setPluginCode("sms");
        menuRelation.setPluginVersion("1.0.0");
        menuRelation.setMenuCode("plugin.sms");
        menuRelation.setMenuName("SMS Plugin");
        menuRelation.setRoutePath("/plugins/sms");
        menuRelation.setIcon("MessageOutlined");
        menuRelation.setPermissionKey("plugin:sms:view");
        menuRelation.setParentMenuCode(null);
        menuRelation.setSortNo(10);

        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("Dashboard");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setPermissionKey("dashboard:view");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());

        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.findVersion("sms", "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations("sms", "1.0.0")).thenReturn(List.of(menuRelation));

        List<Map<String, Object>> menus = pluginManagementAppService.currentMenus(List.of("dashboard:view", "plugin:sms:view"));

        assertThat(menus).extracting(menu -> (String) menu.get("menuCode"))
                .contains("system.dashboard", "plugin.sms");
        Map<String, Object> pluginMenu = menus.stream()
                .filter(menu -> "plugin.sms".equals(menu.get("menuCode")))
                .findFirst()
                .orElseThrow();
        assertThat(pluginMenu.get("path")).isEqualTo("/plugins/sms");
        assertThat(pluginMenu.get("permissionKey")).isEqualTo("plugin:sms:view");
    }

    @Test
    void currentBootstrap_shouldCacheByPermissionSnapshotVersionAndReuseAvailablePlugins() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);
        PluginMenuRelEntity menuRelation = createMenuRelation(availablePluginCode, "1.0.0", "plugin.sms");

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L, 10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L, 20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(java.util.Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of(menuRelation));

        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("Dashboard");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));

        Map<String, Object> bootstrap = pluginManagementAppService.currentBootstrap(
                List.of("plugin:sms:view"),
                "v10:data-scope-cache-v4"
        );
        expireReadModelVersionCache();
        Map<String, Object> bootstrapSecond = pluginManagementAppService.currentBootstrap(
                List.of("plugin:sms:view"),
                "v10:data-scope-cache-v4"
        );

        assertThat(bootstrap).containsKeys("menuTree", "availablePlugins");
        assertThat(bootstrapSecond).isEqualTo(bootstrap);

        verify(systemInternalApi, times(2)).readModelVersion("plugin", "bootstrap");
        verify(systemInternalApi, times(2)).readModelVersion("platform", "menu-tree");
        verify(systemInternalApi, times(1)).builtinMenus();
        verify(pluginPersistenceService, times(1)).listAvailablePlugins();
        verify(pluginPersistenceService, times(1)).listMenuRelations(availablePluginCode, "1.0.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldFilterAvailablePluginMenusByPermissionSnapshot() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);
        PluginMenuRelEntity menuRelation = createMenuRelation(availablePluginCode, "1.0.0", "plugin.sms");

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of(menuRelation));
        when(systemInternalApi.builtinMenus()).thenReturn(List.of());

        Map<String, Object> bootstrap = pluginManagementAppService.currentBootstrap(List.of(), "anonymous");

        assertThat(collectMenuCodes((List<Map<String, Object>>) bootstrap.get("menuTree")))
                .doesNotContain("plugin.sms");
        List<PluginVO.PluginAvailabilityVO> availablePlugins = (List<PluginVO.PluginAvailabilityVO>) bootstrap.get("availablePlugins");
        assertThat(availablePlugins).hasSize(1);
        assertThat(availablePlugins.get(0).getMenus()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldHideUnknownPermissionlessPagesButKeepAuthenticatedPages() throws Exception {
        MenuNodeDTO settingsRoot = new MenuNodeDTO();
        settingsRoot.setMenuCode("settings.root");
        settingsRoot.setName("Settings");
        settingsRoot.setPath("/settings");
        settingsRoot.setComponent("@/layouts/SettingsLayout");
        settingsRoot.setPermissionKey("system:view");
        settingsRoot.setSortNo(1);

        MenuNodeDTO unsafeManagementPage = new MenuNodeDTO();
        unsafeManagementPage.setMenuCode("settings.unsafe");
        unsafeManagementPage.setName("Unsafe settings page");
        unsafeManagementPage.setPath("/settings/unsafe");
        unsafeManagementPage.setComponent("@/pages/settings/Unsafe");
        unsafeManagementPage.setSortNo(1);
        unsafeManagementPage.setChildren(List.of());

        MenuNodeDTO permissionlessRegistrationPage = new MenuNodeDTO();
        permissionlessRegistrationPage.setMenuCode("competition.registration");
        permissionlessRegistrationPage.setName("Competition registration");
        permissionlessRegistrationPage.setPath("/competitions/register");
        permissionlessRegistrationPage.setComponent("@/pages/competition");
        permissionlessRegistrationPage.setSortNo(2);
        permissionlessRegistrationPage.setChildren(List.of());

        MenuNodeDTO personalCertificatePage = new MenuNodeDTO();
        personalCertificatePage.setMenuCode("certificate.mine");
        personalCertificatePage.setName("My certificates");
        personalCertificatePage.setPath("/certificates/mine");
        personalCertificatePage.setComponent("@/pages/certificates/MyCertificatesPage");
        personalCertificatePage.setSortNo(3);
        personalCertificatePage.setChildren(List.of());

        MenuNodeDTO expertApplicationPage = new MenuNodeDTO();
        expertApplicationPage.setMenuCode("expert.application");
        expertApplicationPage.setName("Expert application");
        expertApplicationPage.setPath("/competitions/expert-apply");
        expertApplicationPage.setComponent("@/pages/competition");
        expertApplicationPage.setSortNo(4);
        expertApplicationPage.setChildren(List.of());

        settingsRoot.setChildren(List.of(unsafeManagementPage));

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(
                settingsRoot,
                permissionlessRegistrationPage,
                personalCertificatePage,
                expertApplicationPage
        ));

        Map<String, Object> bootstrap = pluginManagementAppService.currentBootstrap(List.of(), "wechat-common-user");

        assertThat(collectMenuCodes((List<Map<String, Object>>) bootstrap.get("menuTree")))
                .contains("certificate.mine", "expert.application")
                .doesNotContain("settings.root", "settings.unsafe", "competition.registration");
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldReturnRoleScopedPagesAfterPermissionAdjustment() throws Exception {
        MenuNodeDTO settingsRoot = new MenuNodeDTO();
        settingsRoot.setMenuCode("settings.root");
        settingsRoot.setName("Settings");
        settingsRoot.setPath("/settings");
        settingsRoot.setComponent("@/layouts/SettingsLayout");
        settingsRoot.setPermissionKey("system:view");
        settingsRoot.setSortNo(1);

        MenuNodeDTO menuManagementPage = new MenuNodeDTO();
        menuManagementPage.setMenuCode("settings.menus");
        menuManagementPage.setName("Menus");
        menuManagementPage.setPath("/settings/menus");
        menuManagementPage.setComponent("@/pages/settings/menus");
        menuManagementPage.setPermissionKey("system:menu:view");
        menuManagementPage.setSortNo(1);
        menuManagementPage.setChildren(List.of());

        MenuNodeDTO dictionaryPage = new MenuNodeDTO();
        dictionaryPage.setMenuCode("settings.dicts");
        dictionaryPage.setName("Dicts");
        dictionaryPage.setPath("/settings/dicts");
        dictionaryPage.setComponent("@/pages/settings/dicts");
        dictionaryPage.setPermissionKey("system:dict:view");
        dictionaryPage.setSortNo(2);
        dictionaryPage.setChildren(List.of());

        settingsRoot.setChildren(List.of(menuManagementPage, dictionaryPage));

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L, 10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L, 20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(settingsRoot));

        Map<String, Object> beforeAdjustment = pluginManagementAppService.currentBootstrap(
                List.of("system:menu:view"),
                "role-menu-v1"
        );
        Map<String, Object> afterAdjustment = pluginManagementAppService.currentBootstrap(
                List.of("system:dict:view"),
                "role-dict-v2"
        );

        assertThat(collectMenuCodes((List<Map<String, Object>>) beforeAdjustment.get("menuTree")))
                .contains("settings.root", "settings.menus")
                .doesNotContain("settings.dicts");
        assertThat(collectMenuCodes((List<Map<String, Object>>) afterAdjustment.get("menuTree")))
                .contains("settings.root", "settings.dicts")
                .doesNotContain("settings.menus");
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldNotReuseVisibleMenusForDifferentPermissionsWithSameSnapshotVersion() throws Exception {
        MenuNodeDTO settingsRoot = new MenuNodeDTO();
        settingsRoot.setMenuCode("settings.root");
        settingsRoot.setName("Settings");
        settingsRoot.setPath("/settings");
        settingsRoot.setComponent("@/layouts/SettingsLayout");
        settingsRoot.setPermissionKey("system:view");
        settingsRoot.setSortNo(1);

        MenuNodeDTO menuManagementPage = new MenuNodeDTO();
        menuManagementPage.setMenuCode("settings.menus");
        menuManagementPage.setName("Menus");
        menuManagementPage.setPath("/settings/menus");
        menuManagementPage.setComponent("@/pages/settings/menus");
        menuManagementPage.setPermissionKey("system:menu:view");
        menuManagementPage.setSortNo(1);
        menuManagementPage.setChildren(List.of());

        MenuNodeDTO dictionaryPage = new MenuNodeDTO();
        dictionaryPage.setMenuCode("settings.dicts");
        dictionaryPage.setName("Dicts");
        dictionaryPage.setPath("/settings/dicts");
        dictionaryPage.setComponent("@/pages/settings/dicts");
        dictionaryPage.setPermissionKey("system:dict:view");
        dictionaryPage.setSortNo(2);
        dictionaryPage.setChildren(List.of());

        settingsRoot.setChildren(List.of(menuManagementPage, dictionaryPage));

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(settingsRoot));

        Map<String, Object> menuBootstrap = pluginManagementAppService.currentBootstrap(
                List.of("system:menu:view"),
                "v7:data-scope-cache-v4"
        );
        Map<String, Object> dictBootstrap = pluginManagementAppService.currentBootstrap(
                List.of("system:dict:view"),
                "v7:data-scope-cache-v4"
        );

        assertThat(collectMenuCodes((List<Map<String, Object>>) menuBootstrap.get("menuTree")))
                .contains("settings.root", "settings.menus")
                .doesNotContain("settings.dicts");
        assertThat(collectMenuCodes((List<Map<String, Object>>) dictBootstrap.get("menuTree")))
                .contains("settings.root", "settings.dicts")
                .doesNotContain("settings.menus");
        verify(systemInternalApi, times(1)).builtinMenus();
    }

    @Test
    void currentAvailablePlugins_shouldFilterMenusByPermissionSnapshot() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);
        PluginMenuRelEntity menuRelation = createMenuRelation(availablePluginCode, "1.0.0", "plugin.sms");

        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of(menuRelation));

        List<PluginVO.PluginAvailabilityVO> hidden = pluginManagementAppService.currentAvailablePlugins(List.of());
        List<PluginVO.PluginAvailabilityVO> visible = pluginManagementAppService.currentAvailablePlugins(List.of("plugin:sms:view"));

        assertThat(hidden).hasSize(1);
        assertThat(hidden.get(0).getMenus()).isEmpty();
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getMenus()).extracting(menu -> menu.get("menuCode")).containsExactly("plugin.sms");
    }

    @Test
    void currentBootstrap_shouldReuseSuppliedReadModelVersionsWithoutVersionRoundTrips() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);
        PluginMenuRelEntity menuRelation = createMenuRelation(availablePluginCode, "1.0.0", "plugin.sms");

        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(java.util.Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of(menuRelation));

        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("Dashboard");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setPermissionKey("dashboard:view");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));

        Map<String, Object> bootstrap = pluginManagementAppService.currentBootstrap(
                List.of("plugin:sms:view"),
                "v10:data-scope-cache-v4",
                10L,
                20L
        );

        assertThat(bootstrap).containsKeys("menuTree", "availablePlugins");
        verify(systemInternalApi, never()).readModelVersion("plugin", "bootstrap");
        verify(systemInternalApi, never()).readModelVersion("platform", "menu-tree");
        verify(systemInternalApi, times(1)).builtinMenus();
        verify(pluginPersistenceService, times(1)).listAvailablePlugins();
        verify(pluginPersistenceService, times(1)).listMenuRelations(availablePluginCode, "1.0.0");
    }

    @Test
    void currentBootstrap_shouldSingleFlightConcurrentWarmMissesPerPermissionVersion() throws Exception {
        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("Dashboard");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L);
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());

        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        @SuppressWarnings("unchecked")
        CompletableFuture<Map<String, Object>>[] futures = new CompletableFuture[threadCount];
        for (int index = 0; index < threadCount; index++) {
            futures[index] = CompletableFuture.supplyAsync(() -> {
                try {
                    ready.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return pluginManagementAppService.currentBootstrap(
                        List.of("dashboard:view"),
                        "v10:data-scope-cache-v4"
                );
            }, executor);
        }

        ready.countDown();
        CompletableFuture.allOf(futures).join();
        executor.shutdown();
        assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();

        for (CompletableFuture<Map<String, Object>> future : futures) {
            assertThat(future.join()).containsKeys("menuTree", "availablePlugins");
        }

        verify(systemInternalApi, times(1)).builtinMenus();
        verify(pluginPersistenceService, times(1)).listAvailablePlugins();
    }

    @Test
    void currentBootstrap_shouldReuseCompiledMenusForDifferentPermissionSignature() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);
        PluginMenuRelEntity menuRelation = createMenuRelation(availablePluginCode, "1.0.0", "plugin.sms");

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(java.util.Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of(menuRelation));

        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("Dashboard");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());
        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));

        pluginManagementAppService.currentBootstrap(List.of("plugin:sms:view"));
        pluginManagementAppService.currentBootstrap(List.of("*"));

        verify(systemInternalApi, times(1)).readModelVersion("plugin", "bootstrap");
        verify(systemInternalApi, times(1)).readModelVersion("platform", "menu-tree");
        verify(systemInternalApi, times(1)).builtinMenus();
        verify(pluginPersistenceService, times(1)).listAvailablePlugins();
        verify(pluginPersistenceService, times(1)).listMenuRelations(availablePluginCode, "1.0.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldReloadBuiltinMenusWhenReadModelVersionChanges() throws Exception {
        MenuNodeDTO dashboardMenu = new MenuNodeDTO();
        dashboardMenu.setMenuCode("system.dashboard");
        dashboardMenu.setName("Dashboard");
        dashboardMenu.setPath("/dashboard");
        dashboardMenu.setSortNo(1);
        dashboardMenu.setChildren(List.of());

        MenuNodeDTO projectManagementMenu = new MenuNodeDTO();
        projectManagementMenu.setMenuCode("project.management");
        projectManagementMenu.setName("Project Management");
        projectManagementMenu.setPath("/projects/management");
        projectManagementMenu.setPermissionKey("aiadc:project:view");
        projectManagementMenu.setSortNo(1);
        projectManagementMenu.setChildren(List.of());

        MenuNodeDTO projectRootMenu = new MenuNodeDTO();
        projectRootMenu.setMenuCode("project.root");
        projectRootMenu.setName("Projects");
        projectRootMenu.setPath("/projects");
        projectRootMenu.setSortNo(5);
        projectRootMenu.setChildren(List.of(projectManagementMenu));

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L, 11L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L, 20L);
        when(systemInternalApi.builtinMenus())
                .thenReturn(List.of(dashboardMenu))
                .thenReturn(List.of(dashboardMenu, projectRootMenu));
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());

        Map<String, Object> firstBootstrap = pluginManagementAppService.currentBootstrap(List.of("aiadc:project:view"));
        expireReadModelVersionCache();
        Map<String, Object> secondBootstrap = pluginManagementAppService.currentBootstrap(List.of("aiadc:project:view"));

        assertThat(collectMenuCodes((List<Map<String, Object>>) firstBootstrap.get("menuTree")))
                .doesNotContain("project.management");
        assertThat(collectMenuCodes((List<Map<String, Object>>) secondBootstrap.get("menuTree")))
                .contains("project.root", "project.management");
        verify(systemInternalApi, times(2)).builtinMenus();
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBootstrap_shouldReloadBuiltinMenusWhenPlatformMenuTreeVersionChanges() throws Exception {
        MenuNodeDTO dashboardMenu = new MenuNodeDTO();
        dashboardMenu.setMenuCode("system.dashboard");
        dashboardMenu.setName("Dashboard");
        dashboardMenu.setPath("/dashboard");
        dashboardMenu.setSortNo(1);
        dashboardMenu.setChildren(List.of());

        MenuNodeDTO projectManagementMenu = new MenuNodeDTO();
        projectManagementMenu.setMenuCode("project.management");
        projectManagementMenu.setName("Project Management");
        projectManagementMenu.setPath("/projects/management");
        projectManagementMenu.setPermissionKey("aiadc:project:view");
        projectManagementMenu.setSortNo(1);
        projectManagementMenu.setChildren(List.of());

        MenuNodeDTO projectRootMenu = new MenuNodeDTO();
        projectRootMenu.setMenuCode("project.root");
        projectRootMenu.setName("Projects");
        projectRootMenu.setPath("/projects");
        projectRootMenu.setSortNo(5);
        projectRootMenu.setChildren(List.of(projectManagementMenu));

        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(10L, 10L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(20L, 21L);
        when(systemInternalApi.builtinMenus())
                .thenReturn(List.of(dashboardMenu))
                .thenReturn(List.of(dashboardMenu, projectRootMenu));
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of());

        Map<String, Object> firstBootstrap = pluginManagementAppService.currentBootstrap(List.of("aiadc:project:view"));
        expireReadModelVersionCache();
        Map<String, Object> secondBootstrap = pluginManagementAppService.currentBootstrap(List.of("aiadc:project:view"));

        assertThat(collectMenuCodes((List<Map<String, Object>>) firstBootstrap.get("menuTree")))
                .doesNotContain("project.management");
        assertThat(collectMenuCodes((List<Map<String, Object>>) secondBootstrap.get("menuTree")))
                .contains("project.root", "project.management");
        verify(systemInternalApi, times(2)).builtinMenus();
        verify(systemInternalApi, times(2)).readModelVersion("plugin", "bootstrap");
        verify(systemInternalApi, times(2)).readModelVersion("platform", "menu-tree");
    }

    @Test
    void availablePlugins_shouldRefreshWhenReadModelVersionChanges() throws Exception {
        String availablePluginCode = "sms";
        PluginVO.PluginAvailabilityVO availablePlugin = createValidPluginAvailability(availablePluginCode, tempDir);
        PluginVersionEntity versionEntity = createValidVersionEntity(availablePluginCode, tempDir);

        when(systemInternalApi.readModelVersion("plugin", "bootstrap"))
                .thenReturn(1L, 2L);
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus(availablePluginCode)).thenReturn(java.util.Optional.empty());
        when(pluginPersistenceService.findVersion(availablePluginCode, "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations(availablePluginCode, "1.0.0")).thenReturn(List.of());

        pluginManagementAppService.availablePlugins();
        expireReadModelVersionCache();
        pluginManagementAppService.availablePlugins();

        verify(systemInternalApi, atLeast(2)).readModelVersion("plugin", "bootstrap");
        verify(pluginPersistenceService, times(2)).listAvailablePlugins();
    }

    @Test
    void availablePlugins_shouldExposeBuiltinWorkOrderFeedbackWithoutRuntimeAssets() {
        PluginVO.PluginAvailabilityVO availablePlugin = new PluginVO.PluginAvailabilityVO();
        availablePlugin.setPluginCode("work-order-feedback");
        availablePlugin.setPluginName("Work Order Feedback");
        availablePlugin.setVersion("1.0.0");
        availablePlugin.setSharedDeps(new ArrayList<>());
        availablePlugin.setRoutes(new ArrayList<>());
        availablePlugin.setMenus(new ArrayList<>());

        PluginMenuRelEntity menuRelation = createMenuRelation("work-order-feedback", "1.0.0", "plugin.work-order-feedback");

        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.pluginStatus("work-order-feedback")).thenReturn(Optional.empty());
        when(pluginPersistenceService.listMenuRelations("work-order-feedback", "1.0.0")).thenReturn(List.of(menuRelation));

        List<PluginVO.PluginAvailabilityVO> plugins = pluginManagementAppService.availablePlugins();

        assertThat(plugins).hasSize(1);
        PluginVO.PluginAvailabilityVO plugin = plugins.get(0);
        assertThat(plugin.getPluginCode()).isEqualTo("work-order-feedback");
        assertThat(plugin.getRoutes()).containsExactly("/work-order-feedback");
        assertThat(plugin.getRuntimeContributions()).contains("routes", "menus", "permissions", "rich-text-upload");
        assertThat(plugin.getMenus()).hasSize(1);
        verify(pluginPersistenceService, never()).findVersion(eq("work-order-feedback"), any());
    }

    @Test
    void availablePlugins_shouldSkipRuntimeEntriesWithMissingAssets() throws Exception {
        Path versionHome = tempDir.resolve("sms").resolve("1.0.0");
        Files.createDirectories(versionHome.resolve("lumira-ui"));
        Path manifest = versionHome.resolve("lumira-ui/manifest.json");
        Files.writeString(manifest, """
                {
                  "pluginCode": "sms",
                  "version": "1.0.0",
                  "entry": "index.js",
                  "assets": ["index.js", "missing.js"],
                  "routes": ["/plugins/sms"],
                  "sharedDeps": ["react"]
                }
                """);

        PluginVO.PluginAvailabilityVO availablePlugin = new PluginVO.PluginAvailabilityVO();
        availablePlugin.setPluginCode("sms");
        availablePlugin.setPluginName("SMS Plugin");
        availablePlugin.setVersion("1.0.0");
        availablePlugin.setManifestPath(manifest.toString());
        availablePlugin.setSharedDeps(new ArrayList<>());
        availablePlugin.setRoutes(new ArrayList<>());
        availablePlugin.setMenus(new ArrayList<>());

        PluginVersionEntity versionEntity = new PluginVersionEntity();
        versionEntity.setPluginCode("sms");
        versionEntity.setVersion("1.0.0");
        versionEntity.setArtifactPath(versionHome.toString());
        versionEntity.setFrontendManifestPath(manifest.toString());
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.findVersion("sms", "1.0.0")).thenReturn(Optional.of(versionEntity));

        Logger logger = (Logger) LoggerFactory.getLogger(PluginManagementAppService.class);
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            List<PluginVO.PluginAvailabilityVO> plugins = pluginManagementAppService.availablePlugins();

            assertThat(plugins).isEmpty();
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("Skipping plugin sms 1.0.0 because runtime files are invalid");
                    });
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditive);
        }
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(
                100L,
                "alice",
                "session-1",
                3,
                true,
                Set.of(
                        "plugin:management:view",
                        "plugin:management:upload",
                        "plugin:management:install",
                        "plugin:management:upgrade",
                        "plugin:management:rollback",
                        "plugin:management:enable",
                        "plugin:management:disable"
                )
        );
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(String version) {
        return permissionSnapshot(
                version,
                "plugin:management:view",
                "plugin:management:upload",
                "plugin:management:install",
                "plugin:management:upgrade",
                "plugin:management:rollback",
                "plugin:management:enable",
                "plugin:management:disable"
        );
    }

    private PermissionSnapshotDTO permissionSnapshot(String version, String... permissions) {
        return new PermissionSnapshotDTO(
                version,
                List.of(permissions),
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/plugins"
        );
    }

    private PluginVO.PluginAvailabilityVO createValidPluginAvailability(String pluginCode, Path baseDir) throws Exception {
        Path versionHome = baseDir.resolve(pluginCode).resolve("1.0.0");
        Files.createDirectories(versionHome.resolve("lumira-ui"));
        Path manifest = versionHome.resolve("lumira-ui/manifest.json");
        Files.writeString(manifest, """
                {
                  "pluginCode": "%s",
                  "version": "1.0.0",
                  "entry": "index.js",
                  "assets": ["index.js"],
                  "routes": ["/plugins/%s"],
                  "sharedDeps": ["react"]
                }
                """.formatted(pluginCode, pluginCode));
        Files.writeString(versionHome.resolve("lumira-ui/index.js"), "console.log('ok');");

        PluginVO.PluginAvailabilityVO availablePlugin = new PluginVO.PluginAvailabilityVO();
        availablePlugin.setPluginCode(pluginCode);
        availablePlugin.setPluginName("SMS Plugin");
        availablePlugin.setVersion("1.0.0");
        availablePlugin.setManifestPath(manifest.toString());
        availablePlugin.setSharedDeps(new ArrayList<>());
        availablePlugin.setRoutes(new ArrayList<>());
        availablePlugin.setMenus(new ArrayList<>());
        return availablePlugin;
    }

    private PluginVersionEntity createValidVersionEntity(String pluginCode, Path baseDir) throws Exception {
        Path versionHome = baseDir.resolve(pluginCode).resolve("1.0.0");
        PluginVersionEntity versionEntity = new PluginVersionEntity();
        versionEntity.setPluginCode(pluginCode);
        versionEntity.setVersion("1.0.0");
        versionEntity.setArtifactPath(versionHome.toString());
        versionEntity.setFrontendManifestPath(versionHome.resolve("lumira-ui/manifest.json").toString());
        return versionEntity;
    }

    private PluginMenuRelEntity createMenuRelation(String pluginCode, String version, String menuCode) {
        PluginMenuRelEntity menuRelation = new PluginMenuRelEntity();
        menuRelation.setPluginCode(pluginCode);
        menuRelation.setPluginVersion(version);
        menuRelation.setMenuCode(menuCode);
        menuRelation.setMenuName("SMS Plugin");
        menuRelation.setRoutePath("/plugins/" + pluginCode);
        menuRelation.setIcon("MessageOutlined");
        menuRelation.setPermissionKey("plugin:sms:view");
        menuRelation.setParentMenuCode(null);
        menuRelation.setSortNo(10);
        return menuRelation;
    }

    @SuppressWarnings("unchecked")
    private List<String> collectMenuCodes(List<Map<String, Object>> menus) {
        List<String> codes = new ArrayList<>();
        for (Map<String, Object> menu : menus) {
            codes.add((String) menu.get("menuCode"));
            Object children = menu.get("children");
            if (children instanceof List<?> childList) {
                codes.addAll(collectMenuCodes((List<Map<String, Object>>) childList));
            }
        }
        return codes;
    }

    @SuppressWarnings("unchecked")
    private void expireReadModelVersionCache() throws Exception {
        java.lang.reflect.Field field = PluginManagementAppService.class.getDeclaredField("readModelVersionCache");
        field.setAccessible(true);
        var cache = (com.lumira.common.runtime.ReadModelVersionCache) field.get(pluginManagementAppService);
        cache.clear();
    }
}
