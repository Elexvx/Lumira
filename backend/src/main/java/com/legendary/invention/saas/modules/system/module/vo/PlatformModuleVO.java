package com.legendary.invention.saas.modules.system.module.vo;

import java.util.List;

public class PlatformModuleVO {

    private String moduleCode;
    private String moduleName;
    private String moduleType;
    private String lifecycleStatus;
    private String sourceType;
    private String description;
    private String ownerService;
    private String adminRoutePath;
    private List<String> apiPrefixes;
    private List<String> permissionKeys;
    private List<String> dependencies;
    private boolean dependencySatisfied;
    private List<String> missingDependencies;
    private List<String> inactiveDependencies;
    private boolean readyToEnable;
    private List<String> readinessIssues;
    private boolean overriddenByDatabase;
    private List<String> registrationSourceOrder;
    private String registeredAt;
    private boolean builtin;

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerService() {
        return ownerService;
    }

    public void setOwnerService(String ownerService) {
        this.ownerService = ownerService;
    }

    public String getAdminRoutePath() {
        return adminRoutePath;
    }

    public void setAdminRoutePath(String adminRoutePath) {
        this.adminRoutePath = adminRoutePath;
    }

    public List<String> getApiPrefixes() {
        return apiPrefixes;
    }

    public void setApiPrefixes(List<String> apiPrefixes) {
        this.apiPrefixes = apiPrefixes;
    }

    public List<String> getPermissionKeys() {
        return permissionKeys;
    }

    public void setPermissionKeys(List<String> permissionKeys) {
        this.permissionKeys = permissionKeys;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public boolean isDependencySatisfied() {
        return dependencySatisfied;
    }

    public void setDependencySatisfied(boolean dependencySatisfied) {
        this.dependencySatisfied = dependencySatisfied;
    }

    public List<String> getMissingDependencies() {
        return missingDependencies;
    }

    public void setMissingDependencies(List<String> missingDependencies) {
        this.missingDependencies = missingDependencies;
    }

    public List<String> getInactiveDependencies() {
        return inactiveDependencies;
    }

    public void setInactiveDependencies(List<String> inactiveDependencies) {
        this.inactiveDependencies = inactiveDependencies;
    }

    public boolean isReadyToEnable() {
        return readyToEnable;
    }

    public void setReadyToEnable(boolean readyToEnable) {
        this.readyToEnable = readyToEnable;
    }

    public List<String> getReadinessIssues() {
        return readinessIssues;
    }

    public void setReadinessIssues(List<String> readinessIssues) {
        this.readinessIssues = readinessIssues;
    }

    public boolean isOverriddenByDatabase() {
        return overriddenByDatabase;
    }

    public void setOverriddenByDatabase(boolean overriddenByDatabase) {
        this.overriddenByDatabase = overriddenByDatabase;
    }

    public List<String> getRegistrationSourceOrder() {
        return registrationSourceOrder;
    }

    public void setRegistrationSourceOrder(List<String> registrationSourceOrder) {
        this.registrationSourceOrder = registrationSourceOrder;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }
}
