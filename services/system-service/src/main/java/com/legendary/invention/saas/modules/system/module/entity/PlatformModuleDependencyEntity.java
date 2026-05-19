package com.legendary.invention.saas.modules.system.module.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("platform_module_dependency")
public class PlatformModuleDependencyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleCode;
    private String dependencyModuleCode;
    private String minVersion;
    private Integer optionalFlag;
    private Integer sortNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getDependencyModuleCode() { return dependencyModuleCode; }
    public void setDependencyModuleCode(String dependencyModuleCode) { this.dependencyModuleCode = dependencyModuleCode; }
    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }
    public Integer getOptionalFlag() { return optionalFlag; }
    public void setOptionalFlag(Integer optionalFlag) { this.optionalFlag = optionalFlag; }
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
}
