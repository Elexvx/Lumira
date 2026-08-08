package com.lumira.saas.modules.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CertificateDTO {
    private CertificateDTO() {
    }

    public static class TemplateUpsertRequest {
        @Size(max = 64)
        private String templateCode;
        @NotBlank
        @Size(max = 128)
        private String templateName;
        @Size(max = 32)
        private String sceneType;
        @Size(max = 1000)
        private String description;

        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getSceneType() { return sceneType; }
        public void setSceneType(String sceneType) { this.sceneType = sceneType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class CanvasSaveRequest {
        @NotNull
        private Integer pageWidth;
        @NotNull
        private Integer pageHeight;
        @Size(max = 16)
        private String orientation;
        @Size(max = 16)
        private String unit;
        private Integer dpi;
        @NotBlank
        private String canvasJson;
        private String variableSchemaJson;

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
    }

    public static class PreviewRequest {
        @NotNull
        private Long templateVersionId;
        private Map<String, Object> data;

        public Long getTemplateVersionId() { return templateVersionId; }
        public void setTemplateVersionId(Long templateVersionId) { this.templateVersionId = templateVersionId; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    public static class BatchGenerateRequest {
        @Size(max = 128)
        private String batchName;
        @NotNull
        private Long templateId;
        @NotNull
        private Long templateVersionId;
        private Long competitionId;
        private Long stageId;
        @Size(max = 32)
        private String sourceType;
        @NotEmpty
        @Valid
        private List<CertificateDataRequest> records;

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
        public List<CertificateDataRequest> getRecords() { return records; }
        public void setRecords(List<CertificateDataRequest> records) { this.records = records; }
    }

    public static class CertificateDataRequest {
        @NotBlank
        @Size(max = 128)
        private String recipientName;
        @Size(max = 32)
        private String recipientType;
        @Size(max = 128)
        private String competitionTitle;
        @Size(max = 128)
        private String projectName;
        @Size(max = 128)
        private String teamName;
        @Size(max = 128)
        private String awardName;
        private LocalDate issueDate;
        private LocalDate expireDate;
        private Map<String, Object> data;

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
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    public static class AwardGrantRequest {
        @NotNull
        private Long reviewBatchId;
        @Size(max = 128)
        private String awardName;
        @Min(1)
        @Max(10000)
        private Integer maxRank;
        @Valid
        @Size(max = 20)
        private List<@NotNull AwardRuleRequest> rules;

        public Long getReviewBatchId() { return reviewBatchId; }
        public void setReviewBatchId(Long reviewBatchId) { this.reviewBatchId = reviewBatchId; }
        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public Integer getMaxRank() { return maxRank; }
        public void setMaxRank(Integer maxRank) { this.maxRank = maxRank; }
        public List<AwardRuleRequest> getRules() { return rules; }
        public void setRules(List<AwardRuleRequest> rules) { this.rules = rules; }
    }

    public static class AwardRuleRequest {
        @NotBlank
        @Size(max = 128)
        private String awardName;
        @NotNull
        @Min(1)
        @Max(10000)
        private Integer minRank;
        @NotNull
        @Min(1)
        @Max(10000)
        private Integer maxRank;

        public String getAwardName() { return awardName; }
        public void setAwardName(String awardName) { this.awardName = awardName; }
        public Integer getMinRank() { return minRank; }
        public void setMinRank(Integer minRank) { this.minRank = minRank; }
        public Integer getMaxRank() { return maxRank; }
        public void setMaxRank(Integer maxRank) { this.maxRank = maxRank; }
    }

    public static class AwardCertificateGenerateRequest {
        @Size(max = 128)
        private String batchName;
        @NotNull
        private Long templateId;
        @NotNull
        private Long templateVersionId;
        @NotEmpty
        @Size(max = 200)
        private List<@NotNull Long> grantIds;

        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Long getTemplateVersionId() { return templateVersionId; }
        public void setTemplateVersionId(Long templateVersionId) { this.templateVersionId = templateVersionId; }
        public List<Long> getGrantIds() { return grantIds; }
        public void setGrantIds(List<Long> grantIds) { this.grantIds = grantIds; }
    }

    public static class RevokeRequest {
        @Size(max = 500)
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
