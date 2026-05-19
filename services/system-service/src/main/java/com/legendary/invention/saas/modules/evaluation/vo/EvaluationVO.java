package com.legendary.invention.saas.modules.evaluation.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EvaluationVO {
    private EvaluationVO() {
    }

    public static class TemplateVO {
        private Long id;
        private String templateName;
        private String objectType;
        private String description;
        private Boolean enabled;
        private LocalDateTime createTime;
        private List<DimensionVO> dimensions;
        private List<GradeRuleVO> gradeRules;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public List<DimensionVO> getDimensions() { return dimensions; }
        public void setDimensions(List<DimensionVO> dimensions) { this.dimensions = dimensions; }
        public List<GradeRuleVO> getGradeRules() { return gradeRules; }
        public void setGradeRules(List<GradeRuleVO> gradeRules) { this.gradeRules = gradeRules; }
    }

    public static class DimensionVO {
        private Long id;
        private String dimensionName;
        private BigDecimal weight;
        private BigDecimal maxScore;
        private Integer sortOrder;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDimensionName() { return dimensionName; }
        public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class GradeRuleVO {
        private Long id;
        private String gradeCode;
        private String gradeName;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getGradeCode() { return gradeCode; }
        public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
        public String getGradeName() { return gradeName; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }
        public BigDecimal getMinScore() { return minScore; }
        public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    }

    public static class InstanceVO {
        private Long id;
        private Long templateId;
        private String objectType;
        private Long objectId;
        private String objectTitle;
        private String status;
        private Long creatorId;
        private Long reviewerUserId;
        private BigDecimal finalScore;
        private String finalGrade;
        private String archiveComment;
        private LocalDateTime createTime;
        private List<ScoreTaskVO> scoreTasks;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public Long getObjectId() { return objectId; }
        public void setObjectId(Long objectId) { this.objectId = objectId; }
        public String getObjectTitle() { return objectTitle; }
        public void setObjectTitle(String objectTitle) { this.objectTitle = objectTitle; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCreatorId() { return creatorId; }
        public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
        public Long getReviewerUserId() { return reviewerUserId; }
        public void setReviewerUserId(Long reviewerUserId) { this.reviewerUserId = reviewerUserId; }
        public BigDecimal getFinalScore() { return finalScore; }
        public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
        public String getFinalGrade() { return finalGrade; }
        public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }
        public String getArchiveComment() { return archiveComment; }
        public void setArchiveComment(String archiveComment) { this.archiveComment = archiveComment; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public List<ScoreTaskVO> getScoreTasks() { return scoreTasks; }
        public void setScoreTasks(List<ScoreTaskVO> scoreTasks) { this.scoreTasks = scoreTasks; }
    }

    public static class ScoreTaskVO {
        private Long id;
        private Long instanceId;
        private Long assigneeUserId;
        private String status;
        private BigDecimal totalScore;
        private String comment;
        private LocalDateTime submittedAt;
        private LocalDateTime createTime;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public Long getAssigneeUserId() { return assigneeUserId; }
        public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalScore() { return totalScore; }
        public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }
}
