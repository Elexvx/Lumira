package com.lumira.saas.modules.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class PluginDTO {

    private PluginDTO() {
    }

    public static class InstallRequest {
        @NotBlank
        private String pluginCode;
        @NotBlank
        private String version;

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
    }

    public static class EnableRequest {
        @NotNull
        private Long tenantId;
        @NotBlank
        private String pluginCode;
        private String version;
        private String configJson;

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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getConfigJson() {
            return configJson;
        }

        public void setConfigJson(String configJson) {
            this.configJson = configJson;
        }
    }

    public static class DisableRequest {
        @NotNull
        private Long tenantId;
        @NotBlank
        private String pluginCode;
        private Boolean purgeData;

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

        public Boolean getPurgeData() {
            return purgeData;
        }

        public void setPurgeData(Boolean purgeData) {
            this.purgeData = purgeData;
        }
    }

    public static class UninstallRequest {
        private boolean removeData;

        public boolean isRemoveData() {
            return removeData;
        }

        public void setRemoveData(boolean removeData) {
            this.removeData = removeData;
        }
    }

    public static class RollbackRequest {
        @NotBlank
        private String pluginCode;
        @NotBlank
        private String targetVersion;

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getTargetVersion() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion = targetVersion;
        }
    }

    public static class PluginPackageMetadata {
        @NotBlank
        private String pluginCode;
        @NotBlank
        private String pluginName;
        @NotBlank
        private String version;
        @NotBlank
        private String pluginApiVersion;
        @NotBlank
        private String kind;
        private String description;
        private String author;
        @NotBlank
        private String minPlatformVersion;
        private String frontendEntry;
        private String backendEntry;
        private List<PluginDependencyDeclaration> dependencyPlugins;
        private List<PluginPermissionDeclaration> requiredPermissions;
        private List<PluginMenuDeclaration> menuDeclarations;
        private String configSchema;
        private String migrationStrategy;
        private String schemaMode;
        private Boolean supportsHotDisable;
        private Boolean supportsDataPurge;
        private List<String> runtimeContributions;
        @NotBlank
        private String checksumAlgorithm;

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

        public String getPluginApiVersion() {
            return pluginApiVersion;
        }

        public void setPluginApiVersion(String pluginApiVersion) {
            this.pluginApiVersion = pluginApiVersion;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
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

        public String getMinPlatformVersion() {
            return minPlatformVersion;
        }

        public void setMinPlatformVersion(String minPlatformVersion) {
            this.minPlatformVersion = minPlatformVersion;
        }

        public String getFrontendEntry() {
            return frontendEntry;
        }

        public void setFrontendEntry(String frontendEntry) {
            this.frontendEntry = frontendEntry;
        }

        public String getBackendEntry() {
            return backendEntry;
        }

        public void setBackendEntry(String backendEntry) {
            this.backendEntry = backendEntry;
        }

        public List<PluginDependencyDeclaration> getDependencyPlugins() {
            return dependencyPlugins;
        }

        public void setDependencyPlugins(List<PluginDependencyDeclaration> dependencyPlugins) {
            this.dependencyPlugins = dependencyPlugins;
        }

        public List<PluginPermissionDeclaration> getRequiredPermissions() {
            return requiredPermissions;
        }

        public void setRequiredPermissions(List<PluginPermissionDeclaration> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
        }

        public List<PluginMenuDeclaration> getMenuDeclarations() {
            return menuDeclarations;
        }

        public void setMenuDeclarations(List<PluginMenuDeclaration> menuDeclarations) {
            this.menuDeclarations = menuDeclarations;
        }

        public String getConfigSchema() {
            return configSchema;
        }

        public void setConfigSchema(String configSchema) {
            this.configSchema = configSchema;
        }

        public String getMigrationStrategy() {
            return migrationStrategy;
        }

        public void setMigrationStrategy(String migrationStrategy) {
            this.migrationStrategy = migrationStrategy;
        }

        public String getSchemaMode() {
            return schemaMode;
        }

        public void setSchemaMode(String schemaMode) {
            this.schemaMode = schemaMode;
        }

        public Boolean getSupportsHotDisable() {
            return supportsHotDisable;
        }

        public void setSupportsHotDisable(Boolean supportsHotDisable) {
            this.supportsHotDisable = supportsHotDisable;
        }

        public Boolean getSupportsDataPurge() {
            return supportsDataPurge;
        }

        public void setSupportsDataPurge(Boolean supportsDataPurge) {
            this.supportsDataPurge = supportsDataPurge;
        }

        public List<String> getRuntimeContributions() {
            return runtimeContributions;
        }

        public void setRuntimeContributions(List<String> runtimeContributions) {
            this.runtimeContributions = runtimeContributions;
        }

        public String getChecksumAlgorithm() {
            return checksumAlgorithm;
        }

        public void setChecksumAlgorithm(String checksumAlgorithm) {
            this.checksumAlgorithm = checksumAlgorithm;
        }
    }

    public static class PluginDependencyDeclaration {
        @NotBlank
        private String pluginCode;
        @NotBlank
        private String minVersion;

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }

        public String getMinVersion() {
            return minVersion;
        }

        public void setMinVersion(String minVersion) {
            this.minVersion = minVersion;
        }
    }

    public static class PluginPermissionDeclaration {
        @NotBlank
        private String permissionKey;
        @NotBlank
        private String permissionName;
        private String permissionGroup;

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
    }

    public static class PluginMenuDeclaration {
        @NotBlank
        private String menuCode;
        private String parentMenuCode;
        @NotBlank
        private String menuName;
        @NotBlank
        private String routePath;
        private String icon;
        private String permissionKey;
        private Integer sortNo;

        public String getMenuCode() {
            return menuCode;
        }

        public void setMenuCode(String menuCode) {
            this.menuCode = menuCode;
        }

        public String getParentMenuCode() {
            return parentMenuCode;
        }

        public void setParentMenuCode(String parentMenuCode) {
            this.parentMenuCode = parentMenuCode;
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

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }
    }

    public static class FrontendPluginManifest {
        @NotBlank
        private String pluginCode;
        @NotBlank
        private String version;
        @NotBlank
        private String entry;
        @NotEmpty
        private List<String> assets;
        private List<String> styles;
        private List<String> routes;
        private List<String> sharedDeps;

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

        public String getEntry() {
            return entry;
        }

        public void setEntry(String entry) {
            this.entry = entry;
        }

        public List<String> getAssets() {
            return assets;
        }

        public void setAssets(List<String> assets) {
            this.assets = assets;
        }

        public List<String> getStyles() {
            return styles;
        }

        public void setStyles(List<String> styles) {
            this.styles = styles;
        }

        public List<String> getRoutes() {
            return routes;
        }

        public void setRoutes(List<String> routes) {
            this.routes = routes;
        }

        public List<String> getSharedDeps() {
            return sharedDeps;
        }

        public void setSharedDeps(List<String> sharedDeps) {
            this.sharedDeps = sharedDeps;
        }
    }

    public static class PluginStatusRequest {
        @NotNull
        private Long tenantId;

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
