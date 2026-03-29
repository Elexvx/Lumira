package com.yourcompany.saas.modules.plugin.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.yourcompany.saas.modules.plugin.dto.PluginDTO;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.yourcompany.saas.modules.plugin.loader.PluginArtifactLoader;
import com.yourcompany.saas.modules.plugin.loader.PluginRuntimeLoader;
import com.yourcompany.saas.modules.plugin.registry.PluginRegistry;
import com.yourcompany.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.yourcompany.saas.modules.plugin.service.PluginMigrationService;
import com.yourcompany.saas.modules.plugin.service.PluginPersistenceService;
import com.yourcompany.saas.modules.plugin.service.PluginSemver;
import com.yourcompany.saas.modules.plugin.vo.PluginVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PluginManagementAppService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final PluginArtifactLoader pluginArtifactLoader;
    private final PluginPersistenceService pluginPersistenceService;
    private final PluginMigrationService pluginMigrationService;
    private final PluginRuntimeLoader pluginRuntimeLoader;
    private final PluginRegistry pluginRegistry;
    private final PluginSemver pluginSemver;
    private final PermissionSnapshotService permissionSnapshotService;
    private final ObjectMapper objectMapper;

    public PluginManagementAppService(
            PluginArtifactLoader pluginArtifactLoader,
            PluginPersistenceService pluginPersistenceService,
            PluginMigrationService pluginMigrationService,
            PluginRuntimeLoader pluginRuntimeLoader,
            PluginRegistry pluginRegistry,
            PluginSemver pluginSemver,
            PermissionSnapshotService permissionSnapshotService,
            ObjectMapper objectMapper
    ) {
        this.pluginArtifactLoader = pluginArtifactLoader;
        this.pluginPersistenceService = pluginPersistenceService;
        this.pluginMigrationService = pluginMigrationService;
        this.pluginRuntimeLoader = pluginRuntimeLoader;
        this.pluginRegistry = pluginRegistry;
        this.pluginSemver = pluginSemver;
        this.permissionSnapshotService = permissionSnapshotService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PluginVO.PluginUploadVO upload(MultipartFile file, CurrentUser currentUser) {
        PluginArtifactLoader.UploadedArtifact artifact = pluginArtifactLoader.stage(file);
        PluginVersionEntity versionEntity = pluginPersistenceService.saveUploadedPackage(
                artifact.metadata(),
                artifact.zipPath(),
                artifact.validationReportJson(),
                artifact.packageChecksum(),
                artifact.signaturePath().toString(),
                currentUser.getUserId()
        );
        pluginPersistenceService.replaceDependencies(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getDependencyPlugins(),
                currentUser.getUserId()
        );
        pluginPersistenceService.replacePermissionRelations(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getVersion(),
                artifact.metadata().getRequiredPermissions(),
                currentUser.getUserId()
        );
        pluginPersistenceService.replaceMenuRelations(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getVersion(),
                artifact.metadata().getMenuDeclarations(),
                currentUser.getUserId()
        );
        log(null, artifact.metadata().getPluginCode(), artifact.metadata().getVersion(), "UPLOAD", "VERIFIED", "SUCCESS", "插件包已上传并完成校验", null, currentUser.getUserId());
        PluginVO.PluginUploadVO vo = new PluginVO.PluginUploadVO();
        vo.setPluginCode(versionEntity.getPluginCode());
        vo.setPluginName(artifact.metadata().getPluginName());
        vo.setVersion(versionEntity.getVersion());
        vo.setInstallStatus(versionEntity.getInstallStatus());
        vo.setValidationReportJson(versionEntity.getValidationReportJson());
        return vo;
    }

    @Transactional
    public PluginVO.PluginVersionVO install(String pluginCode, String version, CurrentUser currentUser) {
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        PluginDTO.PluginPackageMetadata metadata = parseMetadata(versionEntity);
        validateDependencies(metadata);
        Path versionHome = pluginArtifactLoader.installToVersionHome(pluginCode, version, Path.of(versionEntity.getStagedPath()));
        log(null, pluginCode, version, "INSTALL", "INSTALLED", "SUCCESS", "插件文件已落盘", null, currentUser.getUserId());
        pluginMigrationService.executeMigrations(versionHome);
        log(null, pluginCode, version, "INSTALL", "MIGRATED", "SUCCESS", "插件私有迁移已完成", null, currentUser.getUserId());
        PluginRuntimeDescriptor descriptor = pluginRuntimeLoader.load(metadata, versionHome);
        pluginRegistry.register(descriptor);
        pluginPersistenceService.markInstalled(
                pluginCode,
                version,
                versionHome,
                versionHome.resolve("frontend/manifest.json"),
                versionHome.resolve("backend/plugin.jar"),
                "LOADED",
                "LOADED",
                descriptor.getHealthIndicator() == null ? "HEALTHY" : "HEALTHY",
                1
        );
        if (pluginPersistenceService.listInstalledVersions(pluginCode).stream().noneMatch(item -> item.getIsActive() != null && item.getIsActive() == 1)) {
            pluginRegistry.activate(pluginCode, version);
            pluginPersistenceService.activateVersion(pluginCode, version);
        }
        log(null, pluginCode, version, "INSTALL", "LOADED", "SUCCESS", "插件后端运行时已加载", null, currentUser.getUserId());
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> version.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PluginVO.PluginVersionVO upgrade(String pluginCode, String version, CurrentUser currentUser) {
        ensureLoaded(pluginCode, version);
        pluginRegistry.activate(pluginCode, version);
        pluginPersistenceService.activateVersion(pluginCode, version);
        log(null, pluginCode, version, "UPGRADE", "ENABLED", "SUCCESS", "插件激活版本已切换", null, currentUser.getUserId());
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> version.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PluginVO.PluginVersionVO rollback(String pluginCode, String targetVersion, CurrentUser currentUser) {
        ensureLoaded(pluginCode, targetVersion);
        pluginRegistry.activate(pluginCode, targetVersion);
        pluginPersistenceService.activateVersion(pluginCode, targetVersion);
        log(null, pluginCode, targetVersion, "ROLLBACK", "ROLLED_BACK", "SUCCESS", "插件已回滚到目标版本", null, currentUser.getUserId());
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> targetVersion.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void enable(PluginDTO.EnableRequest request, CurrentUser currentUser) {
        String version = request.getVersion();
        if (version == null || version.isBlank()) {
            version = pluginRegistry.findActiveVersion(request.getPluginCode())
                    .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "当前插件不存在激活版本"));
        }
        ensureLoaded(request.getPluginCode(), version);
        pluginPersistenceService.enablePluginForTenant(
                request.getTenantId(),
                request.getPluginCode(),
                version,
                request.getConfigJson(),
                currentUser.getUserId()
        );
        pluginPersistenceService.registerTenantPermissions(request.getTenantId(), request.getPluginCode(), version);
        permissionSnapshotService.invalidateTenant(request.getTenantId());
        log(request.getTenantId(), request.getPluginCode(), version, "ENABLE", "ENABLED", "SUCCESS", "租户插件已启用", null, currentUser.getUserId());
    }

    @Transactional
    public void disable(PluginDTO.DisableRequest request, CurrentUser currentUser) {
        PluginTenantEntity tenantEntity = pluginPersistenceService.findTenantPlugin(request.getTenantId(), request.getPluginCode())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "当前租户尚未启用该插件"));
        pluginPersistenceService.disablePluginForTenant(request.getTenantId(), request.getPluginCode(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(request.getTenantId());
        log(request.getTenantId(), request.getPluginCode(), tenantEntity.getPluginVersion(), "DISABLE", "DISABLED", "SUCCESS", "租户插件已停用", null, currentUser.getUserId());
    }

    public List<PluginVO.PluginDefinitionVO> listDefinitions() {
        return pluginPersistenceService.listDefinitions();
    }

    public List<PluginVO.PluginVersionVO> listVersions(String pluginCode) {
        return pluginPersistenceService.listVersions(pluginCode);
    }

    public String validationDetail(String pluginCode, String version) {
        return requireVersion(pluginCode, version).getValidationReportJson();
    }

    public List<PluginVO.PluginRuntimeLogVO> runtimeLogs(String pluginCode) {
        return pluginPersistenceService.listRuntimeLogs(pluginCode);
    }

    public List<PluginVO.TenantPluginVO> availablePlugins(Long tenantId) {
        List<PluginVO.TenantPluginVO> result = pluginPersistenceService.listTenantPlugins(tenantId);
        for (PluginVO.TenantPluginVO plugin : result) {
            try {
                PluginDTO.FrontendPluginManifest manifest = objectMapper.readValue(Path.of(plugin.getManifestPath()).toFile(), PluginDTO.FrontendPluginManifest.class);
                plugin.setSharedDeps(manifest.getSharedDeps());
                plugin.setRoutes(manifest.getRoutes());
                plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
            } catch (Exception exception) {
                plugin.setSharedDeps(List.of());
                plugin.setRoutes(List.of());
                plugin.setMenus(List.of());
            }
        }
        return result;
    }

    public PluginRuntimeDescriptor requireTenantRuntime(Long tenantId, String pluginCode) {
        PluginTenantEntity tenantEntity = pluginPersistenceService.findTenantPlugin(tenantId, pluginCode)
                .filter(item -> item.getEnabled() != null && item.getEnabled() == 1)
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "当前租户未启用该插件"));
        ensureLoaded(pluginCode, tenantEntity.getPluginVersion());
        return pluginRegistry.find(pluginCode, tenantEntity.getPluginVersion())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行时不存在"));
    }

    public List<Map<String, Object>> tenantPluginMenus(Long tenantId, List<String> permissions) {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (PluginVO.TenantPluginVO plugin : availablePlugins(tenantId)) {
            for (Map<String, Object> menu : buildPluginMenus(plugin.getPluginCode(), plugin.getVersion())) {
                String permissionKey = (String) menu.get("permissionKey");
                if (permissionKey == null || permissions.contains(permissionKey)) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    public Path resolveManifestPath(String pluginCode, String version) {
        return Path.of(requireVersion(pluginCode, version).getFrontendManifestPath());
    }

    public Path resolvePluginAssetPath(String pluginCode, String version, String relativePath) {
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        Path versionHome = Path.of(versionEntity.getArtifactPath());
        Path resolved = versionHome.resolve("frontend").resolve(relativePath).normalize();
        if (!resolved.startsWith(versionHome.resolve("frontend"))) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件资源不存在");
        }
        if (!Files.exists(resolved)) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件资源不存在");
        }
        return resolved;
    }

    private void ensureLoaded(String pluginCode, String version) {
        if (pluginRegistry.find(pluginCode, version).isPresent()) {
            return;
        }
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (!Files.exists(Path.of(versionEntity.getArtifactPath()))) {
            install(pluginCode, version, new CurrentUser(0L, "system", null, null, 0, true, java.util.Set.of()));
            return;
        }
        PluginRuntimeDescriptor descriptor = pluginRuntimeLoader.load(parseMetadata(versionEntity), Path.of(versionEntity.getArtifactPath()));
        pluginRegistry.register(descriptor);
    }

    private void validateDependencies(PluginDTO.PluginPackageMetadata metadata) {
        if (metadata.getDependencyPlugins() == null || metadata.getDependencyPlugins().isEmpty()) {
            return;
        }
        for (PluginDTO.PluginDependencyDeclaration dependency : metadata.getDependencyPlugins()) {
            Optional<PluginVersionEntity> matched = pluginPersistenceService.listInstalledVersions(dependency.getPluginCode()).stream()
                    .filter(item -> "LOADED".equalsIgnoreCase(item.getInstallStatus()) || "LOADED".equalsIgnoreCase(item.getLoadStatus()))
                    .filter(item -> pluginSemver.compare(item.getVersion(), dependency.getMinVersion()) >= 0)
                    .findFirst();
            if (matched.isEmpty()) {
                throw new BizException(
                        ErrorCode.PLUGIN_DEPENDENCY_CONFLICT,
                        "缺少依赖插件 " + dependency.getPluginCode() + "，且版本需不低于 " + dependency.getMinVersion()
                );
            }
        }
    }

    private PluginDTO.PluginPackageMetadata parseMetadata(PluginVersionEntity versionEntity) {
        try {
            return objectMapper.readValue(versionEntity.getMetadataJson(), PluginDTO.PluginPackageMetadata.class);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "插件元数据解析失败");
        }
    }

    private List<Map<String, Object>> buildPluginMenus(String pluginCode, String version) {
        List<PluginMenuRelEntity> menuRelations = pluginPersistenceService.listMenuRelations(pluginCode, version);
        List<Map<String, Object>> menus = new ArrayList<>(menuRelations.size());
        for (PluginMenuRelEntity menuRelation : menuRelations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginCode", menuRelation.getPluginCode());
            item.put("menuCode", menuRelation.getMenuCode());
            item.put("parentMenuCode", menuRelation.getParentMenuCode());
            item.put("name", menuRelation.getMenuName());
            item.put("path", menuRelation.getRoutePath());
            item.put("icon", menuRelation.getIcon());
            item.put("permissionKey", menuRelation.getPermissionKey());
            item.put("sortNo", menuRelation.getSortNo());
            menus.add(item);
        }
        return menus;
    }

    private PluginVersionEntity requireVersion(String pluginCode, String version) {
        return pluginPersistenceService.findVersion(pluginCode, version)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "插件版本不存在"));
    }

    private void log(
            Long tenantId,
            String pluginCode,
            String version,
            String operationType,
            String lifecycleStatus,
            String resultStatus,
            String detailMessage,
            String failureStack,
            Long operatorId
    ) {
        pluginPersistenceService.insertRuntimeLog(
                tenantId,
                pluginCode,
                version,
                operationType,
                lifecycleStatus,
                resultStatus,
                detailMessage,
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                failureStack,
                operatorId
        );
    }
}
