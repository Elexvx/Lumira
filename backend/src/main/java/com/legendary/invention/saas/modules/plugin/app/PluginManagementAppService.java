package com.legendary.invention.saas.modules.plugin.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.plugin.dto.PluginDTO;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.legendary.invention.saas.modules.plugin.loader.PluginArtifactLoader;
import com.legendary.invention.saas.modules.plugin.loader.PluginRuntimeLoader;
import com.legendary.invention.saas.modules.plugin.registry.PluginRegistry;
import com.legendary.invention.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.legendary.invention.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import com.legendary.invention.saas.modules.plugin.service.PluginMigrationService;
import com.legendary.invention.saas.modules.plugin.service.PluginPersistenceService;
import com.legendary.invention.saas.modules.plugin.service.PluginSemver;
import com.legendary.invention.saas.modules.plugin.vo.PluginVO;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
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

    private static final Logger log = LoggerFactory.getLogger(PluginManagementAppService.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final PluginArtifactLoader pluginArtifactLoader;
    private final PluginPersistenceService pluginPersistenceService;
    private final PluginMigrationService pluginMigrationService;
    private final PluginRuntimeLoader pluginRuntimeLoader;
    private final PluginRegistry pluginRegistry;
    private final PluginSemver pluginSemver;
    private final PermissionSnapshotService permissionSnapshotService;
    private final UserDomainService userDomainService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PluginManagementAppService(
            PluginArtifactLoader pluginArtifactLoader,
            PluginPersistenceService pluginPersistenceService,
            PluginMigrationService pluginMigrationService,
            PluginRuntimeLoader pluginRuntimeLoader,
            PluginRegistry pluginRegistry,
            PluginSemver pluginSemver,
            PermissionSnapshotService permissionSnapshotService,
            UserDomainService userDomainService,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.pluginArtifactLoader = pluginArtifactLoader;
        this.pluginPersistenceService = pluginPersistenceService;
        this.pluginMigrationService = pluginMigrationService;
        this.pluginRuntimeLoader = pluginRuntimeLoader;
        this.pluginRegistry = pluginRegistry;
        this.pluginSemver = pluginSemver;
        this.permissionSnapshotService = permissionSnapshotService;
        this.userDomainService = userDomainService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PluginVO.PluginUploadVO upload(MultipartFile file, CurrentUser currentUser) {
        PluginArtifactLoader.UploadedArtifact artifact = pluginArtifactLoader.stage(file);
        PluginVersionEntity versionEntity = pluginPersistenceService.saveUploadedPackage(
                artifact.metadata(),
                artifact.zipPath(),
                artifact.packageRoot(),
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

    public void enable(PluginDTO.EnableRequest request, CurrentUser currentUser) {
        try {
            String resolvedVersion = request.getVersion();
            if (resolvedVersion == null || resolvedVersion.isBlank()) {
                resolvedVersion = pluginRegistry.findActiveVersion(request.getPluginCode())
                        .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "当前插件不存在激活版本"));
            }
            final String version = resolvedVersion;
            ensureLoaded(request.getPluginCode(), version);
            enforceEmailRequirementIfNeeded(request.getPluginCode(), version, currentUser);
            transactionTemplate.executeWithoutResult(status -> {
                pluginPersistenceService.enablePluginForTenant(
                        request.getTenantId(),
                        request.getPluginCode(),
                        version,
                        request.getConfigJson(),
                        currentUser.getUserId()
                );
                pluginPersistenceService.registerTenantPermissions(request.getTenantId(), request.getPluginCode(), version);
            });
            permissionSnapshotService.invalidateTenant(request.getTenantId());
            safeLog(request.getTenantId(), request.getPluginCode(), version, "ENABLE", "ENABLED", "SUCCESS", "租户插件已启用", null, currentUser.getUserId());
        } catch (BizException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "启用插件失败: " + rootCauseMessage(throwable));
        }
    }

    @Transactional
    public void disable(PluginDTO.DisableRequest request, CurrentUser currentUser) {
        PluginTenantEntity tenantEntity = pluginPersistenceService.findTenantPlugin(request.getTenantId(), request.getPluginCode())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "当前租户尚未启用该插件"));
        pluginPersistenceService.disablePluginForTenant(request.getTenantId(), request.getPluginCode(), currentUser.getUserId());
        permissionSnapshotService.invalidateTenant(request.getTenantId());
        safeLog(request.getTenantId(), request.getPluginCode(), tenantEntity.getPluginVersion(), "DISABLE", "DISABLED", "SUCCESS", "租户插件已停用", null, currentUser.getUserId());
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

    @Transactional
    public void uninstall(String pluginCode, boolean removeData, CurrentUser currentUser) {
        List<PluginVersionEntity> versions = pluginPersistenceService.listInstalledVersions(pluginCode);
        List<Long> tenantIds = pluginPersistenceService.listTenantIdsForPlugin(pluginCode);
        for (PluginVersionEntity versionEntity : versions) {
            removePluginVersionArtifacts(versionEntity);
            try {
                pluginRegistry.unload(pluginCode, versionEntity.getVersion());
            } catch (Exception exception) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行时卸载失败: " + exception.getMessage());
            }
        }
        if (removeData) {
            pluginPersistenceService.purgePluginData(pluginCode, currentUser.getUserId());
        } else {
            pluginPersistenceService.uninstallPlugin(pluginCode, currentUser.getUserId());
        }
        for (Long tenantId : tenantIds) {
            permissionSnapshotService.invalidateTenant(tenantId);
        }
        safeLog(
                null,
                pluginCode,
                versions.isEmpty() ? null : versions.get(0).getVersion(),
                "UNINSTALL",
                "REMOVED",
                "SUCCESS",
                removeData ? "插件已卸载并删除数据库数据" : "插件已卸载",
                null,
                currentUser.getUserId()
        );
    }

    private void removePluginVersionArtifacts(PluginVersionEntity versionEntity) {
        if (versionEntity == null) {
            return;
        }
        if (StringUtils.hasText(versionEntity.getArtifactPath())) {
            pluginArtifactLoader.removePath(Path.of(versionEntity.getArtifactPath()));
        }
        if (StringUtils.hasText(versionEntity.getPackagePath())) {
            Path stagedRoot = Path.of(versionEntity.getPackagePath()).getParent();
            if (stagedRoot != null) {
                pluginArtifactLoader.removePath(stagedRoot);
                return;
            }
        }
        if (StringUtils.hasText(versionEntity.getStagedPath())) {
            Path stagedRoot = Path.of(versionEntity.getStagedPath()).getParent();
            if (stagedRoot != null) {
                pluginArtifactLoader.removePath(stagedRoot);
            }
        }
    }

    public Optional<PluginRuntimeDescriptor> findTenantRuntimeDescriptor(Long tenantId, String pluginCode) {
        return availablePlugins(tenantId).stream()
                .filter(item -> pluginCode.equals(item.getPluginCode()))
                .findFirst()
                .flatMap(item -> pluginRegistry.find(item.getPluginCode(), item.getVersion()));
    }

    public Optional<PluginRuntimeDescriptor> findActiveRuntimeDescriptor(String pluginCode) {
        return pluginRegistry.findActiveVersion(pluginCode).flatMap(version -> pluginRegistry.find(pluginCode, version));
    }

    public Optional<PluginSecondFactorProvider> findSecondFactorProvider(Long tenantId, String pluginCode) {
        return findTenantRuntimeDescriptor(tenantId, pluginCode).map(PluginRuntimeDescriptor::getSecondFactorProvider);
    }

    public Optional<PluginSecondFactorProvider> findSecondFactorProvider(String pluginCode) {
        return findActiveRuntimeDescriptor(pluginCode).map(PluginRuntimeDescriptor::getSecondFactorProvider);
    }

    private void enforceEmailRequirementIfNeeded(String pluginCode, String version, CurrentUser currentUser) {
        PluginRuntimeDescriptor descriptor = pluginRegistry.find(pluginCode, version)
                .orElse(null);
        if (descriptor == null || descriptor.getSecondFactorProvider() == null) {
            return;
        }
        PluginSecondFactorProvider provider = descriptor.getSecondFactorProvider();
        if (!provider.requiresEmail()) {
            return;
        }
        SysUserEntity user = userDomainService.findById(currentUser.getUserId()).orElse(null);
        if (user == null || !org.springframework.util.StringUtils.hasText(user.getEmail())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再启用该验证方式");
        }
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
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件版本尚未安装，请先安装后再启用");
        }
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

    private void safeLog(
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
        try {
            log(
                    tenantId,
                    pluginCode,
                    version,
                    operationType,
                    lifecycleStatus,
                    resultStatus,
                    detailMessage,
                    failureStack,
                    operatorId
            );
        } catch (Throwable throwable) {
            log.warn("Failed to write plugin runtime log pluginCode={} version={} operationType={}", pluginCode, version, operationType, throwable);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return throwable.getClass().getSimpleName();
    }
}
