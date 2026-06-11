package com.lumira.saas.modules.system.update.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformUpdateVO {

    private PlatformUpdateVO() {
    }

    public static class StatusVO {
        private CurrentVersionVO current;
        private LatestVersionVO latest;
        private Boolean updateAvailable;
        private String status;
        private Boolean currentKnown;
        private Boolean latestKnown;
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
        public Boolean getUpdateAvailable() { return updateAvailable; }
        public void setUpdateAvailable(Boolean updateAvailable) { this.updateAvailable = updateAvailable; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getCurrentKnown() { return currentKnown; }
        public void setCurrentKnown(Boolean currentKnown) { this.currentKnown = currentKnown; }
        public Boolean getLatestKnown() { return latestKnown; }
        public void setLatestKnown(Boolean latestKnown) { this.latestKnown = latestKnown; }
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
    }
}
