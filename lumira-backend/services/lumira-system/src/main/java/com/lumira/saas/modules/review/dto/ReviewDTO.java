package com.lumira.saas.modules.review.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDTO {
    private ReviewDTO() {
    }

    public static class PlanCreateRequest {
        @NotNull
        private Long competitionId;
        @NotNull
        private Long stageId;
        @NotBlank
        @Size(max = 255)
        private String planName;
        @Size(max = 32)
        private String blindMode;
        @Min(1)
        @Max(20)
        private Integer requiredReviewerCount;
        @Min(1)
        @Max(20)
        private Integer minimumSubmittedCount;
        @Size(max = 32)
        private String aggregateMethod;
        @DecimalMin("1")
        @DecimalMax("1000")
        private BigDecimal scoreScale;
        @Min(0)
        @Max(10)
        private Integer trimHighestCount;
        @Min(0)
        @Max(10)
        private Integer trimLowestCount;
        @NotEmpty
        @Valid
        private List<CriterionRequest> criteria;

        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public String getPlanName() { return planName; }
        public void setPlanName(String planName) { this.planName = planName; }
        public String getBlindMode() { return blindMode; }
        public void setBlindMode(String blindMode) { this.blindMode = blindMode; }
        public Integer getRequiredReviewerCount() { return requiredReviewerCount; }
        public void setRequiredReviewerCount(Integer requiredReviewerCount) { this.requiredReviewerCount = requiredReviewerCount; }
        public Integer getMinimumSubmittedCount() { return minimumSubmittedCount; }
        public void setMinimumSubmittedCount(Integer minimumSubmittedCount) { this.minimumSubmittedCount = minimumSubmittedCount; }
        public String getAggregateMethod() { return aggregateMethod; }
        public void setAggregateMethod(String aggregateMethod) { this.aggregateMethod = aggregateMethod; }
        public BigDecimal getScoreScale() { return scoreScale; }
        public void setScoreScale(BigDecimal scoreScale) { this.scoreScale = scoreScale; }
        public Integer getTrimHighestCount() { return trimHighestCount; }
        public void setTrimHighestCount(Integer trimHighestCount) { this.trimHighestCount = trimHighestCount; }
        public Integer getTrimLowestCount() { return trimLowestCount; }
        public void setTrimLowestCount(Integer trimLowestCount) { this.trimLowestCount = trimLowestCount; }
        public List<CriterionRequest> getCriteria() { return criteria; }
        public void setCriteria(List<CriterionRequest> criteria) { this.criteria = criteria; }
    }

    public static class CriterionRequest {
        @NotBlank
        @Size(max = 64)
        private String code;
        @NotBlank
        @Size(max = 255)
        private String name;
        @Size(max = 2000)
        private String description;
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("1")
        private BigDecimal weight;
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        private BigDecimal maximumScore;
        private Boolean required;
        private Integer sortOrder;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }
        public BigDecimal getMaximumScore() { return maximumScore; }
        public void setMaximumScore(BigDecimal maximumScore) { this.maximumScore = maximumScore; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class BatchCreateRequest {
        @NotNull
        private Long planId;
        @NotBlank
        @Size(max = 255)
        private String batchName;
        @Size(max = 32)
        private String assignmentStrategy;
        private LocalDateTime reviewDeadline;

        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public String getAssignmentStrategy() { return assignmentStrategy; }
        public void setAssignmentStrategy(String assignmentStrategy) { this.assignmentStrategy = assignmentStrategy; }
        public LocalDateTime getReviewDeadline() { return reviewDeadline; }
        public void setReviewDeadline(LocalDateTime reviewDeadline) { this.reviewDeadline = reviewDeadline; }
    }

    public static class BatchFreezeRequest {
        @Size(max = 10000)
        private List<@NotNull Long> registrationIds;

        public List<Long> getRegistrationIds() { return registrationIds; }
        public void setRegistrationIds(List<Long> registrationIds) { this.registrationIds = registrationIds; }
    }

    public static class AssignmentCreateRequest {
        @NotEmpty
        @Size(max = 20000)
        @Valid
        private List<AssignmentItemRequest> assignments;
        private LocalDateTime dueAt;

        public List<AssignmentItemRequest> getAssignments() { return assignments; }
        public void setAssignments(List<AssignmentItemRequest> assignments) { this.assignments = assignments; }
        public LocalDateTime getDueAt() { return dueAt; }
        public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    }

    public static class AutoAssignmentRequest {
        @Size(max = 500)
        private List<@NotNull Long> expertIds;
        private LocalDateTime dueAt;
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        private BigDecimal reviewerWeight;

        public List<Long> getExpertIds() { return expertIds; }
        public void setExpertIds(List<Long> expertIds) { this.expertIds = expertIds; }
        public LocalDateTime getDueAt() { return dueAt; }
        public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
        public BigDecimal getReviewerWeight() { return reviewerWeight; }
        public void setReviewerWeight(BigDecimal reviewerWeight) { this.reviewerWeight = reviewerWeight; }
    }

    public static class AssignmentItemRequest {
        @NotNull
        private Long candidateId;
        @NotNull
        private Long expertId;
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        private BigDecimal reviewerWeight;

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getExpertId() { return expertId; }
        public void setExpertId(Long expertId) { this.expertId = expertId; }
        public BigDecimal getReviewerWeight() { return reviewerWeight; }
        public void setReviewerWeight(BigDecimal reviewerWeight) { this.reviewerWeight = reviewerWeight; }
    }

    public static class AssignmentDeclineRequest {
        @NotBlank
        @Size(max = 1000)
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AssignmentRevokeRequest {
        @NotBlank
        @Size(max = 1000)
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ReviewSheetRequest {
        @Size(max = 4000)
        private String reviewComment;
        @NotEmpty
        @Size(max = 100)
        @Valid
        private List<ScoreItemRequest> scores;

        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
        public List<ScoreItemRequest> getScores() { return scores; }
        public void setScores(List<ScoreItemRequest> scores) { this.scores = scores; }
    }

    public static class ScoreItemRequest {
        @NotNull
        private Long criterionId;
        @NotNull
        @DecimalMin("0")
        private BigDecimal score;
        @Size(max = 2000)
        private String comment;

        public Long getCriterionId() { return criterionId; }
        public void setCriterionId(Long criterionId) { this.criterionId = criterionId; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class AggregateDecisionRequest {
        @NotBlank
        @Size(max = 32)
        private String decision;
        @Size(max = 2000)
        private String reason;

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AppealSubmitRequest {
        @NotBlank
        @Size(max = 4000)
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AppealResolveRequest {
        @NotBlank
        @Size(max = 32)
        private String decision;
        @NotBlank
        @Size(max = 4000)
        private String resolution;

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
    }

    public static class PublicationCorrectionRequest {
        @NotBlank
        @Size(max = 1000)
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
