package com.lumira.saas.modules.system.update.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("platform_update_task")
public class PlatformUpdateTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private String status;
    private String strategy;
    private String phase;
    private Integer progressPercent;
    private String activeSlot;
    private String targetSlot;
    private String preflightId;
    private String manifestHash;
    private Long rollbackOfTaskId;
    private String activeKey;
    private String targetVersion;
    private String targetCommit;
    private String serverImage;
    private String frontendImage;
    private String updaterTaskId;
    private String backupPath;
    private String logSummary;
    private String errorMessage;
    private Long createdBy;
    private String createdByUuid;
    private String createdByName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getActiveSlot() { return activeSlot; }
    public void setActiveSlot(String activeSlot) { this.activeSlot = activeSlot; }
    public String getTargetSlot() { return targetSlot; }
    public void setTargetSlot(String targetSlot) { this.targetSlot = targetSlot; }
    public String getPreflightId() { return preflightId; }
    public void setPreflightId(String preflightId) { this.preflightId = preflightId; }
    public String getManifestHash() { return manifestHash; }
    public void setManifestHash(String manifestHash) { this.manifestHash = manifestHash; }
    public Long getRollbackOfTaskId() { return rollbackOfTaskId; }
    public void setRollbackOfTaskId(Long rollbackOfTaskId) { this.rollbackOfTaskId = rollbackOfTaskId; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }
    public String getTargetVersion() { return targetVersion; }
    public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }
    public String getTargetCommit() { return targetCommit; }
    public void setTargetCommit(String targetCommit) { this.targetCommit = targetCommit; }
    public String getServerImage() { return serverImage; }
    public void setServerImage(String serverImage) { this.serverImage = serverImage; }
    public String getFrontendImage() { return frontendImage; }
    public void setFrontendImage(String frontendImage) { this.frontendImage = frontendImage; }
    public String getUpdaterTaskId() { return updaterTaskId; }
    public void setUpdaterTaskId(String updaterTaskId) { this.updaterTaskId = updaterTaskId; }
    public String getBackupPath() { return backupPath; }
    public void setBackupPath(String backupPath) { this.backupPath = backupPath; }
    public String getLogSummary() { return logSummary; }
    public void setLogSummary(String logSummary) { this.logSummary = logSummary; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByUuid() { return createdByUuid; }
    public void setCreatedByUuid(String createdByUuid) { this.createdByUuid = createdByUuid; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
