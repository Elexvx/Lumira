package com.lumira.saas.modules.plugin.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginDefinitionEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginDependencyEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginPermissionRelEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginRuntimeLogEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginSchemaHistoryEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PluginPersistenceService {

    private final PluginPersistenceMapper pluginPersistenceMapper;
    private final SystemInternalApi systemInternalApi;

    public PluginPersistenceService(PluginPersistenceMapper pluginPersistenceMapper, SystemInternalApi systemInternalApi) {
        this.pluginPersistenceMapper = pluginPersistenceMapper;
        this.systemInternalApi = systemInternalApi;
    }

    @Transactional
    public PluginVersionEntity saveUploadedPackage(
            PluginDTO.PluginPackageMetadata metadata,
            Path stagedZipPath,
            Path stagedPackageRoot,
            String validationReportJson,
            String packageChecksum,
            String signaturePath,
            Long operatorId
    ) {
        upsertDefinition(metadata, operatorId);
        PluginVersionEntity version = new PluginVersionEntity();
        version.setPluginCode(metadata.getPluginCode());
        version.setVersion(metadata.getVersion());
        version.setPackagePath(stagedZipPath.toString());
        version.setChecksum(packageChecksum);
        version.setSignaturePath(signaturePath);
        version.setMinPlatformVersion(metadata.getMinPlatformVersion());
        version.setMetadataJson(JsonUtils.toJson(metadata));
        version.setValidationReportJson(validationReportJson);
        version.setStagedPath(stagedPackageRoot.toString());
        version.setCreatedBy(operatorId);
        version.setUpdatedBy(operatorId);
        pluginPersistenceMapper.upsertVersion(version);
        return findVersion(metadata.getPluginCode(), metadata.getVersion()).orElseThrow();
    }

    public Optional<PluginVersionEntity> findVersion(String pluginCode, String version) {
        return Optional.ofNullable(pluginPersistenceMapper.findVersion(pluginCode, version));
    }

    public List<PluginVO.PluginDefinitionVO> listDefinitions() {
        List<PluginVO.PluginDefinitionVO> definitions = pluginPersistenceMapper.listDefinitions();
        definitions.forEach(item -> {
            if (item.getRuntimeContributions() == null) {
                item.setRuntimeContributions(List.of());
            }
        });
        return definitions;
    }

    public List<PluginVO.PluginVersionVO> listVersions(String pluginCode) {
        return pluginPersistenceMapper.listVersions(pluginCode);
    }

    public Map<String, List<PluginVO.PluginVersionVO>> listAllVersions() {
        List<PluginVO.PluginVersionVO> versions = pluginPersistenceMapper.listAllVersions();
        Map<String, List<PluginVO.PluginVersionVO>> result = new LinkedHashMap<>();
        for (PluginVO.PluginVersionVO version : versions) {
            result.computeIfAbsent(version.getPluginCode(), ignored -> new ArrayList<>()).add(version);
        }
        return result;
    }

    public List<PluginVO.PluginRuntimeLogVO> listRuntimeLogs(String pluginCode) {
        return pluginPersistenceMapper.listRuntimeLogs(pluginCode);
    }

    public Optional<PluginVO.PluginStatusVO> pluginStatus(Long tenantId, String pluginCode) {
        PluginVO.PluginStatusVO status = pluginPersistenceMapper.pluginStatus(tenantId, pluginCode);
        if (status == null) {
            return Optional.empty();
        }
        if (status.getRuntimeContributions() == null) {
            status.setRuntimeContributions(List.of());
        }
        return Optional.of(status);
    }

    @Transactional
    public void markInstalled(
            String pluginCode,
            String version,
            Path artifactPath,
            Path frontendManifestPath,
            Path backendJarPath,
            String installStatus,
            String loadStatus,
            String healthStatus,
            Integer rollbackable
    ) {
        pluginPersistenceMapper.markInstalled(
                pluginCode,
                version,
                artifactPath.toString(),
                frontendManifestPath.toString(),
                backendJarPath.toString(),
                installStatus,
                loadStatus,
                healthStatus,
                rollbackable
        );
    }

    @Transactional
    public void updateVersionStatus(
            String pluginCode,
            String version,
            String installStatus,
            String loadStatus,
            String healthStatus,
            String lifecycleStatus,
            String schemaStatus
    ) {
        pluginPersistenceMapper.updateVersionStatus(pluginCode, version, installStatus, loadStatus, healthStatus, lifecycleStatus, schemaStatus);
    }

    @Transactional
    public void activateVersion(String pluginCode, String version) {
        pluginPersistenceMapper.deactivateOtherVersions(pluginCode, version);
        pluginPersistenceMapper.activateVersion(pluginCode, version);
        pluginPersistenceMapper.updateEnabledTenantsVersion(pluginCode, version);
    }

    @Transactional
    public void replaceDependencies(String pluginCode, List<PluginDTO.PluginDependencyDeclaration> dependencies, Long operatorId) {
        pluginPersistenceMapper.deleteDependencies(pluginCode);
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginDependencyDeclaration dependency : dependencies) {
            PluginDependencyEntity entity = new PluginDependencyEntity();
            entity.setPluginCode(pluginCode);
            entity.setDependsOnPluginCode(dependency.getPluginCode());
            entity.setMinVersion(dependency.getMinVersion());
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            pluginPersistenceMapper.insertDependency(entity);
        }
    }

    @Transactional
    public void replacePermissionRelations(
            String pluginCode,
            String version,
            List<PluginDTO.PluginPermissionDeclaration> permissions,
            Long operatorId
    ) {
        pluginPersistenceMapper.deletePermissionRelations(pluginCode, version);
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginPermissionDeclaration permission : permissions) {
            PluginPermissionRelEntity entity = new PluginPermissionRelEntity();
            entity.setPluginCode(pluginCode);
            entity.setPluginVersion(version);
            entity.setPermissionKey(permission.getPermissionKey());
            entity.setPermissionName(permission.getPermissionName());
            entity.setPermissionGroup(permission.getPermissionGroup());
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            pluginPersistenceMapper.insertPermissionRelation(entity);
        }
    }

    @Transactional
    public void replaceMenuRelations(
            String pluginCode,
            String version,
            List<PluginDTO.PluginMenuDeclaration> menus,
            Long operatorId
    ) {
        pluginPersistenceMapper.deleteMenuRelations(pluginCode, version);
        if (menus == null || menus.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginMenuDeclaration menu : menus) {
            PluginMenuRelEntity entity = new PluginMenuRelEntity();
            entity.setPluginCode(pluginCode);
            entity.setPluginVersion(version);
            entity.setMenuCode(menu.getMenuCode());
            entity.setMenuName(menu.getMenuName());
            entity.setRoutePath(menu.getRoutePath());
            entity.setIcon(menu.getIcon());
            entity.setPermissionKey(menu.getPermissionKey());
            entity.setParentMenuCode(menu.getParentMenuCode());
            entity.setSortNo(menu.getSortNo() == null ? 0 : menu.getSortNo());
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            pluginPersistenceMapper.insertMenuRelation(entity);
        }
    }

    public List<PluginMenuRelEntity> listMenuRelations(String pluginCode, String version) {
        return pluginPersistenceMapper.listMenuRelations(pluginCode, version);
    }

    public List<PluginPermissionRelEntity> listPermissionRelations(String pluginCode, String version) {
        return pluginPersistenceMapper.listPermissionRelations(pluginCode, version);
    }

    @Transactional
    public void enablePluginForTenant(Long tenantId, String pluginCode, String version, String configJson, Long operatorId) {
        PluginTenantEntity entity = new PluginTenantEntity();
        entity.setTenantId(tenantId);
        entity.setPluginCode(pluginCode);
        entity.setPluginVersion(version);
        entity.setConfigJson(configJson);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        pluginPersistenceMapper.enablePluginForTenant(entity);
    }

    @Transactional
    public void disablePluginForTenant(Long tenantId, String pluginCode, Long operatorId) {
        pluginPersistenceMapper.disablePluginForTenant(tenantId, pluginCode, operatorId);
    }

    @Transactional
    public void uninstallPlugin(String pluginCode, Long operatorId) {
        pluginPersistenceMapper.markTenantsDeletedByPlugin(pluginCode, operatorId);
        pluginPersistenceMapper.uninstallVersionsByPlugin(pluginCode, operatorId);
        pluginPersistenceMapper.markMenuRelationsDeletedByPlugin(pluginCode, operatorId);
        pluginPersistenceMapper.markPermissionRelationsDeletedByPlugin(pluginCode, operatorId);
        pluginPersistenceMapper.markDependenciesDeletedByPlugin(pluginCode, operatorId);
        pluginPersistenceMapper.markDefinitionDeletedByPlugin(pluginCode, operatorId);
    }

    @Transactional
    public void purgePluginData(String pluginCode, Long operatorId) {
        pluginPersistenceMapper.deleteRuntimeLogsByPlugin(pluginCode);
        pluginPersistenceMapper.deleteSchemaHistoryByPlugin(pluginCode);
        pluginPersistenceMapper.deleteTenantsByPlugin(pluginCode);
        pluginPersistenceMapper.deleteVersionsByPlugin(pluginCode);
        pluginPersistenceMapper.deleteMenuRelationsByPlugin(pluginCode);
        pluginPersistenceMapper.deletePermissionRelationsByPlugin(pluginCode);
        pluginPersistenceMapper.deleteDependenciesByPlugin(pluginCode);
        pluginPersistenceMapper.deleteDefinitionByPlugin(pluginCode);
    }

    public Optional<PluginTenantEntity> findTenantPlugin(Long tenantId, String pluginCode) {
        return Optional.ofNullable(pluginPersistenceMapper.findTenantPlugin(tenantId, pluginCode));
    }

    public List<PluginVO.TenantPluginVO> listTenantPlugins(Long tenantId) {
        return pluginPersistenceMapper.listTenantPlugins(tenantId);
    }

    public List<Long> listTenantIdsForPlugin(String pluginCode) {
        return pluginPersistenceMapper.listTenantIdsForPlugin(pluginCode);
    }

    public void bumpBootstrapVersion(Long tenantId, String eventKey) {
        systemInternalApi.bumpReadModelVersion(tenantId, "plugin", "bootstrap", eventKey);
    }

    public List<PluginVersionEntity> listInstalledVersions(String pluginCode) {
        return pluginPersistenceMapper.listInstalledVersions(pluginCode);
    }

    public boolean hasSuccessfulSchemaHistory(String pluginCode, String pluginVersion, String direction, String stepName) {
        Integer count = pluginPersistenceMapper.hasSuccessfulSchemaHistory(pluginCode, pluginVersion, direction, stepName);
        return count != null && count > 0;
    }

    @Transactional
    public void insertSchemaHistory(String pluginCode, String pluginVersion, String stepName, String direction, String scriptPath, String executionStatus, String detailMessage, Long operatorId) {
        PluginSchemaHistoryEntity entity = new PluginSchemaHistoryEntity();
        entity.setPluginCode(pluginCode);
        entity.setPluginVersion(pluginVersion);
        entity.setStepName(stepName);
        entity.setDirection(direction);
        entity.setScriptPath(scriptPath);
        entity.setExecutionStatus(executionStatus);
        entity.setDetailMessage(detailMessage);
        entity.setCreatedBy(operatorId);
        pluginPersistenceMapper.insertSchemaHistory(entity);
    }

    @Transactional
    public void insertRuntimeLog(
            Long tenantId,
            String pluginCode,
            String version,
            String operationType,
            String lifecycleStatus,
            String resultStatus,
            String detailMessage,
            String requestId,
            String traceId,
            String failureStack,
            Long operatorId
    ) {
        PluginRuntimeLogEntity entity = new PluginRuntimeLogEntity();
        entity.setTenantId(tenantId);
        entity.setPluginCode(pluginCode);
        entity.setPluginVersion(version);
        entity.setOperationType(operationType);
        entity.setLifecycleStatus(lifecycleStatus);
        entity.setResultStatus(resultStatus);
        entity.setDetailMessage(detailMessage);
        entity.setRequestId(requestId);
        entity.setTraceId(traceId);
        entity.setFailureStack(failureStack);
        entity.setCreatedBy(operatorId);
        pluginPersistenceMapper.insertRuntimeLog(entity);
    }

    public void registerTenantPermissions(Long tenantId, String pluginCode, String version) {
        List<PluginPermissionRelEntity> permissions = listPermissionRelations(pluginCode, version);
        if (permissions.isEmpty()) {
            return;
        }
        systemInternalApi.registerPluginPermissions(new PluginPermissionRegistrationRequestDTO(
                tenantId,
                pluginCode,
                permissions.stream()
                        .map(permission -> new PluginPermissionRegistrationRequestDTO.Permission(
                                permission.getPermissionKey(),
                                permission.getPermissionName(),
                                permission.getPermissionGroup()
                        ))
                        .toList()
        ));
    }

    private void upsertDefinition(PluginDTO.PluginPackageMetadata metadata, Long operatorId) {
        PluginDefinitionEntity entity = new PluginDefinitionEntity();
        entity.setPluginCode(metadata.getPluginCode());
        entity.setPluginName(metadata.getPluginName());
        entity.setPluginType(metadata.getKind());
        entity.setDescription(metadata.getDescription());
        entity.setAuthor(metadata.getAuthor());
        entity.setPluginApiVersion(metadata.getPluginApiVersion());
        entity.setSchemaMode(metadata.getSchemaMode());
        entity.setSupportsHotDisable(Boolean.TRUE.equals(metadata.getSupportsHotDisable()) ? 1 : 0);
        entity.setSupportsDataPurge(Boolean.TRUE.equals(metadata.getSupportsDataPurge()) ? 1 : 0);
        entity.setRuntimeContributionsJson(JsonUtils.toJson(metadata.getRuntimeContributions() == null ? List.of() : metadata.getRuntimeContributions()));
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        pluginPersistenceMapper.upsertDefinition(entity);
    }

    private static final class JsonUtils {
        private JsonUtils() {
        }

        private static String toJson(Object value) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } catch (Exception exception) {
                return "{}";
            }
        }
    }
}
