package com.legendary.invention.saas.modules.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class EvaluationDTO {
    private EvaluationDTO() {
    }

    public static class TemplateRequest {
        @NotBlank private String templateName;
        @NotBlank private String objectType;
        private String description;
        @Valid @NotEmpty private List<DimensionRequest> dimensions;
        @Valid @NotEmpty private List<GradeRuleRequest> gradeRules;
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<DimensionRequest> getDimensions() { return dimensions; }
        public void setDimensions(List<DimensionRequest> dimensions) { this.dimensions = dimensions; }
        public List<GradeRuleRequest> getGradeRules() { return gradeRules; }
        public void setGradeRules(List<GradeRuleRequest> gradeRules) { this.gradeRules = gradeRules; }
    }

    public static class DimensionRequest {
        @NotBlank private String dimensionName;
        @NotNull private BigDecimal weight;
        @NotNull private BigDecimal maxScore;
        private Integer sortOrder;
        public String getDimensionName() { return dimensionName; }
        public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class GradeRuleRequest {
        @NotBlank private String gradeCode;
        @NotBlank private String gradeName;
        @NotNull private BigDecimal minScore;
        @NotNull private BigDecimal maxScore;
        public String getGradeCode() { return gradeCode; }
        public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
        public String getGradeName() { return gradeName; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }
        public BigDecimal getMinScore() { return minScore; }
        public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    }

    public static class EnabledRequest {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class InstanceCreateRequest {
        @NotNull private Long templateId;
        private Long objectId;
        @NotBlank private String objectTitle;
        @NotEmpty private List<Long> scorerUserIds;
        private Long reviewerUserId;
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Long getObjectId() { return objectId; }
        public void setObjectId(Long objectId) { this.objectId = objectId; }
        public String getObjectTitle() { return objectTitle; }
        public void setObjectTitle(String objectTitle) { this.objectTitle = objectTitle; }
        public List<Long> getScorerUserIds() { return scorerUserIds; }
        public void setScorerUserIds(List<Long> scorerUserIds) { this.scorerUserIds = scorerUserIds; }
        public Long getReviewerUserId() { return reviewerUserId; }
        public void setReviewerUserId(Long reviewerUserId) { this.reviewerUserId = reviewerUserId; }
    }

    public static class ScoreSubmitRequest {
        @Valid @NotEmpty private List<ScoreDetailRequest> details;
        private String comment;
        public List<ScoreDetailRequest> getDetails() { return details; }
        public void setDetails(List<ScoreDetailRequest> details) { this.details = details; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class ScoreDetailRequest {
        @NotNull private Long dimensionId;
        @NotNull private BigDecimal score;
        private String comment;
        public Long getDimensionId() { return dimensionId; }
        public void setDimensionId(Long dimensionId) { this.dimensionId = dimensionId; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class ReviewRequest {
        @NotNull private BigDecimal finalScore;
        @NotBlank private String finalGrade;
        private String comment;
        public BigDecimal getFinalScore() { return finalScore; }
        public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
        public String getFinalGrade() { return finalGrade; }
        public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class ArchiveRequest {
        private String comment;
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
