package com.legendary.invention.saas.modules.system.module.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("platform_module_definition")
public class PlatformModuleDefinitionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleCode;
    private String moduleName;
    private String moduleType;
    private String lifecycleStatus;
    private String sourceType;
    private String description;
    private String ownerService;
    private String adminRoutePath;
    private String apiPrefixes;
    private String permissionKeys;
    private Boolean builtin;
    private Integer sortNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getAdminRoutePath() { return adminRoutePath; }
    public void setAdminRoutePath(String adminRoutePath) { this.adminRoutePath = adminRoutePath; }
    public String getApiPrefixes() { return apiPrefixes; }
    public void setApiPrefixes(String apiPrefixes) { this.apiPrefixes = apiPrefixes; }
    public String getPermissionKeys() { return permissionKeys; }
    public void setPermissionKeys(String permissionKeys) { this.permissionKeys = permissionKeys; }
    public Boolean getBuiltin() { return builtin; }
    public void setBuiltin(Boolean builtin) { this.builtin = builtin; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
