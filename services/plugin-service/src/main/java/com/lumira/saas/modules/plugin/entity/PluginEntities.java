package com.lumira.saas.modules.plugin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

public final class PluginEntities {

    private PluginEntities() {
    }

    @TableName("sys_plugin_definition")
    public static class PluginDefinitionEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String pluginName;
        private String pluginType;
        private String description;
        private String author;
        private String pluginApiVersion;
        private Integer builtinFlag;
        private String status;
        private Integer sortNo;
        private String schemaMode;
        private Integer supportsHotDisable;
        private Integer supportsDataPurge;
        private String runtimeContributionsJson;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginName() {
            return pluginName;
        }

        public void setPluginName(String pluginName) {
            this.pluginName = pluginName;
        }

        public String getPluginType() {
            return pluginType;
        }

        public void setPluginType(String pluginType) {
            this.pluginType = pluginType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getPluginApiVersion() {
            return pluginApiVersion;
        }

        public void setPluginApiVersion(String pluginApiVersion) {
            this.pluginApiVersion = pluginApiVersion;
        }

        public Integer getBuiltinFlag() {
            return builtinFlag;
        }

        public void setBuiltinFlag(Integer builtinFlag) {
            this.builtinFlag = builtinFlag;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public String getSchemaMode() {
            return schemaMode;
        }

        public void setSchemaMode(String schemaMode) {
            this.schemaMode = schemaMode;
        }

        public Integer getSupportsHotDisable() {
            return supportsHotDisable;
        }

        public void setSupportsHotDisable(Integer supportsHotDisable) {
            this.supportsHotDisable = supportsHotDisable;
        }

        public Integer getSupportsDataPurge() {
            return supportsDataPurge;
        }

        public void setSupportsDataPurge(Integer supportsDataPurge) {
            this.supportsDataPurge = supportsDataPurge;
        }

        public String getRuntimeContributionsJson() {
            return runtimeContributionsJson;
        }

        public void setRuntimeContributionsJson(String runtimeContributionsJson) {
            this.runtimeContributionsJson = runtimeContributionsJson;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_version")
    public static class PluginVersionEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String version;
        private String packagePath;
        private String artifactPath;
        private String frontendManifestPath;
        private String backendJarPath;
        private String checksum;
        private String signaturePath;
        private String minPlatformVersion;
        private String installStatus;
        private String loadStatus;
        private String healthStatus;
        private String lifecycleStatus;
        private String schemaStatus;
        private Integer isActive;
        private Integer rollbackable;
        private String metadataJson;
        private String validationReportJson;
        private String stagedPath;
        private LocalDateTime installedAt;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getPackagePath() {
            return packagePath;
        }

        public void setPackagePath(String packagePath) {
            this.packagePath = packagePath;
        }

        public String getArtifactPath() {
            return artifactPath;
        }

        public void setArtifactPath(String artifactPath) {
            this.artifactPath = artifactPath;
        }

        public String getFrontendManifestPath() {
            return frontendManifestPath;
        }

        public void setFrontendManifestPath(String frontendManifestPath) {
            this.frontendManifestPath = frontendManifestPath;
        }

        public String getBackendJarPath() {
            return backendJarPath;
        }

        public void setBackendJarPath(String backendJarPath) {
            this.backendJarPath = backendJarPath;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }

        public String getSignaturePath() {
            return signaturePath;
        }

        public void setSignaturePath(String signaturePath) {
            this.signaturePath = signaturePath;
        }

        public String getMinPlatformVersion() {
            return minPlatformVersion;
        }

        public void setMinPlatformVersion(String minPlatformVersion) {
            this.minPlatformVersion = minPlatformVersion;
        }

        public String getInstallStatus() {
            return installStatus;
        }

        public void setInstallStatus(String installStatus) {
            this.installStatus = installStatus;
        }

        public String getLoadStatus() {
            return loadStatus;
        }

        public void setLoadStatus(String loadStatus) {
            this.loadStatus = loadStatus;
        }

        public String getHealthStatus() {
            return healthStatus;
        }

        public void setHealthStatus(String healthStatus) {
            this.healthStatus = healthStatus;
        }

        public String getLifecycleStatus() {
            return lifecycleStatus;
        }

        public void setLifecycleStatus(String lifecycleStatus) {
            this.lifecycleStatus = lifecycleStatus;
        }

        public String getSchemaStatus() {
            return schemaStatus;
        }

        public void setSchemaStatus(String schemaStatus) {
            this.schemaStatus = schemaStatus;
        }

        public Integer getIsActive() {
            return isActive;
        }

        public void setIsActive(Integer isActive) {
            this.isActive = isActive;
        }

        public Integer getRollbackable() {
            return rollbackable;
        }

        public void setRollbackable(Integer rollbackable) {
            this.rollbackable = rollbackable;
        }

        public String getMetadataJson() {
            return metadataJson;
        }

        public void setMetadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
        }

        public String getValidationReportJson() {
            return validationReportJson;
        }

        public void setValidationReportJson(String validationReportJson) {
            this.validationReportJson = validationReportJson;
        }

        public String getStagedPath() {
            return stagedPath;
        }

        public void setStagedPath(String stagedPath) {
            this.stagedPath = stagedPath;
        }

        public LocalDateTime getInstalledAt() {
            return installedAt;
        }

        public void setInstalledAt(LocalDateTime installedAt) {
            this.installedAt = installedAt;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_tenant")
    public static class PluginTenantEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long tenantId;
        private String pluginCode;
        private String pluginVersion;
        private Integer enabled;
        private String configJson;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public Integer getEnabled() {
            return enabled;
        }

        public void setEnabled(Integer enabled) {
            this.enabled = enabled;
        }

        public String getConfigJson() {
            return configJson;
        }

        public void setConfigJson(String configJson) {
            this.configJson = configJson;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_dependency")
    public static class PluginDependencyEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String dependsOnPluginCode;
        private String minVersion;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getDependsOnPluginCode() {
            return dependsOnPluginCode;
        }

        public void setDependsOnPluginCode(String dependsOnPluginCode) {
            this.dependsOnPluginCode = dependsOnPluginCode;
        }

        public String getMinVersion() {
            return minVersion;
        }

        public void setMinVersion(String minVersion) {
            this.minVersion = minVersion;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_runtime_log")
    public static class PluginRuntimeLogEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long tenantId;
        private String pluginCode;
        private String pluginVersion;
        private String operationType;
        private String lifecycleStatus;
        private String resultStatus;
        private String detailMessage;
        private String requestId;
        private String traceId;
        private String failureStack;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public String getLifecycleStatus() {
            return lifecycleStatus;
        }

        public void setLifecycleStatus(String lifecycleStatus) {
            this.lifecycleStatus = lifecycleStatus;
        }

        public String getResultStatus() {
            return resultStatus;
        }

        public void setResultStatus(String resultStatus) {
            this.resultStatus = resultStatus;
        }

        public String getDetailMessage() {
            return detailMessage;
        }

        public void setDetailMessage(String detailMessage) {
            this.detailMessage = detailMessage;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getFailureStack() {
            return failureStack;
        }

        public void setFailureStack(String failureStack) {
            this.failureStack = failureStack;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_menu_rel")
    public static class PluginMenuRelEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String pluginVersion;
        private String menuCode;
        private String menuName;
        private String routePath;
        private String icon;
        private String permissionKey;
        private String parentMenuCode;
        private Integer sortNo;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getMenuCode() {
            return menuCode;
        }

        public void setMenuCode(String menuCode) {
            this.menuCode = menuCode;
        }

        public String getMenuName() {
            return menuName;
        }

        public void setMenuName(String menuName) {
            this.menuName = menuName;
        }

        public String getRoutePath() {
            return routePath;
        }

        public void setRoutePath(String routePath) {
            this.routePath = routePath;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getParentMenuCode() {
            return parentMenuCode;
        }

        public void setParentMenuCode(String parentMenuCode) {
            this.parentMenuCode = parentMenuCode;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_permission_rel")
    public static class PluginPermissionRelEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String pluginVersion;
        private String permissionKey;
        private String permissionName;
        private String permissionGroup;
        private Long createdBy;
        private LocalDateTime createdAt;
        private Long updatedBy;
        private LocalDateTime updatedAt;
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getPermissionName() {
            return permissionName;
        }

        public void setPermissionName(String permissionName) {
            this.permissionName = permissionName;
        }

        public String getPermissionGroup() {
            return permissionGroup;
        }

        public void setPermissionGroup(String permissionGroup) {
            this.permissionGroup = permissionGroup;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    @TableName("sys_plugin_schema_history")
    public static class PluginSchemaHistoryEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String pluginCode;
        private String pluginVersion;
        private String stepName;
        private String direction;
        private String scriptPath;
        private String executionStatus;
        private String detailMessage;
        private Long createdBy;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getScriptPath() {
            return scriptPath;
        }

        public void setScriptPath(String scriptPath) {
            this.scriptPath = scriptPath;
        }

        public String getExecutionStatus() {
            return executionStatus;
        }

        public void setExecutionStatus(String executionStatus) {
            this.executionStatus = executionStatus;
        }

        public String getDetailMessage() {
            return detailMessage;
        }

        public void setDetailMessage(String detailMessage) {
            this.detailMessage = detailMessage;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
