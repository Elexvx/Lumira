package com.lumira.saas.modules.competition.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CertificateVO {
    private CertificateVO() {
    }

    public static class Template {
        private Long id;
        private String templateCode;
        private String templateName;
        private String templateType;
        private String sceneType;
        private String description;
        private Integer latestVersion;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getTemplateType() { return templateType; }
        public void setTemplateType(String templateType) { this.templateType = templateType; }
        public String getSceneType() { return sceneType; }
        public void setSceneType(String sceneType) { this.sceneType = sceneType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getLatestVersion() { return latestVersion; }
        public void setLatestVersion(Integer latestVersion) { this.latestVersion = latestVersion; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class TemplateVersion {
        private Long id;
        private Long templateId;
        private Integer version;
        private Long backgroundFileId;
        private String backgroundUrl;
        private Integer pageWidth;
        private Integer pageHeight;
        private String orientation;
        private String unit;
        private Integer dpi;
        private String canvasJson;
        private String variableSchemaJson;
        private Long previewFileId;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Long getBackgroundFileId() { return backgroundFileId; }
        public void setBackgroundFileId(Long backgroundFileId) { this.backgroundFileId = backgroundFileId; }
        public String getBackgroundUrl() { return backgroundUrl; }
        public void setBackgroundUrl(String backgroundUrl) { this.backgroundUrl = backgroundUrl; }
        public Integer getPageWidth() { return pageWidth; }
        public void setPageWidth(Integer pageWidth) { this.pageWidth = pageWidth; }
        public Integer getPageHeight() { return pageHeight; }
        public void setPageHeight(Integer pageHeight) { this.pageHeight = pageHeight; }
        public String getOrientation() { return orientation; }
        public void setOrientation(String orientation) { this.orientation = orientation; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Integer getDpi() { return dpi; }
        public void setDpi(Integer dpi) { this.dpi = dpi; }
        public String getCanvasJson() { return canvasJson; }
        public void setCanvasJson(String canvasJson) { this.canvasJson = canvasJson; }
        public String getVariableSchemaJson() { return variableSchemaJson; }
        public void setVariableSchemaJson(String variableSchemaJson) { this.variableSchemaJson = variableSchemaJson; }
        public Long getPreviewFileId() { return previewFileId; }
        public void setPreviewFileId(Long previewFileId) { this.previewFileId = previewFileId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Batch {
        private Long id;
        private String batchNo;
        private String batchName;
        private Long templateId;
        private Long templateVersionId;
        private Long competitionId;
        private Long stageId;
        private String sourceType;
        private Long sourceRefId;
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private String status;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Long getTemplateVersionId() { return templateVersionId; }
        public void setTemplateVersionId(Long templateVersionId) { this.templateVersionId = templateVersionId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public Long getSourceRefId() { return sourceRefId; }
        public void setSourceRefId(Long sourceRefId) { this.sourceRefId = sourceRefId; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getSuccessCount() { return successCount; }
        public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
        public Integer getFailedCount() { return failedCount; }
        public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class AwardGrant {
        private Long id;
        private Long publicationId;
        private Integer publicationVersion;
        private Long reviewBatchId;
        private Long competitionId;
        private Long stageId;
        private Long candidateId;
        private Long registrationId;
        private Long projectId;
        private Long teamId;
        private Long userId;
        private String userUuid;
        private String recipientName;
        private String competitionTitle;
        private String projectName;
        private String teamName;
        private String awardName;
        private Integer rankNo;
        private String decision;
        private String status;
        private Long certificateRecordId;
        private LocalDateTime grantedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getPublicationId() { return publicationId; }
        public void setPublicationId(Long publicationId) { this.publicationId = publicationId; }
        public Integer getPublicationVersion() { return publicationVersion; }
        public void setPublicationVersion(Integer publicationVersion) { this.publicationVersion = publicationVersion; }
        public Long getReviewBatchId() { return reviewBatchId; }
        public void setReviewBatchId(Long reviewBatchId) { this.reviewBatchId = reviewBatchId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserUuid() { return userUuid; }
        public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCertificateRecordId() { return certificateRecordId; }
        public void setCertificateRecordId(Long certificateRecordId) { this.certificateRecordId = certificateRecordId; }
        public LocalDateTime getGrantedAt() { return grantedAt; }
        public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    }

    public static class AwardSource {
        private Long reviewBatchId;
        private String batchNo;
        private String batchName;
        private Long competitionId;
        private String competitionTitle;
        private Long stageId;
        private String stageName;
        private Integer candidateCount;
        private Integer publicationVersion;
        private LocalDateTime publishedAt;
        private Integer grantCount;
        private Integer issuedCount;

        public Long getReviewBatchId() { return reviewBatchId; }
        public void setReviewBatchId(Long reviewBatchId) { this.reviewBatchId = reviewBatchId; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public Integer getCandidateCount() { return candidateCount; }
        public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
        public Integer getPublicationVersion() { return publicationVersion; }
        public void setPublicationVersion(Integer publicationVersion) { this.publicationVersion = publicationVersion; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public Integer getGrantCount() { return grantCount; }
        public void setGrantCount(Integer grantCount) { this.grantCount = grantCount; }
        public Integer getIssuedCount() { return issuedCount; }
        public void setIssuedCount(Integer issuedCount) { this.issuedCount = issuedCount; }
    }

    public static class AwardRule {
        private String awardName;
        private Integer minRank;
        private Integer maxRank;

        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public Integer getMinRank() { return minRank; }
        public void setMinRank(Integer minRank) { this.minRank = minRank; }
        public Integer getMaxRank() { return maxRank; }
        public void setMaxRank(Integer maxRank) { this.maxRank = maxRank; }
    }

    public static class Record {
        private Long id;
        private String certificateNo;
        private String verificationCode;
        private String publicToken;
        private Long batchId;
        private Long templateId;
        private Long templateVersionId;
        private String templateName;
        private Long competitionId;
        private Long registrationId;
        private Long projectId;
        private Long teamId;
        private Long userId;
        private String recipientName;
        private String recipientType;
        private String competitionTitle;
        private String projectName;
        private String teamName;
        private String awardName;
        private LocalDate issueDate;
        private LocalDate expireDate;
        private String dataJson;
        private String certificateFileUrl;
        private String status;
        private String revokedReason;
        private LocalDateTime revokedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCertificateNo() { return certificateNo; }
        public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
        public String getVerificationCode() { return verificationCode; }
        public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
        public String getPublicToken() { return publicToken; }
        public void setPublicToken(String publicToken) { this.publicToken = publicToken; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Long getTemplateVersionId() { return templateVersionId; }
        public void setTemplateVersionId(Long templateVersionId) { this.templateVersionId = templateVersionId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getRecipientType() { return recipientType; }
        public void setRecipientType(String recipientType) { this.recipientType = recipientType; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public LocalDate getIssueDate() { return issueDate; }
        public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
        public LocalDate getExpireDate() { return expireDate; }
        public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
        public String getDataJson() { return dataJson; }
        public void setDataJson(String dataJson) { this.dataJson = dataJson; }
        public String getCertificateFileUrl() { return certificateFileUrl; }
        public void setCertificateFileUrl(String certificateFileUrl) { this.certificateFileUrl = certificateFileUrl; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRevokedReason() { return revokedReason; }
        public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }
        public LocalDateTime getRevokedAt() { return revokedAt; }
        public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class GenerateResult {
        private Batch batch;
        private List<Record> records;

        public Batch getBatch() { return batch; }
        public void setBatch(Batch batch) { this.batch = batch; }
        public List<Record> getRecords() { return records; }
        public void setRecords(List<Record> records) { this.records = records; }
    }

    public static class PublicVerifyResult {
        private String result;
        private String certificateNo;
        private String recipientName;
        private String competitionTitle;
        private String projectName;
        private String awardName;
        private LocalDate issueDate;
        private String organizer;
        private String status;
        private String certificateFileUrl;
        private Map<String, Object> safeData;

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getCertificateNo() { return certificateNo; }
        public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public LocalDate getIssueDate() { return issueDate; }
        public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
        public String getOrganizer() { return organizer; }
        public void setOrganizer(String organizer) { this.organizer = organizer; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCertificateFileUrl() { return certificateFileUrl; }
        public void setCertificateFileUrl(String certificateFileUrl) { this.certificateFileUrl = certificateFileUrl; }
        public Map<String, Object> getSafeData() { return safeData; }
        public void setSafeData(Map<String, Object> safeData) { this.safeData = safeData; }
    }
}
