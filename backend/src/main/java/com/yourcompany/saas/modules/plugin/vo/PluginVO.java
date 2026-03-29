package com.yourcompany.saas.modules.plugin.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PluginVO {

    private PluginVO() {
    }

    public static class PluginDefinitionVO {
        private String pluginCode;
        private String pluginName;
        private String pluginType;
        private String description;
        private String author;
        private String pluginApiVersion;
        private String status;
        private Integer builtinFlag;
        private Integer sortNo;

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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getBuiltinFlag() {
            return builtinFlag;
        }

        public void setBuiltinFlag(Integer builtinFlag) {
            this.builtinFlag = builtinFlag;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }
    }

    public static class PluginVersionVO {
        private String pluginCode;
        private String version;
        private String installStatus;
        private String loadStatus;
        private String healthStatus;
        private Integer isActive;
        private Integer rollbackable;
        private String minPlatformVersion;
        private String frontendManifestPath;
        private String validationReportJson;
        private LocalDateTime installedAt;
        private LocalDateTime createdAt;

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

        public String getMinPlatformVersion() {
            return minPlatformVersion;
        }

        public void setMinPlatformVersion(String minPlatformVersion) {
            this.minPlatformVersion = minPlatformVersion;
        }

        public String getFrontendManifestPath() {
            return frontendManifestPath;
        }

        public void setFrontendManifestPath(String frontendManifestPath) {
            this.frontendManifestPath = frontendManifestPath;
        }

        public String getValidationReportJson() {
            return validationReportJson;
        }

        public void setValidationReportJson(String validationReportJson) {
            this.validationReportJson = validationReportJson;
        }

        public LocalDateTime getInstalledAt() {
            return installedAt;
        }

        public void setInstalledAt(LocalDateTime installedAt) {
            this.installedAt = installedAt;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class PluginUploadVO {
        private String pluginCode;
        private String pluginName;
        private String version;
        private String installStatus;
        private String validationReportJson;

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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getInstallStatus() {
            return installStatus;
        }

        public void setInstallStatus(String installStatus) {
            this.installStatus = installStatus;
        }

        public String getValidationReportJson() {
            return validationReportJson;
        }

        public void setValidationReportJson(String validationReportJson) {
            this.validationReportJson = validationReportJson;
        }
    }

    public static class PluginRuntimeLogVO {
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
        private LocalDateTime createdAt;

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

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class TenantPluginVO {
        private String pluginCode;
        private String pluginName;
        private String version;
        private String manifestPath;
        private List<String> sharedDeps;
        private List<String> routes;
        private List<Map<String, Object>> menus;

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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getManifestPath() {
            return manifestPath;
        }

        public void setManifestPath(String manifestPath) {
            this.manifestPath = manifestPath;
        }

        public List<String> getSharedDeps() {
            return sharedDeps;
        }

        public void setSharedDeps(List<String> sharedDeps) {
            this.sharedDeps = sharedDeps;
        }

        public List<String> getRoutes() {
            return routes;
        }

        public void setRoutes(List<String> routes) {
            this.routes = routes;
        }

        public List<Map<String, Object>> getMenus() {
            return menus;
        }

        public void setMenus(List<Map<String, Object>> menus) {
            this.menus = menus;
        }
    }
}
