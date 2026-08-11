package com.lumira.saas.modules.plugin.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.plugin.BuiltinPluginLifecycleHook;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ReadModelVersionCache;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.domain.model.PluginDomainModels.PluginActivationAggregate;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String BUILTIN_WORK_ORDER_FEEDBACK_PLUGIN = "work-order-feedback";
    private static final String BUILTIN_MOCK_PAYMENT_PLUGIN = "builtin-mock-payment";
    private static final Set<String> AUTHENTICATED_PERMISSIONLESS_MENU_CODES = Set.of(
            "certificate.mine",
            "expert.application"
    );
    private static final Map<String, BuiltinPluginRuntime> BUILTIN_PLUGIN_RUNTIMES = Map.of(
            BUILTIN_SENSITIVE_WORDS_PLUGIN,
            new BuiltinPluginRuntime(
                    List.of("/settings/sensitive-words"),
                    List.of("routes", "menus", "permissions", "importers", "interceptors")
            ),
            BUILTIN_WORK_ORDER_FEEDBACK_PLUGIN,
            new BuiltinPluginRuntime(
                    List.of("/work-order-feedback"),
                    List.of("routes", "menus", "permissions", "rich-text-upload")
            ),
            BUILTIN_MOCK_PAYMENT_PLUGIN,
            new BuiltinPluginRuntime(
                    List.of("/mock-payment/checkout"),
                    List.of("payment-provider", "checkout-route", "callbacks", "refunds")
            )
    );

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
    private final ReadModelVersionCache readModelVersionCache;
    private List<BuiltinPluginLifecycleHook> builtinPluginLifecycleHooks = List.of();
    private static final long READ_MODEL_VERSION_CACHE_TTL_MILLIS = Duration.ofSeconds(2).toMillis();
    private static final String GLOBAL_PLUGIN_SCOPE_KEY = "global";
    private static final String READ_MODEL_CACHE_KEY_PLUGIN_BOOTSTRAP = "plugin:bootstrap";
    private static final String READ_MODEL_CACHE_KEY_PLATFORM_MENU_TREE = "platform:menu-tree";
    private static final String READ_MODEL_CONTEXT_PLUGIN = "plugin";
    private static final String READ_MODEL_SCOPE_PLUGIN_BOOTSTRAP = "bootstrap";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PLATFORM_MENU_TREE = "menu-tree";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_UPLOAD = "plugin:management:upload";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_INSTALL = "plugin:management:install";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_UPGRADE = "plugin:management:upgrade";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_ROLLBACK = "plugin:management:rollback";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_ENABLE = "plugin:management:enable";
    private static final String PERMISSION_PLUGIN_MANAGEMENT_DISABLE = "plugin:management:disable";
    private final ConcurrentMap<String, CachedAvailablePlugins> availablePluginsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<MenuCompilationVersion, CachedCompiledMenuTree> compiledMenuTreeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<VisibleMenuTreeCacheKey, CachedVisibleMenuTree> visibleMenuTreeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<BootstrapCacheKey, CachedCurrentBootstrap> currentBootstrapCache = new ConcurrentHashMap<>();
    private final LongAdder availablePluginsCacheHits = new LongAdder();
    private final LongAdder availablePluginsCacheMisses = new LongAdder();
    private final LongAdder currentBootstrapCacheHits = new LongAdder();
    private final LongAdder currentBootstrapCacheMisses = new LongAdder();
    private final LongAdder readModelVersionCacheHits = new LongAdder();
    private final LongAdder readModelVersionCacheMisses = new LongAdder();

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
        this(
                pluginArtifactLoader,
                pluginPersistenceService,
                pluginMigrationService,
                pluginRuntimeLoader,
                pluginRegistry,
                pluginSemver,
                systemInternalApi,
                transactionManager,
                objectMapper,
                domainEventPublisher,
                new ReadModelVersionCache(READ_MODEL_VERSION_CACHE_TTL_MILLIS)
        );
    }

    @Autowired
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
            @Qualifier("pluginDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            ReadModelVersionCache readModelVersionCache
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
        this.readModelVersionCache = readModelVersionCache;
    }

    @Autowired
    void setBuiltinPluginLifecycleHooks(ObjectProvider<BuiltinPluginLifecycleHook> lifecycleHooksProvider) {
        this.builtinPluginLifecycleHooks = lifecycleHooksProvider == null
                ? List.of()
                : lifecycleHooksProvider.orderedStream().toList();
    }

    @Transactional
    public PluginVO.PluginUploadVO upload(MultipartFile file, CurrentUser currentUser) {
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_UPLOAD);
        PluginArtifactLoader.UploadedArtifact artifact = pluginArtifactLoader.stage(file);
        PluginVersionEntity versionEntity = pluginPersistenceService.saveUploadedPackage(
                artifact.metadata(),
                artifact.zipPath(),
                artifact.packageRoot(),
                artifact.validationReportJson(),
                artifact.packageChecksum(),
                artifact.signaturePath().toString(),
                operator.userId(),
                operator.userUuid()
        );
        pluginPersistenceService.replaceDependencies(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getDependencyPlugins(),
                operator.userId(),
                operator.userUuid()
        );
        pluginPersistenceService.replacePermissionRelations(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getVersion(),
                artifact.metadata().getRequiredPermissions(),
                operator.userId(),
                operator.userUuid()
        );
        pluginPersistenceService.replaceMenuRelations(
                artifact.metadata().getPluginCode(),
                artifact.metadata().getVersion(),
                artifact.metadata().getMenuDeclarations(),
                operator.userId(),
                operator.userUuid()
        );
        log(artifact.metadata().getPluginCode(), artifact.metadata().getVersion(), "UPLOAD", "VERIFIED", "SUCCESS", "Plugin package uploaded and verified", null, operator);
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
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_INSTALL);
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        PluginDTO.PluginPackageMetadata metadata = parseMetadata(versionEntity);
        validateDependencies(metadata);
        Path versionHome = pluginArtifactLoader.installToVersionHome(pluginCode, version, Path.of(versionEntity.getStagedPath()));
        log(pluginCode, version, "INSTALL", "INSTALLED", "SUCCESS", "Plugin files installed", null, operator);
        pluginMigrationService.executeUpMigrations(pluginCode, version, versionHome, operator.userId(), operator.userUuid());
        log(pluginCode, version, "INSTALL", "MIGRATED", "SUCCESS", "Plugin private migrations completed", null, operator);
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
                1,
                operator.userId(),
                operator.userUuid()
        );
        if (pluginPersistenceService.listInstalledVersions(pluginCode).stream().noneMatch(item -> item.getIsActive() != null && item.getIsActive() == 1)) {
            pluginRegistry.activate(pluginCode, version);
            pluginPersistenceService.activateVersion(pluginCode, version, operator.userId(), operator.userUuid());
            bumpBootstrapVersions(pluginCode, "plugin.version.auto-activated");
        }
        log(pluginCode, version, "INSTALL", "LOADED", "SUCCESS", "Plugin runtime loaded", null, operator);
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> version.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PluginVO.PluginVersionVO upgrade(String pluginCode, String version, CurrentUser currentUser) {
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_UPGRADE);
        ensureLoaded(pluginCode, version, currentUser, PERMISSION_PLUGIN_MANAGEMENT_UPGRADE);
        pluginRegistry.activate(pluginCode, version);
        pluginPersistenceService.activateVersion(pluginCode, version, operator.userId(), operator.userUuid());
        bumpBootstrapVersions(pluginCode, "plugin.version.upgraded");
        log(pluginCode, version, "UPGRADE", "ENABLED", "SUCCESS", "Plugin active version switched", null, operator);
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> version.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public PluginVO.PluginVersionVO rollback(String pluginCode, String targetVersion, CurrentUser currentUser) {
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_ROLLBACK);
        ensureLoaded(pluginCode, targetVersion, currentUser, PERMISSION_PLUGIN_MANAGEMENT_ROLLBACK);
        pluginRegistry.activate(pluginCode, targetVersion);
        pluginPersistenceService.activateVersion(pluginCode, targetVersion, operator.userId(), operator.userUuid());
        bumpBootstrapVersions(pluginCode, "plugin.version.rolled-back");
        log(pluginCode, targetVersion, "ROLLBACK", "ROLLED_BACK", "SUCCESS", "Plugin rolled back to target version", null, operator);
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> targetVersion.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    public void enable(PluginDTO.EnableRequest request, CurrentUser currentUser) {
        try {
            TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_ENABLE);
            String resolvedVersion = request.getVersion();
            if (resolvedVersion == null || resolvedVersion.isBlank()) {
                resolvedVersion = pluginRegistry.findActiveVersion(request.getPluginCode())
                        .orElseGet(() -> pluginPersistenceService.listInstalledVersions(request.getPluginCode()).stream()
                                .findFirst()
                                .map(PluginVersionEntity::getVersion)
                                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "No active plugin version found")));
            }
            final String version = resolvedVersion;
            ensureLoaded(request.getPluginCode(), version, currentUser, PERMISSION_PLUGIN_MANAGEMENT_ENABLE);
            enforceEmailRequirementIfNeeded(request.getPluginCode(), version, currentUser);
            pluginMigrationService.executeUpMigrations(request.getPluginCode(), version, resolveVersionHome(request.getPluginCode(), version), operator.userId(), operator.userUuid());
            transactionTemplate.executeWithoutResult(status -> {
                PluginActivationAggregate pluginActivation = new PluginActivationAggregate(request.getPluginCode(), false);
                pluginActivation.enable(version, operator.userId(), operator.userUuid());
                pluginPersistenceService.enablePlugin(
                        request.getPluginCode(),
                        version,
                        request.getConfigJson(),
                        operator.userId(),
                        operator.userUuid()
                );
                invokeBuiltinEnableHook(request.getPluginCode(), operator);
                pluginPersistenceService.registerPluginPermissions(request.getPluginCode(), version);
                pluginPersistenceService.updateVersionStatus(request.getPluginCode(), version, "LOADED", "LOADED", "HEALTHY", "ENABLED", "READY", operator.userId(), operator.userUuid());
                pluginPersistenceService.bumpBootstrapVersion("plugin.enabled");
                invalidatePluginBootstrapCaches();
                logPluginActivationDomainEvents(pluginActivation.pullDomainEvents(), version, operator);
            });
            safeLog(request.getPluginCode(), version, "ENABLE", "ENABLED", "SUCCESS", "Platform plugin enabled", null, operator);
        } catch (BizException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Enable plugin failed: " + rootCauseMessage(throwable));
        }
    }

    @Transactional
    public void disable(PluginDTO.DisableRequest request, CurrentUser currentUser) {
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_DISABLE);
        PluginVersionEntity enabledVersion = pluginPersistenceService.findEnabledVersion(request.getPluginCode())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "Plugin is not enabled"));
        PluginVO.PluginStatusVO pluginStatus = pluginPersistenceService.pluginStatus(request.getPluginCode()).orElse(null);
        if (Boolean.TRUE.equals(request.getPurgeData())
                && (pluginStatus == null || !Boolean.TRUE.equals(pluginStatus.getSupportsDataPurge()))) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Plugin does not support data purge on disable");
        }
        PluginActivationAggregate pluginActivation = new PluginActivationAggregate(request.getPluginCode(), true);
        pluginActivation.disable(Boolean.TRUE.equals(request.getPurgeData()) ? "purge-data" : "disable", operator.userId(), operator.userUuid());
        pluginPersistenceService.disablePlugin(request.getPluginCode(), operator.userId(), operator.userUuid());
        invokeBuiltinDisableHook(request.getPluginCode(), operator);
        pluginPersistenceService.bumpBootstrapVersion("plugin.disabled");
        invalidatePluginBootstrapCaches();
        boolean purgeData = Boolean.TRUE.equals(request.getPurgeData());
        if (purgeData) {
            pluginMigrationService.executeDownMigrations(
                    request.getPluginCode(),
                    enabledVersion.getVersion(),
                    resolveVersionHome(request.getPluginCode(), enabledVersion.getVersion()),
                    operator.userId(),
                    operator.userUuid()
            );
        }
        pluginPersistenceService.updateVersionStatus(
                request.getPluginCode(),
                enabledVersion.getVersion(),
                "LOADED",
                purgeData ? "UNLOADED" : "LOADED",
                "HEALTHY",
                "DISABLED",
                purgeData ? "REMOVED" : "READY",
                operator.userId(),
                operator.userUuid()
        );
        logPluginActivationDomainEvents(pluginActivation.pullDomainEvents(), enabledVersion.getVersion(), operator);
        systemInternalApi.invalidatePermissionSnapshot();
        safeLog(
                request.getPluginCode(),
                enabledVersion.getVersion(),
                "DISABLE",
                "DISABLED",
                "SUCCESS",
                purgeData ? "Platform plugin disabled and data purged" : "Platform plugin disabled",
                null,
                operator
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

    public List<PluginVO.PluginAvailabilityVO> availablePlugins() {
        long bootstrapVersion = readPluginBootstrapVersion();
        return availablePlugins(bootstrapVersion);
    }

    public List<PluginVO.PluginAvailabilityVO> currentAvailablePlugins(List<String> permissions) {
        return filterAvailablePluginMenus(availablePlugins(), normalizePermissionSet(permissions));
    }

    private List<PluginVO.PluginAvailabilityVO> availablePlugins(long bootstrapVersion) {
        pruneStalePluginBootstrapCaches(bootstrapVersion);
        CachedAvailablePlugins cached = availablePluginsCache.get(GLOBAL_PLUGIN_SCOPE_KEY);
        if (cached != null && cached.version() == bootstrapVersion) {
            availablePluginsCacheHits.increment();
            return new ArrayList<>(cached.availablePlugins());
        }
        availablePluginsCacheMisses.increment();
        List<PluginVO.PluginAvailabilityVO> fromPersistence = pluginPersistenceService.listAvailablePlugins();
        if (fromPersistence == null || fromPersistence.isEmpty()) {
            CachedAvailablePlugins emptySnapshot = new CachedAvailablePlugins(bootstrapVersion, List.of());
            availablePluginsCache.put(GLOBAL_PLUGIN_SCOPE_KEY, emptySnapshot);
            return List.of();
        }
        List<PluginVO.PluginAvailabilityVO> result = new ArrayList<>(fromPersistence.size());
        for (PluginVO.PluginAvailabilityVO plugin : fromPersistence) {
            try {
                populateRuntimeMetadata(plugin);
                result.add(plugin);
            } catch (BizException exception) {
                log.warn("Skipping plugin {} {} because runtime files are invalid: {}", plugin.getPluginCode(), plugin.getVersion(), exception.getMessage());
            } catch (Exception exception) {
                log.warn("Skipping plugin {} {} because runtime metadata failed to load", plugin.getPluginCode(), plugin.getVersion(), exception);
            }
        }
        CachedAvailablePlugins snapshot = new CachedAvailablePlugins(bootstrapVersion, result);
        availablePluginsCache.put(GLOBAL_PLUGIN_SCOPE_KEY, snapshot);
        return new ArrayList<>(result);
    }

    public PluginVO.PluginStatusVO status(String pluginCode) {
        PluginVO.PluginStatusVO status = pluginPersistenceService.pluginStatus(pluginCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Plugin not found"));
        if (status.getRuntimeContributions() == null || status.getRuntimeContributions().isEmpty()) {
            status.setRuntimeContributions(resolveRuntimeContributions(pluginCode));
        }
        return status;
    }

    @Transactional
    public void uninstall(String pluginCode, boolean removeData, CurrentUser currentUser) {
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_DISABLE);
        if (isBuiltinCorePlugin(pluginCode)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Built-in plugins cannot be uninstalled; disable the plugin instead");
        }
        List<PluginVersionEntity> versions = pluginPersistenceService.listInstalledVersions(pluginCode);
        for (PluginVersionEntity versionEntity : versions) {
            if (removeData) {
                pluginMigrationService.executeDownMigrations(
                        pluginCode,
                        versionEntity.getVersion(),
                        resolveVersionHome(pluginCode, versionEntity.getVersion()),
                        operator.userId(),
                        operator.userUuid()
                );
            }
            removePluginVersionArtifacts(versionEntity);
            try {
                pluginRegistry.unload(pluginCode, versionEntity.getVersion());
            } catch (Exception exception) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin runtime unload failed: " + exception.getMessage());
            }
        }
        if (removeData) {
            pluginPersistenceService.purgePluginData(pluginCode, operator.userId(), operator.userUuid());
        } else {
            pluginPersistenceService.uninstallPlugin(pluginCode, operator.userId(), operator.userUuid());
        }
        systemInternalApi.invalidatePermissionSnapshot();
        pluginPersistenceService.bumpBootstrapVersion("plugin.uninstalled");
        invalidatePluginBootstrapCaches();
        safeLog(
                pluginCode,
                versions.isEmpty() ? null : versions.get(0).getVersion(),
                "UNINSTALL",
                "REMOVED",
                "SUCCESS",
                removeData ? "Plugin uninstalled and data purged" : "Plugin uninstalled",
                null,
                operator
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

    public Optional<PluginRuntimeDescriptor> findRuntimeDescriptor(String pluginCode) {
        return availablePlugins().stream()
                .filter(item -> pluginCode.equals(item.getPluginCode()))
                .findFirst()
                .flatMap(item -> pluginRegistry.find(item.getPluginCode(), item.getVersion()));
    }

    public Optional<PluginRuntimeDescriptor> findActiveRuntimeDescriptor(String pluginCode) {
        return pluginRegistry.findActiveVersion(pluginCode).flatMap(version -> pluginRegistry.find(pluginCode, version));
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
        TrustedOperator operator = requireTrustedOperator(currentUser, PERMISSION_PLUGIN_MANAGEMENT_ENABLE);
        if (!Boolean.TRUE.equals(systemInternalApi.userHasEmail(operator.userId(), operator.userUuid()))) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Please provide an email before enabling this verification method");
        }
    }

    public PluginRuntimeDescriptor requireRuntime(String pluginCode) {
        PluginVersionEntity enabledVersion = pluginPersistenceService.findEnabledVersion(pluginCode)
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "Plugin is not enabled"));
        ensureLoaded(pluginCode, enabledVersion.getVersion());
        return pluginRegistry.find(pluginCode, enabledVersion.getVersion())
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin runtime does not exist"));
    }

    public List<Map<String, Object>> pluginMenus(Set<String> permissions) {
        return pluginActivationMenus(availablePlugins(), permissions);
    }

    public Map<String, Object> currentBootstrap(List<String> permissions) {
        return currentBootstrap(permissions, null);
    }

    public Map<String, Object> currentBootstrap(List<String> permissions, String permissionsVersion) {
        return currentBootstrap(permissions, permissionsVersion, null, null);
    }

    public Map<String, Object> currentBootstrap(
            List<String> permissions,
            String permissionsVersion,
            Long pluginBootstrapVersion,
            Long platformMenuTreeVersion
    ) {
        Set<String> permissionSet = normalizePermissionSet(permissions);
        long resolvedPluginBootstrapVersion = resolveVersion(pluginBootstrapVersion, this::readPluginBootstrapVersion);
        MenuCompilationVersion menuCompilationVersion = currentMenuCompilationVersion(
                resolvedPluginBootstrapVersion,
                platformMenuTreeVersion
        );
        pruneStalePluginBootstrapCaches(resolvedPluginBootstrapVersion, menuCompilationVersion);
        String permissionCacheKey = permissionCacheKey(permissionSet, permissionsVersion);
        BootstrapCacheKey cacheKey = new BootstrapCacheKey(GLOBAL_PLUGIN_SCOPE_KEY, menuCompilationVersion, permissionCacheKey);
        CachedCurrentBootstrap cached = currentBootstrapCache.get(cacheKey);
        if (cached != null) {
            currentBootstrapCacheHits.increment();
            return cached.bootstrapPayload();
        }
        currentBootstrapCacheMisses.increment();
        CachedCurrentBootstrap snapshot = currentBootstrapCache.computeIfAbsent(
                cacheKey,
                ignored -> {
                    List<PluginVO.PluginAvailabilityVO> availablePlugins = availablePlugins(resolvedPluginBootstrapVersion);
                    List<PluginVO.PluginAvailabilityVO> visiblePlugins = filterAvailablePluginMenus(availablePlugins, permissionSet);
                    Map<String, Object> payload = Map.of(
                            "menuTree", currentMenus(menuCompilationVersion, availablePlugins, permissionSet, permissionCacheKey),
                            "availablePlugins", visiblePlugins
                    );
                    return new CachedCurrentBootstrap(menuCompilationVersion, payload);
                }
        );
        return snapshot.bootstrapPayload();
    }

    private void bumpBootstrapVersions(String pluginCode, String eventKey) {
        pluginPersistenceService.bumpBootstrapVersion(eventKey);
        invalidatePluginBootstrapCaches();
    }

    private List<Map<String, Object>> pluginActivationMenus(List<PluginVO.PluginAvailabilityVO> availablePlugins, Set<String> permissions) {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (PluginVO.PluginAvailabilityVO plugin : availablePlugins) {
            for (Map<String, Object> menu : pluginMenuSnapshot(plugin)) {
                String permissionKey = (String) menu.get("permissionKey");
                if (permissionKey == null || permissions.contains("*") || permissions.contains(permissionKey)) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    private List<PluginVO.PluginAvailabilityVO> filterAvailablePluginMenus(
            List<PluginVO.PluginAvailabilityVO> availablePlugins,
            Set<String> permissions
    ) {
        if (availablePlugins == null || availablePlugins.isEmpty()) {
            return List.of();
        }
        List<PluginVO.PluginAvailabilityVO> result = new ArrayList<>(availablePlugins.size());
        for (PluginVO.PluginAvailabilityVO plugin : availablePlugins) {
            PluginVO.PluginAvailabilityVO copy = copyAvailability(plugin);
            copy.setMenus(pluginMenuSnapshot(plugin).stream()
                    .filter(menu -> isMenuAllowed(menu, permissions))
                    .map(menu -> new LinkedHashMap<>(menu))
                    .map(item -> (Map<String, Object>) item)
                    .toList());
            result.add(copy);
        }
        return result;
    }

    private PluginVO.PluginAvailabilityVO copyAvailability(PluginVO.PluginAvailabilityVO plugin) {
        PluginVO.PluginAvailabilityVO copy = new PluginVO.PluginAvailabilityVO();
        copy.setPluginCode(plugin.getPluginCode());
        copy.setPluginName(plugin.getPluginName());
        copy.setVersion(plugin.getVersion());
        copy.setManifestPath(plugin.getManifestPath());
        copy.setSharedDeps(plugin.getSharedDeps() == null ? List.of() : List.copyOf(plugin.getSharedDeps()));
        copy.setRoutes(plugin.getRoutes() == null ? List.of() : List.copyOf(plugin.getRoutes()));
        copy.setMenus(plugin.getMenus() == null ? List.of() : copyMenus(plugin.getMenus()));
        copy.setLifecycleStatus(plugin.getLifecycleStatus());
        copy.setSchemaStatus(plugin.getSchemaStatus());
        copy.setSupportsHotDisable(plugin.getSupportsHotDisable());
        copy.setSupportsDataPurge(plugin.getSupportsDataPurge());
        copy.setRuntimeContributions(plugin.getRuntimeContributions() == null ? List.of() : List.copyOf(plugin.getRuntimeContributions()));
        return copy;
    }

    private List<Map<String, Object>> pluginActivationMenus(List<PluginVO.PluginAvailabilityVO> availablePlugins) {
        List<Map<String, Object>> menus = new ArrayList<>();
        for (PluginVO.PluginAvailabilityVO plugin : availablePlugins) {
            menus.addAll(pluginMenuSnapshot(plugin));
        }
        return menus;
    }

    public List<Map<String, Object>> currentMenus(List<String> permissions) {
        return currentMenus(permissions, null);
    }

    public List<Map<String, Object>> currentMenus(List<String> permissions, String permissionsVersion) {
        Set<String> permissionSet = normalizePermissionSet(permissions);
        long pluginBootstrapVersion = readPluginBootstrapVersion();
        MenuCompilationVersion menuCompilationVersion = currentMenuCompilationVersion(pluginBootstrapVersion);
        pruneStalePluginBootstrapCaches(pluginBootstrapVersion, menuCompilationVersion);
        List<PluginVO.PluginAvailabilityVO> availablePlugins = availablePlugins();
        return currentMenus(
                menuCompilationVersion,
                availablePlugins,
                permissionSet,
                permissionCacheKey(permissionSet, permissionsVersion)
        );
    }

    private List<Map<String, Object>> currentMenus(
            MenuCompilationVersion menuCompilationVersion,
            List<PluginVO.PluginAvailabilityVO> availablePlugins,
            Set<String> permissionSet,
            String permissionCacheKey
    ) {
        VisibleMenuTreeCacheKey cacheKey = new VisibleMenuTreeCacheKey(menuCompilationVersion, permissionCacheKey);
        CachedVisibleMenuTree cached = visibleMenuTreeCache.get(cacheKey);
        if (cached != null) {
            return copyMenus(cached.menus());
        }

        CachedVisibleMenuTree snapshot = visibleMenuTreeCache.computeIfAbsent(
                cacheKey,
                ignored -> {
                    List<Map<String, Object>> compiledMenus = compiledMenuTree(menuCompilationVersion, availablePlugins);
                    List<Map<String, Object>> visibleMenus = pruneMenuTree(compiledMenus, permissionSet);
                    return new CachedVisibleMenuTree(menuCompilationVersion, copyMenus(visibleMenus));
                }
        );
        return copyMenus(snapshot.menus());
    }

    public Path resolveManifestPath(String pluginCode, String version) {
        if (isBuiltinCorePlugin(pluginCode)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Built-in plugin does not provide an independent frontend manifest");
        }
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (versionEntity.getFrontendManifestPath() == null || versionEntity.getFrontendManifestPath().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin manifest does not exist");
        }
        Path manifestPath = Path.of(versionEntity.getFrontendManifestPath());
        if (!Files.exists(manifestPath)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin manifest does not exist");
        }
        return manifestPath;
    }

    public Path resolvePluginAssetPath(String pluginCode, String version, String relativePath) {
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin asset does not exist");
        }
        Path versionHome = Path.of(versionEntity.getArtifactPath());
        Path resolved = versionHome.resolve("lumira-ui").resolve(relativePath).normalize();
        if (!resolved.startsWith(versionHome.resolve("lumira-ui"))) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin asset does not exist");
        }
        if (!Files.exists(resolved)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plugin asset does not exist");
        }
        return resolved;
    }

    private void ensureLoaded(String pluginCode, String version) {
        ensureLoaded(pluginCode, version, null);
    }

    private void ensureLoaded(String pluginCode, String version, CurrentUser currentUser) {
        ensureLoaded(pluginCode, version, currentUser, PERMISSION_PLUGIN_MANAGEMENT_INSTALL);
    }

    private void ensureLoaded(String pluginCode, String version, CurrentUser currentUser, String requiredPermission) {
        if (isBuiltinCorePlugin(pluginCode)) {
            return;
        }
        if (pluginRegistry.find(pluginCode, version).isPresent()) {
            return;
        }
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin version is not installed; install it before enabling");
        }
        if (!Files.exists(Path.of(versionEntity.getArtifactPath()))) {
            if (currentUser == null) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin runtime files are missing; reinstall the plugin before use");
            }
            installWithPermission(pluginCode, version, currentUser, requiredPermission);
            return;
        }
        PluginRuntimeDescriptor descriptor = pluginRuntimeLoader.load(parseMetadata(versionEntity), Path.of(versionEntity.getArtifactPath()));
        pluginRegistry.register(descriptor);
    }

    private PluginVO.PluginVersionVO installWithPermission(String pluginCode, String version, CurrentUser currentUser, String requiredPermission) {
        TrustedOperator operator = requireTrustedOperator(currentUser, requiredPermission);
        PluginVersionEntity versionEntity = requireVersion(pluginCode, version);
        PluginDTO.PluginPackageMetadata metadata = parseMetadata(versionEntity);
        validateDependencies(metadata);
        Path versionHome = pluginArtifactLoader.installToVersionHome(pluginCode, version, Path.of(versionEntity.getStagedPath()));
        log(pluginCode, version, "INSTALL", "INSTALLED", "SUCCESS", "Plugin files installed", null, operator);
        pluginMigrationService.executeUpMigrations(pluginCode, version, versionHome, operator.userId(), operator.userUuid());
        log(pluginCode, version, "INSTALL", "MIGRATED", "SUCCESS", "Plugin private migrations completed", null, operator);
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
                1,
                operator.userId(),
                operator.userUuid()
        );
        if (pluginPersistenceService.listInstalledVersions(pluginCode).stream().noneMatch(item -> item.getIsActive() != null && item.getIsActive() == 1)) {
            pluginRegistry.activate(pluginCode, version);
            pluginPersistenceService.activateVersion(pluginCode, version, operator.userId(), operator.userUuid());
            bumpBootstrapVersions(pluginCode, "plugin.version.auto-activated");
        }
        log(pluginCode, version, "INSTALL", "LOADED", "SUCCESS", "Plugin runtime loaded", null, operator);
        return pluginPersistenceService.listVersions(pluginCode).stream()
                .filter(item -> version.equals(item.getVersion()))
                .findFirst()
                .orElseThrow();
    }

    private TrustedOperator requireTrustedOperator(CurrentUser currentUser, String requiredPermission) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator is required");
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted operator is required");
        }
        SystemUserSnapshotDTO snapshot = systemInternalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator is disabled");
        }
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, snapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, snapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator permissions are unavailable");
        }
        List<String> permissions = permissionSnapshot.permissions() == null ? List.of() : permissionSnapshot.permissions();
        if (!permissions.contains("*") && !permissions.contains(requiredPermission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + requiredPermission);
        }
        return new TrustedOperator(snapshot.userId(), snapshot.userUuid().trim());
    }

    private record TrustedOperator(Long userId, String userUuid) {
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
                        "Missing dependency plugin " + dependency.getPluginCode() + " with minimum version " + dependency.getMinVersion()
                );
            }
        }
    }

    private PluginDTO.PluginPackageMetadata parseMetadata(PluginVersionEntity versionEntity) {
        try {
            return objectMapper.readValue(versionEntity.getMetadataJson(), PluginDTO.PluginPackageMetadata.class);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "Failed to parse plugin metadata");
        }
    }

    private void populateRuntimeMetadata(PluginVO.PluginAvailabilityVO plugin) throws Exception {
        PluginVO.PluginStatusVO status = pluginPersistenceService.pluginStatus(plugin.getPluginCode())
                .orElse(null);
        BuiltinPluginRuntime builtinRuntime = builtinPluginRuntime(plugin.getPluginCode());
        if (builtinRuntime != null) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(builtinRuntime.routes());
            plugin.setMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
            plugin.setLifecycleStatus(status == null ? "ENABLED" : status.getLifecycleStatus());
            plugin.setSchemaStatus(status == null ? "READY" : status.getSchemaStatus());
            plugin.setSupportsHotDisable(status == null ? Boolean.TRUE : status.getSupportsHotDisable());
            plugin.setSupportsDataPurge(status == null ? Boolean.TRUE : status.getSupportsDataPurge());
            plugin.setRuntimeContributions(status == null ? builtinRuntime.runtimeContributions() : status.getRuntimeContributions());
            return;
        }
        PluginVersionEntity versionEntity = requireVersion(plugin.getPluginCode(), plugin.getVersion());
        if (versionEntity.getArtifactPath() == null || versionEntity.getArtifactPath().isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin runtime directory does not exist");
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
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin frontend manifest is missing");
        }
        if (versionHome == null || !Files.exists(versionHome)) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin runtime directory does not exist");
        }
        if (manifest.getAssets() == null || manifest.getAssets().isEmpty()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin frontend manifest is missing assets");
        }
        for (String asset : manifest.getAssets()) {
            if (asset == null || asset.isBlank()) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin frontend manifest contains an empty asset path");
            }
            Path assetPath = versionHome.resolve("lumira-ui").resolve(asset).normalize();
            if (!assetPath.startsWith(versionHome.resolve("lumira-ui")) || !Files.exists(assetPath)) {
                throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "Plugin asset does not exist: " + asset);
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
        List<Map<String, Object>> template = systemInternalApi.builtinMenus().stream().map(this::toMenuMap).toList();
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
        if (StringUtils.hasText(permissionKey)) {
            return permissions.contains("*") || permissions.contains(permissionKey.trim());
        }
        Object menuCode = menu.get("menuCode");
        return menuCode instanceof String code && AUTHENTICATED_PERMISSIONLESS_MENU_CODES.contains(code.trim());
    }

    private boolean isBuiltinCorePlugin(String pluginCode) {
        return builtinPluginRuntime(pluginCode) != null;
    }

    private void invokeBuiltinEnableHook(String pluginCode, TrustedOperator operator) {
        BuiltinPluginLifecycleHook hook = findBuiltinLifecycleHook(pluginCode);
        if (hook != null) {
            hook.onEnable(new BuiltinPluginLifecycleHook.PluginLifecycleContext(operator.userId(), operator.userUuid()));
        }
    }

    private void invokeBuiltinDisableHook(String pluginCode, TrustedOperator operator) {
        BuiltinPluginLifecycleHook hook = findBuiltinLifecycleHook(pluginCode);
        if (hook != null) {
            hook.onDisable(new BuiltinPluginLifecycleHook.PluginLifecycleContext(operator.userId(), operator.userUuid()));
        }
    }

    private BuiltinPluginLifecycleHook findBuiltinLifecycleHook(String pluginCode) {
        BuiltinPluginLifecycleHook hook = builtinPluginLifecycleHooks.stream()
                .filter(candidate -> candidate != null && pluginCode.equals(candidate.pluginCode()))
                .findFirst()
                .orElse(null);
        if (BUILTIN_MOCK_PAYMENT_PLUGIN.equals(pluginCode) && hook == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Built-in mock payment lifecycle is unavailable");
        }
        return hook;
    }

    private BuiltinPluginRuntime builtinPluginRuntime(String pluginCode) {
        return BUILTIN_PLUGIN_RUNTIMES.get(pluginCode);
    }

    private void invalidatePluginBootstrapCaches() {
        availablePluginsCache.remove(GLOBAL_PLUGIN_SCOPE_KEY);
        compiledMenuTreeCache.clear();
        visibleMenuTreeCache.clear();
        readModelVersionCache.invalidate(READ_MODEL_CACHE_KEY_PLUGIN_BOOTSTRAP);
        readModelVersionCache.invalidate(READ_MODEL_CACHE_KEY_PLATFORM_MENU_TREE);
        currentBootstrapCache.keySet().removeIf(key -> GLOBAL_PLUGIN_SCOPE_KEY.equals(key.scopeKey()));
    }

    private void pruneStalePluginBootstrapCaches(long bootstrapVersion) {
        CachedAvailablePlugins cachedAvailablePlugins = availablePluginsCache.get(GLOBAL_PLUGIN_SCOPE_KEY);
        if (cachedAvailablePlugins != null && cachedAvailablePlugins.version() != bootstrapVersion) {
            availablePluginsCache.remove(GLOBAL_PLUGIN_SCOPE_KEY, cachedAvailablePlugins);
        }
        compiledMenuTreeCache.keySet().removeIf(version -> version.pluginBootstrapVersion() != bootstrapVersion);
        visibleMenuTreeCache.keySet().removeIf(key -> key.version().pluginBootstrapVersion() != bootstrapVersion);
        currentBootstrapCache.keySet().removeIf(
                key -> GLOBAL_PLUGIN_SCOPE_KEY.equals(key.scopeKey()) && key.version().pluginBootstrapVersion() != bootstrapVersion
        );
    }

    private void pruneStalePluginBootstrapCaches(long bootstrapVersion, MenuCompilationVersion menuCompilationVersion) {
        pruneStalePluginBootstrapCaches(bootstrapVersion);
        compiledMenuTreeCache.keySet().removeIf(version -> !version.equals(menuCompilationVersion));
        visibleMenuTreeCache.keySet().removeIf(key -> !key.version().equals(menuCompilationVersion));
        currentBootstrapCache.keySet().removeIf(
                key -> GLOBAL_PLUGIN_SCOPE_KEY.equals(key.scopeKey()) && !key.version().equals(menuCompilationVersion)
        );
    }

    private String permissionCacheKey(Set<String> permissions, String permissionsVersion) {
        String permissionSignature = permissionSignature(permissions);
        if (StringUtils.hasText(permissionsVersion)) {
            return "perm-version:" + permissionsVersion.trim() + ":perm-signature:" + permissionSignature;
        }
        return "perm-signature:" + permissionSignature;
    }

    private String permissionSignature(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "NONE";
        }
        return permissions.stream()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private long readPluginBootstrapVersion() {
        long fallback = 0L;
        CachedAvailablePlugins cachedAvailablePlugins = availablePluginsCache.get(GLOBAL_PLUGIN_SCOPE_KEY);
        if (cachedAvailablePlugins != null) {
            fallback = cachedAvailablePlugins.version();
        }
        return readReadModelVersion(
                READ_MODEL_CACHE_KEY_PLUGIN_BOOTSTRAP,
                READ_MODEL_CONTEXT_PLUGIN,
                READ_MODEL_SCOPE_PLUGIN_BOOTSTRAP,
                fallback,
                "plugin bootstrap"
        );
    }

    private long readPlatformMenuTreeVersion() {
        return readReadModelVersion(
                READ_MODEL_CACHE_KEY_PLATFORM_MENU_TREE,
                READ_MODEL_CONTEXT_PLATFORM,
                READ_MODEL_SCOPE_PLATFORM_MENU_TREE,
                0L,
                "platform menu tree"
        );
    }

    private MenuCompilationVersion currentMenuCompilationVersion(long pluginBootstrapVersion) {
        return new MenuCompilationVersion(pluginBootstrapVersion, readPlatformMenuTreeVersion());
    }

    private MenuCompilationVersion currentMenuCompilationVersion(long pluginBootstrapVersion, Long platformMenuTreeVersion) {
        return new MenuCompilationVersion(
                pluginBootstrapVersion,
                resolveVersion(platformMenuTreeVersion, this::readPlatformMenuTreeVersion)
        );
    }

    private long resolveVersion(Long suppliedVersion, java.util.function.LongSupplier fallback) {
        if (suppliedVersion != null && suppliedVersion >= 0L) {
            return suppliedVersion;
        }
        return fallback.getAsLong();
    }

    private long readReadModelVersion(
            String cacheKey,
            String context,
            String scope,
            long fallback,
            String label
    ) {
        try {
            ReadModelVersionCache.ReadResult result = readModelVersionCache.read(
                    cacheKey,
                    READ_MODEL_VERSION_CACHE_TTL_MILLIS,
                    () -> {
                        try {
                            Long actualVersion = systemInternalApi.readModelVersion(context, scope);
                            return actualVersion == null ? fallback : actualVersion;
                        } catch (Exception exception) {
                            log.warn("Failed to read {} read-model version context={} scope={}", label, context, scope, exception);
                            return fallback;
                        }
                    }
            );
            if (result.cacheHit()) {
                readModelVersionCacheHits.increment();
            } else {
                readModelVersionCacheMisses.increment();
            }
            return result.version() == null ? fallback : result.version();
        } catch (RuntimeException exception) {
            readModelVersionCacheMisses.increment();
            log.warn("Failed to read {} read-model version context={} scope={}", label, context, scope, exception);
            return fallback;
        }
    }

    private List<Map<String, Object>> compiledMenuTree(
            MenuCompilationVersion menuCompilationVersion,
            List<PluginVO.PluginAvailabilityVO> availablePlugins
    ) {
        CachedCompiledMenuTree cached = compiledMenuTreeCache.get(menuCompilationVersion);
        if (cached != null) {
            return copyMenus(cached.menus());
        }

        CachedCompiledMenuTree snapshot = compiledMenuTreeCache.computeIfAbsent(
                menuCompilationVersion,
                ignored -> {
                    List<Map<String, Object>> mergedMenus = mergeMenus(builtinMenus(), pluginActivationMenus(availablePlugins));
                    return new CachedCompiledMenuTree(menuCompilationVersion, copyMenus(mergedMenus));
                }
        );
        return copyMenus(snapshot.menus());
    }

    private List<Map<String, Object>> pluginMenuSnapshot(PluginVO.PluginAvailabilityVO plugin) {
        if (plugin == null) {
            return List.of();
        }
        List<Map<String, Object>> menus = plugin.getMenus();
        if (menus != null) {
            return copyMenus(menus);
        }
        return copyMenus(buildPluginMenus(plugin.getPluginCode(), plugin.getVersion()));
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
        BuiltinPluginRuntime builtinRuntime = builtinPluginRuntime(pluginCode);
        if (builtinRuntime != null) {
            return builtinRuntime.runtimeContributions();
        }
        return List.of();
    }

    private PluginVersionEntity requireVersion(String pluginCode, String version) {
        return pluginPersistenceService.findVersion(pluginCode, version)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Plugin version not found"));
    }

    private void log(
            String pluginCode,
            String version,
            String operationType,
            String lifecycleStatus,
            String resultStatus,
            String detailMessage,
            String failureStack,
            TrustedOperator operator
    ) {
        pluginPersistenceService.insertRuntimeLog(
                pluginCode,
                version,
                operationType,
                lifecycleStatus,
                resultStatus,
                detailMessage,
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                failureStack,
                operator.userId(),
                operator.userUuid()
        );
    }

    private void safeLog(
            String pluginCode,
            String version,
            String operationType,
            String lifecycleStatus,
            String resultStatus,
            String detailMessage,
            String failureStack,
            TrustedOperator operator
    ) {
        try {
            log(
                    pluginCode,
                    version,
                    operationType,
                    lifecycleStatus,
                    resultStatus,
                    detailMessage,
                    failureStack,
                    operator
            );
        } catch (Throwable throwable) {
            log.warn("Failed to write plugin runtime log pluginCode={} version={} operationType={}", pluginCode, version, operationType, throwable);
        }
    }

    private void logPluginActivationDomainEvents(List<DomainEvent> events, String version, TrustedOperator operator) {
        if (events == null || events.isEmpty()) {
            return;
        }
        domainEventPublisher.publishAll(events);
        for (DomainEvent event : events) {
            safeLog(
                    event.aggregateId(),
                    version,
                    event.eventType(),
                    "DOMAIN_EVENT",
                    "SUCCESS",
                    event.attributes() == null ? "{}" : event.attributes().toString(),
                    null,
                    operator
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
        private final List<PluginVO.PluginAvailabilityVO> availablePlugins;

        private CachedAvailablePlugins(long version, List<PluginVO.PluginAvailabilityVO> availablePlugins) {
            this.version = version;
            this.availablePlugins = availablePlugins == null ? List.of() : List.copyOf(availablePlugins);
        }

        private long version() {
            return version;
        }

        private List<PluginVO.PluginAvailabilityVO> availablePlugins() {
            return availablePlugins;
        }
    }

    private static final class CachedCurrentBootstrap {
        private final MenuCompilationVersion version;
        private final Map<String, Object> bootstrapPayload;

        private CachedCurrentBootstrap(MenuCompilationVersion version, Map<String, Object> bootstrapPayload) {
            this.version = version;
            this.bootstrapPayload = bootstrapPayload;
        }

        private Map<String, Object> bootstrapPayload() {
            return bootstrapPayload;
        }
    }

    private static final class CachedCompiledMenuTree {
        private final MenuCompilationVersion version;
        private final List<Map<String, Object>> menus;

        private CachedCompiledMenuTree(MenuCompilationVersion version, List<Map<String, Object>> menus) {
            this.version = version;
            this.menus = menus == null ? List.of() : List.copyOf(menus);
        }

        private MenuCompilationVersion version() {
            return version;
        }

        private List<Map<String, Object>> menus() {
            return menus;
        }
    }

    private static final class CachedVisibleMenuTree {
        private final MenuCompilationVersion version;
        private final List<Map<String, Object>> menus;

        private CachedVisibleMenuTree(MenuCompilationVersion version, List<Map<String, Object>> menus) {
            this.version = version;
            this.menus = menus == null ? List.of() : List.copyOf(menus);
        }

        private MenuCompilationVersion version() {
            return version;
        }

        private List<Map<String, Object>> menus() {
            return menus;
        }
    }

    private static final record MenuCompilationVersion(long pluginBootstrapVersion, long platformMenuTreeVersion) {
    }

    private static final record BootstrapCacheKey(String scopeKey, MenuCompilationVersion version, String permissionCacheKey) {
    }

    private static final record VisibleMenuTreeCacheKey(MenuCompilationVersion version, String permissionCacheKey) {
    }

    private record BuiltinPluginRuntime(List<String> routes, List<String> runtimeContributions) {
    }
}
