package com.lumira.saas.modules.plugin.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.lumira.saas.modules.plugin.loader.PluginArtifactLoader;
import com.lumira.saas.modules.plugin.loader.PluginRuntimeLoader;
import com.lumira.saas.modules.plugin.registry.PluginRegistry;
import com.lumira.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.lumira.saas.modules.plugin.service.PluginMigrationService;
import com.lumira.saas.modules.plugin.service.PluginPersistenceService;
import com.lumira.saas.modules.plugin.service.PluginSemver;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        doNothing().when(pluginPersistenceService).enablePlugin("sms", "1.0.0", "{\"level\":\"basic\"}", 100L);
        doNothing().when(pluginPersistenceService).registerPluginPermissions("sms", "1.0.0");

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{\"level\":\"basic\"}");

        pluginManagementAppService.enable(request, currentUser());

        verify(pluginPersistenceService).enablePlugin("sms", "1.0.0", "{\"level\":\"basic\"}", 100L);
        verify(pluginPersistenceService).registerPluginPermissions("sms", "1.0.0");
        verify(pluginPersistenceService).bumpBootstrapVersion("plugin.enabled");
        verify(pluginMigrationService).executeUpMigrations("sms", "1.0.0", null, 100L);
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

        verify(pluginPersistenceService).disablePlugin("sms", 100L);
        verify(pluginPersistenceService).bumpBootstrapVersion("plugin.disabled");
        verify(pluginMigrationService).executeDownMigrations("sms", "1.0.0", null, 100L);
        verify(systemInternalApi).invalidatePermissionSnapshot();
        verify(domainEventPublisher).publishAll(any());
    }

    @Test
    void disable_shouldRejectPurgeWhenPluginDoesNotSupportDataPurge() {
        PluginVersionEntity enabledVersion = new PluginVersionEntity();
        enabledVersion.setPluginCode("sensitive-words");
        enabledVersion.setVersion("1.0.0");

        PluginVO.PluginStatusVO pluginStatus = new PluginVO.PluginStatusVO();
        pluginStatus.setSupportsDataPurge(false);
        when(pluginPersistenceService.findEnabledVersion("sensitive-words")).thenReturn(Optional.of(enabledVersion));
        when(pluginPersistenceService.pluginStatus("sensitive-words")).thenReturn(Optional.of(pluginStatus));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setPluginCode("sensitive-words");
        request.setPurgeData(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pluginManagementAppService.disable(request, currentUser()))
                .hasMessageContaining("does not support data purge");

        verify(pluginPersistenceService, never()).disablePlugin(any(), any());
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
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());

        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));
        when(pluginPersistenceService.listAvailablePlugins()).thenReturn(List.of(availablePlugin));
        when(pluginPersistenceService.findVersion("sms", "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations("sms", "1.0.0")).thenReturn(List.of(menuRelation));

        List<Map<String, Object>> menus = pluginManagementAppService.currentMenus(List.of("plugin:sms:view"));

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
        assertThat(plugin.getRoutes()).containsExactly("/plugins/work-order-feedback");
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
        return new CurrentUser(100L, "alice", 1001L, "session-1", 3, true, Set.of("plugin:management:enable"));
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
