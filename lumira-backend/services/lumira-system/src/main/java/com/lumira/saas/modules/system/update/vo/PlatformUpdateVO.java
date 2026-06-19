package com.lumira.saas.modules.system.update.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformUpdateVO {

    private PlatformUpdateVO() {
    }

    public static class StatusVO {
        private CurrentVersionVO current;
        private LatestVersionVO latest;
        private ManifestVO manifest;
        private TaskVO activeTask;
        private Boolean updateAvailable;
        private String status;
        private Boolean currentKnown;
        private Boolean latestKnown;
        private Boolean sourceReachable;
        private Boolean updaterAvailable;
        private String comparisonBasis;
        private String actionRequired;
        private String sourceType;
        private String sourceUrl;
        private LocalDateTime checkedAt;
        private String errorMessage;
        private List<String> notes;

        public CurrentVersionVO getCurrent() { return current; }
        public void setCurrent(CurrentVersionVO current) { this.current = current; }
        public LatestVersionVO getLatest() { return latest; }
        public void setLatest(LatestVersionVO latest) { this.latest = latest; }
        public ManifestVO getManifest() { return manifest; }
        public void setManifest(ManifestVO manifest) { this.manifest = manifest; }
        public TaskVO getActiveTask() { return activeTask; }
        public void setActiveTask(TaskVO activeTask) { this.activeTask = activeTask; }
        public Boolean getUpdateAvailable() { return updateAvailable; }
        public void setUpdateAvailable(Boolean updateAvailable) { this.updateAvailable = updateAvailable; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getCurrentKnown() { return currentKnown; }
        public void setCurrentKnown(Boolean currentKnown) { this.currentKnown = currentKnown; }
        public Boolean getLatestKnown() { return latestKnown; }
        public void setLatestKnown(Boolean latestKnown) { this.latestKnown = latestKnown; }
        public Boolean getSourceReachable() { return sourceReachable; }
        public void setSourceReachable(Boolean sourceReachable) { this.sourceReachable = sourceReachable; }
        public Boolean getUpdaterAvailable() { return updaterAvailable; }
        public void setUpdaterAvailable(Boolean updaterAvailable) { this.updaterAvailable = updaterAvailable; }
        public String getComparisonBasis() { return comparisonBasis; }
        public void setComparisonBasis(String comparisonBasis) { this.comparisonBasis = comparisonBasis; }
        public String getActionRequired() { return actionRequired; }
        public void setActionRequired(String actionRequired) { this.actionRequired = actionRequired; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> notes) { this.notes = notes; }
    }

    public static class CurrentVersionVO {
        private String version;
        private String commitId;
        private String branch;
        private String buildTime;

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getBuildTime() { return buildTime; }
        public void setBuildTime(String buildTime) { this.buildTime = buildTime; }
    }

    public static class LatestVersionVO {
        private String version;
        private String commitId;
        private String branch;
        private String releasedAt;
        private String title;
        private String url;
        private String serverImage;
        private String frontendImage;
        private Boolean migrationRequired;
        private Boolean rollbackSupported;

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getReleasedAt() { return releasedAt; }
        public void setReleasedAt(String releasedAt) { this.releasedAt = releasedAt; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getServerImage() { return serverImage; }
        public void setServerImage(String serverImage) { this.serverImage = serverImage; }
        public String getFrontendImage() { return frontendImage; }
        public void setFrontendImage(String frontendImage) { this.frontendImage = frontendImage; }
        public Boolean getMigrationRequired() { return migrationRequired; }
        public void setMigrationRequired(Boolean migrationRequired) { this.migrationRequired = migrationRequired; }
        public Boolean getRollbackSupported() { return rollbackSupported; }
        public void setRollbackSupported(Boolean rollbackSupported) { this.rollbackSupported = rollbackSupported; }
    }

    public static class ManifestVO {
        private String app;
        private String channel;
        private String minVersion;
        private String serverImage;
        private String frontendImage;
        private Boolean migrationRequired;
        private Boolean rollbackSupported;
        private String releaseNotes;

        public String getApp() { return app; }
        public void setApp(String app) { this.app = app; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getMinVersion() { return minVersion; }
        public void setMinVersion(String minVersion) { this.minVersion = minVersion; }
        public String getServerImage() { return serverImage; }
        public void setServerImage(String serverImage) { this.serverImage = serverImage; }
        public String getFrontendImage() { return frontendImage; }
        public void setFrontendImage(String frontendImage) { this.frontendImage = frontendImage; }
        public Boolean getMigrationRequired() { return migrationRequired; }
        public void setMigrationRequired(Boolean migrationRequired) { this.migrationRequired = migrationRequired; }
        public Boolean getRollbackSupported() { return rollbackSupported; }
        public void setRollbackSupported(Boolean rollbackSupported) { this.rollbackSupported = rollbackSupported; }
        public String getReleaseNotes() { return releaseNotes; }
        public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    }

    public static class TaskVO {
        private Long id;
        private String taskType;
        private String status;
        private String targetVersion;
        private String targetCommit;
        private String serverImage;
        private String frontendImage;
        private String updaterTaskId;
        private String backupPath;
        private String logSummary;
        private String errorMessage;
        private Long createdBy;
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
}
