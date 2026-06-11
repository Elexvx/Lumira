package com.lumira.saas.modules.plugin.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
                new ObjectMapper()
        );
    }

    @Test
    void enable_shouldInvalidatePermissionSnapshotAfterTenantBinding() {
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
        doNothing().when(pluginPersistenceService).enablePluginForTenant(1001L, "sms", "1.0.0", "{\"level\":\"basic\"}", 100L);
        doNothing().when(pluginPersistenceService).registerTenantPermissions(1001L, "sms", "1.0.0");

        PluginDTO.EnableRequest request = new PluginDTO.EnableRequest();
        request.setTenantId(1001L);
        request.setPluginCode("sms");
        request.setVersion("1.0.0");
        request.setConfigJson("{\"level\":\"basic\"}");

        pluginManagementAppService.enable(request, currentUser());

        verify(pluginPersistenceService).enablePluginForTenant(1001L, "sms", "1.0.0", "{\"level\":\"basic\"}", 100L);
        verify(pluginPersistenceService).registerTenantPermissions(1001L, "sms", "1.0.0");
        verify(systemInternalApi).invalidatePermissionSnapshot(1001L);
        verify(pluginMigrationService).executeUpMigrations("sms", "1.0.0", null, 100L);
    }

    @Test
    void disable_shouldPurgeSchemaWhenRequested() {
        PluginTenantEntity tenantEntity = new PluginTenantEntity();
        tenantEntity.setTenantId(1001L);
        tenantEntity.setPluginCode("sms");
        tenantEntity.setPluginVersion("1.0.0");
        tenantEntity.setEnabled(1);

        when(pluginPersistenceService.findTenantPlugin(1001L, "sms")).thenReturn(Optional.of(tenantEntity));

        PluginDTO.DisableRequest request = new PluginDTO.DisableRequest();
        request.setTenantId(1001L);
        request.setPluginCode("sms");
        request.setPurgeData(true);

        pluginManagementAppService.disable(request, currentUser());

        verify(pluginPersistenceService).disablePluginForTenant(1001L, "sms", 100L);
        verify(pluginMigrationService).executeDownMigrations("sms", "1.0.0", null, 100L);
        verify(systemInternalApi).invalidatePermissionSnapshot(1001L);
    }

    @Test
    void currentMenus_shouldMergeBuiltinMenusAndPluginMenus() throws Exception {
        Path manifest = tempDir.resolve("manifest.json");
        Path versionHome = tempDir.resolve("sms").resolve("1.0.0");
        Files.createDirectories(versionHome.resolve("frontend"));
        Files.writeString(versionHome.resolve("frontend/index.js"), "console.log('sms');");
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

        PluginVO.TenantPluginVO tenantPlugin = new PluginVO.TenantPluginVO();
        tenantPlugin.setPluginCode("sms");
        tenantPlugin.setPluginName("短信插件");
        tenantPlugin.setVersion("1.0.0");
        tenantPlugin.setManifestPath(manifest.toString());
        tenantPlugin.setSharedDeps(new ArrayList<>());
        tenantPlugin.setRoutes(new ArrayList<>());
        tenantPlugin.setMenus(new ArrayList<>());

        PluginVersionEntity versionEntity = new PluginVersionEntity();
        versionEntity.setPluginCode("sms");
        versionEntity.setVersion("1.0.0");
        versionEntity.setArtifactPath(versionHome.toString());
        versionEntity.setFrontendManifestPath(manifest.toString());

        PluginMenuRelEntity menuRelation = new PluginMenuRelEntity();
        menuRelation.setPluginCode("sms");
        menuRelation.setPluginVersion("1.0.0");
        menuRelation.setMenuCode("plugin.sms");
        menuRelation.setMenuName("短信插件");
        menuRelation.setRoutePath("/plugins/sms");
        menuRelation.setIcon("MessageOutlined");
        menuRelation.setPermissionKey("plugin:sms:view");
        menuRelation.setParentMenuCode(null);
        menuRelation.setSortNo(10);

        MenuNodeDTO builtinMenu = new MenuNodeDTO();
        builtinMenu.setMenuCode("system.dashboard");
        builtinMenu.setName("系统首页");
        builtinMenu.setPath("/dashboard");
        builtinMenu.setSortNo(1);
        builtinMenu.setChildren(List.of());

        when(systemInternalApi.builtinMenus()).thenReturn(List.of(builtinMenu));
        when(pluginPersistenceService.listTenantPlugins(1001L)).thenReturn(List.of(tenantPlugin));
        when(pluginPersistenceService.findVersion("sms", "1.0.0")).thenReturn(Optional.of(versionEntity));
        when(pluginPersistenceService.listMenuRelations("sms", "1.0.0")).thenReturn(List.of(menuRelation));

        List<Map<String, Object>> menus = pluginManagementAppService.currentMenus(1001L, List.of("plugin:sms:view"));

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
    void availablePlugins_shouldSkipRuntimeEntriesWithMissingAssets() throws Exception {
        Path versionHome = tempDir.resolve("sms").resolve("1.0.0");
        Files.createDirectories(versionHome.resolve("frontend"));
        Path manifest = versionHome.resolve("frontend/manifest.json");
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

        PluginVO.TenantPluginVO tenantPlugin = new PluginVO.TenantPluginVO();
        tenantPlugin.setPluginCode("sms");
        tenantPlugin.setPluginName("短信插件");
        tenantPlugin.setVersion("1.0.0");
        tenantPlugin.setManifestPath(manifest.toString());
        tenantPlugin.setSharedDeps(new ArrayList<>());
        tenantPlugin.setRoutes(new ArrayList<>());
        tenantPlugin.setMenus(new ArrayList<>());

        PluginVersionEntity versionEntity = new PluginVersionEntity();
        versionEntity.setPluginCode("sms");
        versionEntity.setVersion("1.0.0");
        versionEntity.setArtifactPath(versionHome.toString());
        versionEntity.setFrontendManifestPath(manifest.toString());
        when(pluginPersistenceService.listTenantPlugins(1001L)).thenReturn(List.of(tenantPlugin));
        when(pluginPersistenceService.findVersion("sms", "1.0.0")).thenReturn(Optional.of(versionEntity));

        Logger logger = (Logger) LoggerFactory.getLogger(PluginManagementAppService.class);
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            List<PluginVO.TenantPluginVO> plugins = pluginManagementAppService.availablePlugins(1001L);

            assertThat(plugins).isEmpty();
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("Skipping plugin sms 1.0.0 for tenant 1001");
                    });
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditive);
        }
    }

    private CurrentUser currentUser() {
        return new CurrentUser(100L, "alice", 1001L, "session-1", 3, true, Set.of("plugin:management:enable"));
    }
}
