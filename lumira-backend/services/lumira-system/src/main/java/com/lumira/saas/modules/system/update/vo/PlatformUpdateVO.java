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
        private UpdaterCapabilitiesVO updaterCapabilities;
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
        public UpdaterCapabilitiesVO getUpdaterCapabilities() { return updaterCapabilities; }
        public void setUpdaterCapabilities(UpdaterCapabilitiesVO updaterCapabilities) { this.updaterCapabilities = updaterCapabilities; }
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
        private String asyncImage;
        private String jobExecutorImage;
        private String migratorImage;
        private Integer schemaVersion;
        private String strategy;
        private Integer minUpdaterProtocol;
        private String migrationMode;
        private String databaseVersion;
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
        public String getAsyncImage() { return asyncImage; }
        public void setAsyncImage(String asyncImage) { this.asyncImage = asyncImage; }
        public String getJobExecutorImage() { return jobExecutorImage; }
        public void setJobExecutorImage(String jobExecutorImage) { this.jobExecutorImage = jobExecutorImage; }
        public String getMigratorImage() { return migratorImage; }
        public void setMigratorImage(String migratorImage) { this.migratorImage = migratorImage; }
        public Integer getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public Integer getMinUpdaterProtocol() { return minUpdaterProtocol; }
        public void setMinUpdaterProtocol(Integer minUpdaterProtocol) { this.minUpdaterProtocol = minUpdaterProtocol; }
        public String getMigrationMode() { return migrationMode; }
        public void setMigrationMode(String migrationMode) { this.migrationMode = migrationMode; }
        public String getDatabaseVersion() { return databaseVersion; }
        public void setDatabaseVersion(String databaseVersion) { this.databaseVersion = databaseVersion; }
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
        private String asyncImage;
        private String jobExecutorImage;
        private String migratorImage;
        private Integer schemaVersion;
        private String strategy;
        private Integer minUpdaterProtocol;
        private String migrationMode;
        private String databaseVersion;
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
        public String getAsyncImage() { return asyncImage; }
        public void setAsyncImage(String asyncImage) { this.asyncImage = asyncImage; }
        public String getJobExecutorImage() { return jobExecutorImage; }
        public void setJobExecutorImage(String jobExecutorImage) { this.jobExecutorImage = jobExecutorImage; }
        public String getMigratorImage() { return migratorImage; }
        public void setMigratorImage(String migratorImage) { this.migratorImage = migratorImage; }
        public Integer getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public Integer getMinUpdaterProtocol() { return minUpdaterProtocol; }
        public void setMinUpdaterProtocol(Integer minUpdaterProtocol) { this.minUpdaterProtocol = minUpdaterProtocol; }
        public String getMigrationMode() { return migrationMode; }
        public void setMigrationMode(String migrationMode) { this.migrationMode = migrationMode; }
        public String getDatabaseVersion() { return databaseVersion; }
        public void setDatabaseVersion(String databaseVersion) { this.databaseVersion = databaseVersion; }
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
        private String strategy;
        private String phase;
        private Integer progressPercent;
        private String activeSlot;
        private String targetSlot;
        private String preflightId;
        private String manifestHash;
        private Long rollbackOfTaskId;
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

    public static class UpdaterCapabilitiesVO {
        private Integer protocolVersion;
        private String strategy;
        private String activeSlot;
        private Boolean supportsPreflight;
        private Boolean supportsCancel;
        private Boolean supportsPlatformTaskLookup;
        private Boolean supportsExpandOnlyMigration;

        public Integer getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(Integer protocolVersion) { this.protocolVersion = protocolVersion; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getActiveSlot() { return activeSlot; }
        public void setActiveSlot(String activeSlot) { this.activeSlot = activeSlot; }
        public Boolean getSupportsPreflight() { return supportsPreflight; }
        public void setSupportsPreflight(Boolean supportsPreflight) { this.supportsPreflight = supportsPreflight; }
        public Boolean getSupportsCancel() { return supportsCancel; }
        public void setSupportsCancel(Boolean supportsCancel) { this.supportsCancel = supportsCancel; }
        public Boolean getSupportsPlatformTaskLookup() { return supportsPlatformTaskLookup; }
        public void setSupportsPlatformTaskLookup(Boolean supportsPlatformTaskLookup) { this.supportsPlatformTaskLookup = supportsPlatformTaskLookup; }
        public Boolean getSupportsExpandOnlyMigration() { return supportsExpandOnlyMigration; }
        public void setSupportsExpandOnlyMigration(Boolean supportsExpandOnlyMigration) { this.supportsExpandOnlyMigration = supportsExpandOnlyMigration; }
    }

    public static class PreflightVO {
        private String preflightId;
        private Boolean ready;
        private String strategy;
        private String activeSlot;
        private String targetSlot;
        private String targetCommit;
        private String targetVersion;
        private String migrationMode;
        private String databaseTargetVersion;
        private List<String> blockers;
        private List<String> warnings;
        private String checkedAt;
        private String expiresAt;

        public String getPreflightId() { return preflightId; }
        public void setPreflightId(String preflightId) { this.preflightId = preflightId; }
        public Boolean getReady() { return ready; }
        public void setReady(Boolean ready) { this.ready = ready; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getActiveSlot() { return activeSlot; }
        public void setActiveSlot(String activeSlot) { this.activeSlot = activeSlot; }
        public String getTargetSlot() { return targetSlot; }
        public void setTargetSlot(String targetSlot) { this.targetSlot = targetSlot; }
        public String getTargetCommit() { return targetCommit; }
        public void setTargetCommit(String targetCommit) { this.targetCommit = targetCommit; }
        public String getTargetVersion() { return targetVersion; }
        public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }
        public String getMigrationMode() { return migrationMode; }
        public void setMigrationMode(String migrationMode) { this.migrationMode = migrationMode; }
        public String getDatabaseTargetVersion() { return databaseTargetVersion; }
        public void setDatabaseTargetVersion(String databaseTargetVersion) { this.databaseTargetVersion = databaseTargetVersion; }
        public List<String> getBlockers() { return blockers; }
        public void setBlockers(List<String> blockers) { this.blockers = blockers; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public String getCheckedAt() { return checkedAt; }
        public void setCheckedAt(String checkedAt) { this.checkedAt = checkedAt; }
        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class InstallRequest {
        private String preflightId;
        private String targetCommit;

        public String getPreflightId() { return preflightId; }
        public void setPreflightId(String preflightId) { this.preflightId = preflightId; }
        public String getTargetCommit() { return targetCommit; }
        public void setTargetCommit(String targetCommit) { this.targetCommit = targetCommit; }
    }
}
