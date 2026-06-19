package com.lumira.saas.modules.plugin.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.domain.model.PluginDomainModels.TenantPluginAggregate;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Service
public class PluginManagementAppService {

    private static final Logger log = LoggerFactory.getLogger(PluginManagementAppService.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String BUILTIN_SENSITIVE_WORDS_PLUGIN = "sensitive-words";

    private final PluginArtifactLoader pluginArtifactLoader;
    private final PluginPersistenceService pluginPersistenceService;
    private final PluginMigrationService pluginMigrationService;
    private final PluginRuntimeLoader pluginRuntimeLoader;
    private final PluginRegistry pluginRegistry;
    private final PluginSemver pluginSemver;
    private final SystemInternalApi systemInternalApi;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher domainEventPublisher;
    private static final long AVAILABLE_PLUGINS_CACHE_TTL_MILLIS = Duration.ofSeconds(8).toMillis();
    private static final long CURRENT_BOOTSTRAP_CACHE_TTL_MILLIS = Duration.ofSeconds(3).toMillis();
    private static final long READ_MODEL_VERSION_CACHE_TTL_MILLIS = Duration.ofSeconds(2).toMillis();
    private final ConcurrentMap<Long, CachedAvailablePlugins> availablePluginsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<BootstrapCacheKey, CachedCurrentBootstrap> currentBootstrapCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CachedReadModelVersion> readModelVersionCache = new ConcurrentHashMap<>();
    private final LongAdder availablePluginsCacheHits = new LongAdder();
    private final LongAdder availablePluginsCacheMisses = new LongAdder();
    private final LongAdder currentBootstrapCacheHits = new LongAdder();
    private final LongAdder currentBootstrapCacheMisses = new LongAdder();
    private final LongAdder readModelVersionCacheHits = new LongAdder();
    private final LongAdder readModelVersionCacheMisses = new LongAdder();
    private volatile List<Map<String, Object>> builtinMenuTemplate;

    public PluginManagementAppService(
            PluginArtifactLoader pluginArtifactLoader,
            PluginPersistenceService pluginPersistenceService,
            PluginMigrationService pluginMigrationService,
            PluginRuntimeLoader pluginRuntimeLoader,
            PluginRegistry pluginRegistry,
            PluginSemver pluginSemver,
            SystemInternalApi systemInternalApi,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            @Qualifier("pluginDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this.pluginArtifactLoader = pluginArtifactLoader;
        this.pluginPersistenceService = pluginPersistenceService;
        this.pluginMigrationService = pluginMigrationService;
        this.pluginRuntimeLoader = pluginRuntimeLoader;
        this.pluginRegistry = pluginRegistry;
        this.pluginSemver = pluginSemver;
        this.systemInternalApi = systemInternalApi;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.domainEventPublisher = domainEventPublisher;
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
        pluginMigrationService.executeUpMigrations(pluginCode, version, versionHome, currentUser.getUserId());
        log(null, pluginCode, version, "INSTALL", "MIGRATED", "SUCCESS", "插件私有迁移已完成", null, currentUser.getUserId());
        PluginRuntimeDescriptor descriptor = pluginRuntimeLoader.load(metadata, versionHome);
        pluginRegistry.register(descriptor);
        pluginPersistenceService.markInstalled(
                pluginCode,
                version,
                versionHome,
                versionHome.resolve("lumira-ui/manifest.json"),
                versionHome.resolve("lumira-backend/plugin.jar"),
                "LOADED",
                "LOADED",
                descriptor.getHealthIndicator() == null ? "HEALTHY" : "HEALTHY",
                1
        );
        if (pluginPersistenceService.listInstalledVersions(pluginCode).stream().noneMatch(item -> item.getIsActive() != null && item.getIsActive() == 1)) {
            pluginRegistry.activate(pluginCode, version);
            pluginPersistenceService.activateVersion(pluginCode, version);
            bumpBootstrapVersions(pluginCode, "plugin.version.auto-activated");
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
        bumpBootstrapVersions(pluginCode, "plugin.version.upgraded");
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
        bumpBootstrapVersions(pluginCode, "plugin.version.rolled-back");
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
                        .orElseGet(() -> pluginPersistenceService.listInstalledVersions(request.getPluginCode()).stream()
                                .findFirst()
                                .map(PluginVersionEntity::getVersion)
                                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "当前插件不存在激活版本")));
            }
            final String version = resolvedVersion;
            ensureLoaded(request.getPluginCode(), version);
            pluginMigrationService.executeUpMigrations(request.getPluginCode(), version, resolveVersionHome(request.getPluginCode(), version), currentUser.getUserId());
            enforceEmailRequirementIfNeeded(request.getPluginCode(), version, currentUser);
            transactionTemplate.executeWithoutResult(status -> {
                TenantPluginAggregate tenantPlugin = new TenantPluginAggregate(request.getPluginCode(), request.getTenantId(), false);
                tenantPlugin.enable(version);
                pluginPersistenceService.enablePluginForTenant(
                        request.getTenantId(),
                        request.getPluginCode(),
                        version,
                        request.getConfigJson(),
                        currentUser.getUserId()
                );
                pluginPersistenceService.registerTenantPermissions(request.getTenantId(), request.getPluginCode(), version);
                pluginPersistenceService.updateVersionStatus(request.getPluginCode(), version, "LOADED", "LOADED", "HEALTHY", "ENABLED", "READY");
                pluginPersistenceService.bumpBootstrapVersion(request.getTenantId(), "plugin.enabled");
                invalidateTenantBootstrapCaches(request.getTenantId());
                logTenantPluginDomainEvents(tenantPlugin.pullDomainEvents(), version, currentUser.getUserId());
            });
            safeLog(request.getTenantId(), request.getPluginCode(), version, "ENABLE", "ENABLED", "SUCCESS", "平台插件已启用", null, currentUser.getUserId());
        } catch (BizException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "启用插件失败: " + rootCauseMessage(throwable));
        }
    }

    @Transactional
    public void disable(PluginDTO.DisableRequest request, CurrentUser currentUser) {
        PluginTenantEntity tenantEntity = pluginPersistenceService.findTenantPlugin(request.getTenantId(), request.getPluginCode())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "当前尚未启用该插件"));
        TenantPluginAggregate tenantPlugin = new TenantPluginAggregate(request.getPluginCode(), request.getTenantId(), true);
        tenantPlugin.disable(Boolean.TRUE.equals(request.getPurgeData()) ? "purge-data" : "disable");
        pluginPersistenceService.disablePluginForTenant(request.getTenantId(), request.getPluginCode(), currentUser.getUserId());
        pluginPersistenceService.bumpBootstrapVersion(request.getTenantId(), "plugin.disabled");
        invalidateTenantBootstrapCaches(request.getTenantId());
        boolean purgeData = Boolean.TRUE.equals(request.getPurgeData());
        if (purgeData) {
            pluginMigrationService.executeDownMigrations(
                    request.getPluginCode(),
                    tenantEntity.getPluginVersion(),
                    resolveVersionHome(request.getPluginCode(), tenantEntity.getPluginVersion()),
                    currentUser.getUserId()
            );
        }
        pluginPersistenceService.updateVersionStatus(
                request.getPluginCode(),
                tenantEntity.getPluginVersion(),
                "LOADED",
                purgeData ? "UNLOADED" : "LOADED",
                "HEALTHY",
                "DISABLED",
                purgeData ? "REMOVED" : "READY"
        );
        logTenantPluginDomainEvents(tenantPlugin.pullDomainEvents(), tenantEntity.getPluginVersion(), currentUser.getUserId());
        systemInternalApi.invalidatePermissionSnapshot(request.getTenantId());
        safeLog(
                request.getTenantId(),
                request.getPluginCode(),
                tenantEntity.getPluginVersion(),
                "DISABLE",
                "DISABLED",
                "SUCCESS",
                purgeData ? "平台插件已停用并删除独立数据表" : "平台插件已停用",
                null,
                currentUser.getUserId()
        );
    }

    public List<PluginVO.PluginDefinitionVO> listDefinitions() {
        return pluginPersistenceService.listDefinitions();
    }

    public List<PluginVO.PluginVersionVO> listVersions(String pluginCode) {
        return pluginPersistenceService.listVersions(pluginCode);
    }

    public Map<String, List<PluginVO.PluginVersionVO>> listAllVersions() {
        return pluginPersistenceService.listAllVersions();
    }

    public String validationDetail(String pluginCode, String version) {
        return requireVersion(pluginCode, version).getValidationReportJson();
    }

    public List<PluginVO.PluginRuntimeLogVO> runtimeLogs(String pluginCode) {
        return pluginPersistenceService.listRuntimeLogs(pluginCode);
    }

    public List<PluginVO.TenantPluginVO> availablePlugins(Long tenantId) {
        long bootstrapVersion = readModelVersionForTenant(tenantId);
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        CachedAvailablePlugins cached = availablePluginsCache.get(effectiveTenantId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.version() == bootstrapVersion && cached.expiresAtEpochMillis() > now) {
            availablePluginsCacheHits.increment();
            return new ArrayList<>(cached.availablePlugins());
        }
        availablePluginsCacheMisses.increment();
        List<PluginVO.TenantPluginVO> fromPersistence = pluginPersistenceService.listTenantPlugins(effectiveTenantId);
        if (fromPersistence == null || fromPersistence.isEmpty()) {
            CachedAvailablePlugins emptySnapshot = new CachedAvailablePlugins(
                    bootstrapVersion,
                    now + AVAILABLE_PLUGINS_CACHE_TTL_MILLIS,
                    List.of()
            );
            availablePluginsCache.put(effectiveTenantId, emptySnapshot);
            return List.of();
        }
        List<PluginVO.TenantPluginVO> result = new ArrayList<>(fromPersistence.size());
        for (PluginVO.TenantPluginVO plugin : fromPersistence) {
            try {
                populateRuntimeMetadata(plugin);
                result.add(plugin);
            } catch (BizException exception) {
                log.warn("Skipping plugin {} {} for tenant {} because runtime files are invalid: {}", plugin.getPluginCode(), plugin.getVersion(), tenantId, exception.getMessage());
            } catch (Exception exception) {
                log.warn("Skipping plugin {} {} for tenant {} because runtime metadata failed to load", plugin.getPluginCode(), plugin.getVersion(), tenantId, exception);
            }
        }
        CachedAvailablePlugins snapshot = new CachedAvailablePlugins(
                bootstrapVersion,
                System.currentTimeMillis() + AVAILABLE_PLUGINS_CACHE_TTL_MILLIS,
                result
        );
        availablePluginsCache.put(effectiveTenantId, snapshot);
        return new ArrayList<>(result);
    }

    public PluginVO.PluginStatusVO status(Long tenantId, String pluginCode) {
        PluginVO.PluginStatusVO status = pluginPersistenceService.pluginStatus(tenantId, pluginCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "插件不存在"));
        if (status.getRuntimeContributions() == null || status.getRuntimeContributions().isEmpty()) {
            status.setRuntimeContributions(resolveRuntimeContributions(pluginCode));
        }
        return status;
    }

    @Transactional
    public void uninstall(String pluginCode, boolean removeData, CurrentUser currentUser) {
        if (isBuiltinCorePlugin(pluginCode)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "内置插件不支持卸载，请改为停用插件");
        }
        List<PluginVersionEntity> versions = pluginPersistenceService.listInstalledVersions(pluginCode);
        List<Long> tenantIds = pluginPersistenceService.listTenantIdsForPlugin(pluginCode);
        for (PluginVersionEntity versionEntity : versions) {
            if (removeData) {
                pluginMigrationService.executeDownMigrations(pluginCode, versionEntity.getVersion(), resolveVersionHome(pluginCode, versionEntity.getVersion()), currentUser.getUserId());
            }
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
            systemInternalApi.invalidatePermissionSnapshot(tenantId);
            pluginPersistenceService.bumpBootstrapVersion(tenantId, "plugin.uninstalled");
            invalidateTenantBootstrapCaches(tenantId);
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
        var user = systemInternalApi.findUserById(currentUser.getUserId());
        if (user == null || !org.springframework.util.StringUtils.hasText(user.email())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先补充邮箱后再启用该验证方式");
        }
    }

    public PluginRuntimeDescriptor requireTenantRuntime(Long tenantId, String pluginCode) {
        PluginTenantEntity tenantEntity = pluginPersistenceService.findTenantPlugin(tenantId, pluginCode)
                .filter(item -> item.getEnabled() != null && item.getEnabled() == 1)
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "当前未启用该插件"));
        ensureLoaded(pluginCode, tenantEntity.getPluginVersion());
        return pluginRegistry.find(pluginCode, tenantEntity.getPluginVersion())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行时不存在"));
    }

    public List<Map<String, Object>> tenantPluginMenus(Long tenantId, Set<String> permissions) {
        return tenantPluginMenus(availablePlugins(tenantId), permissions);
    }

    public Map<String, Object> currentBootstrap(Long tenantId, List<String> permissions) {
        Set<String> permissionSet = normalizePermissionSet(permissions);
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        long bootstrapVersion = readModelVersionForTenant(tenantId);
        String permissionSignature = permissionSignature(permissionSet);
        BootstrapCacheKey cacheKey = new BootstrapCacheKey(effectiveTenantId, bootstrapVersion, permissionSignature);
        long now = System.currentTimeMillis();
        CachedCurrentBootstrap cached = currentBootstrapCache.get(cacheKey);
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            currentBootstrapCacheHits.increment();
            return cached.bootstrapPayload();
        }
        currentBootstrapCacheMisses.increment();
        List<PluginVO.TenantPluginVO> availablePlugins = availablePlugins(tenantId);
        Map<String, Object> payload = Map.of(
                "menuTree", currentMenus(availablePlugins, permissionSet),
                "availablePlugins", availablePlugins
        );
        currentBootstrapCache.put(cacheKey, new CachedCurrentBootstrap(
                tenantId,
                bootstrapVersion,
                permissionSignature,
                now + CURRENT_BOOTSTRAP_CACHE_TTL_MILLIS,
                payload
        ));
        return payload;
    }

    private void bumpBootstrapVersions(String pluginCode, String eventKey) {
        for (Long tenantId : pluginPersistenceService.listTenantIdsForPlugin(pluginCode)) {
            pluginPersistenceService.bumpBootstrapVersion(tenantId, eventKey);
            invalidateTenantBootstrapCaches(tenantId);
        }
    }

    private List<Map<String, Object>> tenantPluginMenus(List<PluginVO.TenantPluginVO> availablePlugins, Set<String> permissions) {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (PluginVO.TenantPluginVO plugin : availablePlugins) {
            for (Map<String, Object> menu : buildPluginMenus(plugin.getPluginCode(), plugin.getVersion())) {
                String permissionKey = (String) menu.get("permissionKey");
                if (permissionKey == null || permissions.contains("*") || permissions.contains(permissionKey)) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    public List<Map<String, Object>> currentMenus(Long tenantId, List<String> permissions) {
        Map<String, Object> bootstrap = currentBootstrap(tenantId, permissions);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menuTree = (List<Map<String, Object>>) bootstrap.get("menuTree");
        return menuTree == null ? List.of() : menuTree;
    }

    private List<Map<String, Object>> currentMenus(List<PluginVO.TenantPluginVO> availablePlugins, Set<String> permissionSet) {
        List<Map<String, Object>> baseMenus = builtinMenus();
        List<Map<String, Object>> mergedMenus = mergeMenus(baseMenus, tenantPluginMenus(availablePlugins, permissionSet));
        return pruneMenuTree(mergedMenus, permissionSet);
    }

    public Path resolveManifestPath(String pluginCode, String version) {
        if (isBuiltinCorePlugin(pluginCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "内置插件不提供独立前端清单");
        }
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (versionEntity.getFrontendManifestPath() == null || versionEntity.getFrontendManifestPath().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件清单不存在");
        }
        Path manifestPath = Path.of(versionEntity.getFrontendManifestPath());
        if (!Files.exists(manifestPath)) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件清单不存在");
        }
        return manifestPath;
    }

    public Path resolvePluginAssetPath(String pluginCode, String version, String relativePath) {
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件资源不存在");
        }
        Path versionHome = Path.of(versionEntity.getArtifactPath());
        Path resolved = versionHome.resolve("lumira-ui").resolve(relativePath).normalize();
        if (!resolved.startsWith(versionHome.resolve("lumira-ui"))) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件资源不存在");
        }
        if (!Files.exists(resolved)) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件资源不存在");
        }
        return resolved;
    }

    private void ensureLoaded(String pluginCode, String version) {
        if (isBuiltinCorePlugin(pluginCode)) {
            return;
        }
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

    private void populateRuntimeMetadata(PluginVO.TenantPluginVO plugin) throws Exception {
        PluginVO.PluginStatusVO status = pluginPersistenceService.pluginStatus(
                        com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID,
                        plugin.getPluginCode()
                )
                .orElse(null);
        if (isBuiltinCorePlugin(plugin.getPluginCode())) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of("/plugins/sensitive-words"));
            plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
            plugin.setLifecycleStatus(status == null ? "ENABLED" : status.getLifecycleStatus());
            plugin.setSchemaStatus(status == null ? "READY" : status.getSchemaStatus());
            plugin.setSupportsHotDisable(status == null ? Boolean.TRUE : status.getSupportsHotDisable());
            plugin.setSupportsDataPurge(status == null ? Boolean.TRUE : status.getSupportsDataPurge());
            plugin.setRuntimeContributions(status == null ? resolveRuntimeContributions(plugin.getPluginCode()) : status.getRuntimeContributions());
            return;
        }
        PluginVersionEntity versionEntity = requireVersion(plugin.getPluginCode(), plugin.getVersion());
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行目录不存在");
        }
        Path manifestPath = resolveManifestPath(plugin.getPluginCode(), plugin.getVersion());
        PluginDTO.FrontendPluginManifest manifest = objectMapper.readValue(manifestPath.toFile(), PluginDTO.FrontendPluginManifest.class);
        validateRuntimeAssets(Path.of(versionEntity.getArtifactPath()), manifest);
        plugin.setSharedDeps(manifest.getSharedDeps());
        plugin.setRoutes(manifest.getRoutes());
        plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
        if (status != null) {
            plugin.setLifecycleStatus(status.getLifecycleStatus());
            plugin.setSchemaStatus(status.getSchemaStatus());
            plugin.setSupportsHotDisable(status.getSupportsHotDisable());
            plugin.setSupportsDataPurge(status.getSupportsDataPurge());
            plugin.setRuntimeContributions(status.getRuntimeContributions());
        }
    }

    private void validateRuntimeAssets(Path versionHome, PluginDTO.FrontendPluginManifest manifest) {
        if (manifest == null) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件前端清单缺失");
        }
        if (versionHome == null || !Files.exists(versionHome)) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行目录不存在");
        }
        if (manifest.getAssets() == null || manifest.getAssets().isEmpty()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件前端清单缺少 assets");
        }
        for (String asset : manifest.getAssets()) {
            if (asset == null || asset.isBlank()) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件前端清单包含空资产路径");
            }
            Path assetPath = versionHome.resolve("lumira-ui").resolve(asset).normalize();
            if (!assetPath.startsWith(versionHome.resolve("lumira-ui")) || !Files.exists(assetPath)) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件资源不存在: " + asset);
            }
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

    private Map<String, Object> toMenuMap(MenuNodeDTO menuNode) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", menuNode.getId());
        item.put("parentId", menuNode.getParentId());
        item.put("menuCode", menuNode.getMenuCode());
        item.put("name", menuNode.getName());
        item.put("path", menuNode.getPath());
        item.put("component", menuNode.getComponent());
        item.put("icon", menuNode.getIcon());
        item.put("permissionKey", menuNode.getPermissionKey());
        item.put("pluginCode", menuNode.getPluginCode());
        item.put("sortNo", menuNode.getSortNo());
        List<Map<String, Object>> children = menuNode.getChildren() == null
                ? List.of()
                : menuNode.getChildren().stream().map(this::toMenuMap).toList();
        item.put("children", new ArrayList<>(children));
        return item;
    }

    private List<Map<String, Object>> builtinMenus() {
        List<Map<String, Object>> template = builtinMenuTemplate;
        if (template == null) {
            synchronized (this) {
                template = builtinMenuTemplate;
                if (template == null) {
                    template = systemInternalApi.builtinMenus().stream().map(this::toMenuMap).toList();
                    builtinMenuTemplate = template;
                }
            }
        }
        return copyMenus(template);
    }

    private Set<String> normalizePermissionSet(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        return permissions.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyMenus(List<Map<String, Object>> menus) {
        List<Map<String, Object>> copied = new ArrayList<>(menus.size());
        for (Map<String, Object> menu : menus) {
            Map<String, Object> item = new LinkedHashMap<>(menu);
            Object children = menu.get("children");
            item.put("children", children instanceof List<?> childList
                    ? copyMenus((List<Map<String, Object>>) childList)
                    : new ArrayList<Map<String, Object>>());
            copied.add(item);
        }
        return copied;
    }

    private List<Map<String, Object>> mergeMenus(List<Map<String, Object>> baseMenus, List<Map<String, Object>> pluginMenus) {
        List<Map<String, Object>> merged = new ArrayList<>();
        Map<String, Map<String, Object>> byMenuCode = new LinkedHashMap<>();
        for (Map<String, Object> menu : baseMenus) {
            merged.add(menu);
            indexMenu(menu, byMenuCode);
        }
        for (Map<String, Object> pluginMenu : pluginMenus) {
            attachPluginMenu(merged, byMenuCode, pluginMenu);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void indexMenu(Map<String, Object> menu, Map<String, Map<String, Object>> byMenuCode) {
        Object menuCode = menu.get("menuCode");
        if (menuCode instanceof String code && !code.isBlank()) {
            byMenuCode.put(code, menu);
        }
        Object children = menu.get("children");
        if (children instanceof List<?> childList) {
            for (Object child : childList) {
                if (child instanceof Map<?, ?> childMap) {
                    indexMenu((Map<String, Object>) childMap, byMenuCode);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void attachPluginMenu(List<Map<String, Object>> roots, Map<String, Map<String, Object>> byMenuCode, Map<String, Object> pluginMenu) {
        String parentMenuCode = (String) pluginMenu.get("parentMenuCode");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", pluginMenu.get("id"));
        node.put("parentId", pluginMenu.get("parentId"));
        node.put("menuCode", pluginMenu.get("menuCode"));
        node.put("name", pluginMenu.get("name"));
        node.put("path", pluginMenu.get("path"));
        node.put("component", pluginMenu.get("component"));
        node.put("icon", pluginMenu.get("icon"));
        node.put("permissionKey", pluginMenu.get("permissionKey"));
        node.put("pluginCode", pluginMenu.get("pluginCode"));
        node.put("sortNo", pluginMenu.get("sortNo"));
        node.put("children", new ArrayList<Map<String, Object>>());
        if (parentMenuCode != null && !parentMenuCode.isBlank() && byMenuCode.containsKey(parentMenuCode)) {
            Map<String, Object> parent = byMenuCode.get(parentMenuCode);
            List<Map<String, Object>> children = (List<Map<String, Object>>) parent.computeIfAbsent("children", key -> new ArrayList<Map<String, Object>>());
            children.add(node);
            byMenuCode.put((String) node.get("menuCode"), node);
            return;
        }
        roots.add(node);
        byMenuCode.put((String) node.get("menuCode"), node);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pruneMenuTree(List<Map<String, Object>> menus, Set<String> permissions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> menu : menus) {
            Map<String, Object> visible = pruneVisibleMenu(menu, permissions);
            if (visible != null) {
                result.add(visible);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pruneVisibleMenu(Map<String, Object> menu, java.util.Set<String> permissions) {
        List<Map<String, Object>> children = (List<Map<String, Object>>) menu.getOrDefault("children", List.of());
        List<Map<String, Object>> visibleChildren = new ArrayList<>();
        for (Map<String, Object> child : children) {
            Map<String, Object> visibleChild = pruneVisibleMenu(child, permissions);
            if (visibleChild != null) {
                visibleChildren.add(visibleChild);
            }
        }
        if (!isMenuAllowed(menu, permissions) && visibleChildren.isEmpty()) {
            return null;
        }
        Map<String, Object> visibleMenu = new LinkedHashMap<>(menu);
        visibleMenu.put("children", visibleChildren);
        return visibleMenu;
    }

    private boolean isMenuAllowed(Map<String, Object> menu, java.util.Set<String> permissions) {
        String permissionKey = (String) menu.get("permissionKey");
        return permissionKey == null || permissionKey.isBlank() || permissions.contains("*") || permissions.contains(permissionKey);
    }

    private boolean isBuiltinCorePlugin(String pluginCode) {
        return BUILTIN_SENSITIVE_WORDS_PLUGIN.equals(pluginCode);
    }

    private void invalidateTenantBootstrapCaches(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        availablePluginsCache.remove(effectiveTenantId);
        readModelVersionCache.remove(effectiveTenantId);
        currentBootstrapCache.keySet().removeIf(key -> effectiveTenantId.equals(key.tenantId()));
    }

    private String permissionSignature(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "NONE";
        }
        return permissions.stream()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private long readModelVersionForTenant(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        CachedReadModelVersion cached = readModelVersionCache.get(effectiveTenantId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            readModelVersionCacheHits.increment();
            return cached.version();
        }
        readModelVersionCacheMisses.increment();
        long version = 0L;
        try {
            Long actualVersion = systemInternalApi.readModelVersion(effectiveTenantId, "plugin", "bootstrap");
            if (actualVersion != null) {
                version = actualVersion;
            }
        } catch (Exception exception) {
            log.warn("Failed to read plugin bootstrap read-model version for tenantId={}", effectiveTenantId, exception);
            CachedAvailablePlugins cachedAvailablePlugins = availablePluginsCache.get(effectiveTenantId);
            if (cachedAvailablePlugins != null) {
                version = cachedAvailablePlugins.version();
            }
        }
        readModelVersionCache.put(effectiveTenantId, new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private Path resolveVersionHome(String pluginCode, String version) {
        if (isBuiltinCorePlugin(pluginCode)) {
            return null;
        }
        PluginVersionEntity versionEntity = pluginPersistenceService.findVersion(pluginCode, version).orElse(null);
        if (versionEntity == null) {
            return null;
        }
        if (!StringUtils.hasText(versionEntity.getArtifactPath())) {
            return null;
        }
        return Path.of(versionEntity.getArtifactPath());
    }

    private List<String> resolveRuntimeContributions(String pluginCode) {
        if (isBuiltinCorePlugin(pluginCode)) {
            return List.of("routes", "menus", "permissions", "importers", "interceptors");
        }
        return List.of();
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

    private void logTenantPluginDomainEvents(List<DomainEvent> events, String version, Long operatorId) {
        if (events == null || events.isEmpty()) {
            return;
        }
        domainEventPublisher.publishAll(events);
        for (DomainEvent event : events) {
            safeLog(
                    event.tenantId(),
                    event.aggregateId(),
                    version,
                    event.eventType(),
                    "DOMAIN_EVENT",
                    "SUCCESS",
                    event.attributes() == null ? "{}" : event.attributes().toString(),
                    null,
                    operatorId
            );
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

    private static final class CachedAvailablePlugins {
        private final long version;
        private final long expiresAtEpochMillis;
        private final List<PluginVO.TenantPluginVO> availablePlugins;

        private CachedAvailablePlugins(long version, long expiresAtEpochMillis, List<PluginVO.TenantPluginVO> availablePlugins) {
            this.version = version;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
            this.availablePlugins = availablePlugins == null ? List.of() : List.copyOf(availablePlugins);
        }

        private long version() {
            return version;
        }

        private long expiresAtEpochMillis() {
            return expiresAtEpochMillis;
        }

        private List<PluginVO.TenantPluginVO> availablePlugins() {
            return availablePlugins;
        }
    }

    private static final class CachedCurrentBootstrap {
        private final Long tenantId;
        private final long version;
        private final String permissionSignature;
        private final long expiresAtEpochMillis;
        private final Map<String, Object> bootstrapPayload;

        private CachedCurrentBootstrap(Long tenantId, long version, String permissionSignature, long expiresAtEpochMillis, Map<String, Object> bootstrapPayload) {
            this.tenantId = tenantId;
            this.version = version;
            this.permissionSignature = permissionSignature;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
            this.bootstrapPayload = bootstrapPayload;
        }

        private long expiresAtEpochMillis() {
            return expiresAtEpochMillis;
        }

        private Map<String, Object> bootstrapPayload() {
            return bootstrapPayload;
        }
    }

    private static final class CachedReadModelVersion {
        private final long version;
        private final long expiresAtEpochMillis;

        private CachedReadModelVersion(long version, long expiresAtEpochMillis) {
            this.version = version;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
        }

        private long version() {
            return version;
        }

        private long expiresAtEpochMillis() {
            return expiresAtEpochMillis;
        }
    }

    private static final record BootstrapCacheKey(Long tenantId, long version, String permissionSignature) {
    }
}
