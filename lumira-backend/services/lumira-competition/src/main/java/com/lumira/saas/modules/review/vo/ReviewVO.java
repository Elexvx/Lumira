package com.lumira.saas.modules.review.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewVO {
    private ReviewVO() {
    }

    public static class Plan {
        private Long id;
        private Long competitionId;
        private Long stageId;
        private String planName;
        private String status;
        private String blindMode;
        private Integer requiredReviewerCount;
        private Integer minimumSubmittedCount;
        private String aggregateMethod;
        private BigDecimal scoreScale;
        private Integer trimHighestCount;
        private Integer trimLowestCount;
        private Long criteriaVersionId;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<Criterion> criteria;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public String getPlanName() { return planName; }
        public void setPlanName(String planName) { this.planName = planName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
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
        public Long getCriteriaVersionId() { return criteriaVersionId; }
        public void setCriteriaVersionId(Long criteriaVersionId) { this.criteriaVersionId = criteriaVersionId; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public List<Criterion> getCriteria() { return criteria; }
        public void setCriteria(List<Criterion> criteria) { this.criteria = criteria; }
    }

    public static class Criterion {
        private Long id;
        private Long criteriaVersionId;
        private String criterionCode;
        private String criterionName;
        private String description;
        private BigDecimal weight;
        private BigDecimal maximumScore;
        private Boolean required;
        private Integer sortOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCriteriaVersionId() { return criteriaVersionId; }
        public void setCriteriaVersionId(Long criteriaVersionId) { this.criteriaVersionId = criteriaVersionId; }
        public String getCriterionCode() { return criterionCode; }
        public void setCriterionCode(String criterionCode) { this.criterionCode = criterionCode; }
        public String getCriterionName() { return criterionName; }
        public void setCriterionName(String criterionName) { this.criterionName = criterionName; }
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

    public static class Batch {
        private Long id;
        private Long planId;
        private Long competitionId;
        private Long stageId;
        private Long criteriaVersionId;
        private String batchNo;
        private String batchName;
        private String batchType;
        private String status;
        private String assignmentStrategy;
        private Integer minimumReviewerCount;
        private Integer reviewerCountPerCandidate;
        private Integer expertMinAssignments;
        private Integer expertTargetAssignments;
        private Integer expertMaxAssignments;
        private Integer candidateCount;
        private String freezeToken;
        private LocalDateTime frozenAt;
        private LocalDateTime assignmentConfirmedAt;
        private LocalDateTime reviewDeadline;
        private LocalDateTime finalizedAt;
        private LocalDateTime publishedAt;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public Long getCriteriaVersionId() { return criteriaVersionId; }
        public void setCriteriaVersionId(Long criteriaVersionId) { this.criteriaVersionId = criteriaVersionId; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public String getBatchType() { return batchType; }
        public void setBatchType(String batchType) { this.batchType = batchType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAssignmentStrategy() { return assignmentStrategy; }
        public void setAssignmentStrategy(String assignmentStrategy) { this.assignmentStrategy = assignmentStrategy; }
        public Integer getMinimumReviewerCount() { return minimumReviewerCount; }
        public void setMinimumReviewerCount(Integer minimumReviewerCount) { this.minimumReviewerCount = minimumReviewerCount; }
        public Integer getReviewerCountPerCandidate() { return reviewerCountPerCandidate; }
        public void setReviewerCountPerCandidate(Integer reviewerCountPerCandidate) { this.reviewerCountPerCandidate = reviewerCountPerCandidate; }
        public Integer getExpertMinAssignments() { return expertMinAssignments; }
        public void setExpertMinAssignments(Integer expertMinAssignments) { this.expertMinAssignments = expertMinAssignments; }
        public Integer getExpertTargetAssignments() { return expertTargetAssignments; }
        public void setExpertTargetAssignments(Integer expertTargetAssignments) { this.expertTargetAssignments = expertTargetAssignments; }
        public Integer getExpertMaxAssignments() { return expertMaxAssignments; }
        public void setExpertMaxAssignments(Integer expertMaxAssignments) { this.expertMaxAssignments = expertMaxAssignments; }
        public Integer getCandidateCount() { return candidateCount; }
        public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
        public String getFreezeToken() { return freezeToken; }
        public void setFreezeToken(String freezeToken) { this.freezeToken = freezeToken; }
        public LocalDateTime getFrozenAt() { return frozenAt; }
        public void setFrozenAt(LocalDateTime frozenAt) { this.frozenAt = frozenAt; }
        public LocalDateTime getAssignmentConfirmedAt() { return assignmentConfirmedAt; }
        public void setAssignmentConfirmedAt(LocalDateTime assignmentConfirmedAt) { this.assignmentConfirmedAt = assignmentConfirmedAt; }
        public LocalDateTime getReviewDeadline() { return reviewDeadline; }
        public void setReviewDeadline(LocalDateTime reviewDeadline) { this.reviewDeadline = reviewDeadline; }
        public LocalDateTime getFinalizedAt() { return finalizedAt; }
        public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class AssignmentResult {
        private Long batchId;
        private String batchStatus;
        private Integer createdCount;
        private Integer candidateCount;
        private Integer candidatesBelowMinimum;

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBatchStatus() { return batchStatus; }
        public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
        public Integer getCreatedCount() { return createdCount; }
        public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
        public Integer getCandidateCount() { return candidateCount; }
        public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
        public Integer getCandidatesBelowMinimum() { return candidatesBelowMinimum; }
        public void setCandidatesBelowMinimum(Integer candidatesBelowMinimum) { this.candidatesBelowMinimum = candidatesBelowMinimum; }
    }

    public static class Candidate {
        private Long id;
        private Long batchId;
        private Long registrationId;
        private String blindCode;
        private String status;
        private String snapshotJson;
        private String reviewSnapshotJson;
        private String snapshotHash;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public String getBlindCode() { return blindCode; }
        public void setBlindCode(String blindCode) { this.blindCode = blindCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
        public String getReviewSnapshotJson() { return reviewSnapshotJson; }
        public void setReviewSnapshotJson(String reviewSnapshotJson) { this.reviewSnapshotJson = reviewSnapshotJson; }
        public String getSnapshotHash() { return snapshotHash; }
        public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class AdminAssignment {
        private Long id;
        private Long batchId;
        private Long candidateId;
        private Long expertId;
        private Long expertUserId;
        private String expertUserUuid;
        private BigDecimal reviewerWeight;
        private String status;
        private LocalDateTime dueAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime declinedAt;
        private String declineReason;
        private LocalDateTime expiredAt;
        private LocalDateTime revokedAt;
        private String revokeReason;
        private LocalDateTime submittedAt;
        private String invitationStatus;
        private LocalDateTime checkedInAt;
        private Integer version;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getExpertId() { return expertId; }
        public void setExpertId(Long expertId) { this.expertId = expertId; }
        public Long getExpertUserId() { return expertUserId; }
        public void setExpertUserId(Long expertUserId) { this.expertUserId = expertUserId; }
        public String getExpertUserUuid() { return expertUserUuid; }
        public void setExpertUserUuid(String expertUserUuid) { this.expertUserUuid = expertUserUuid; }
        public BigDecimal getReviewerWeight() { return reviewerWeight; }
        public void setReviewerWeight(BigDecimal reviewerWeight) { this.reviewerWeight = reviewerWeight; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getDueAt() { return dueAt; }
        public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
        public LocalDateTime getAcceptedAt() { return acceptedAt; }
        public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
        public LocalDateTime getDeclinedAt() { return declinedAt; }
        public void setDeclinedAt(LocalDateTime declinedAt) { this.declinedAt = declinedAt; }
        public String getDeclineReason() { return declineReason; }
        public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
        public LocalDateTime getExpiredAt() { return expiredAt; }
        public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
        public LocalDateTime getRevokedAt() { return revokedAt; }
        public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
        public String getRevokeReason() { return revokeReason; }
        public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public String getInvitationStatus() { return invitationStatus; }
        public void setInvitationStatus(String invitationStatus) { this.invitationStatus = invitationStatus; }
        public LocalDateTime getCheckedInAt() { return checkedInAt; }
        public void setCheckedInAt(LocalDateTime checkedInAt) { this.checkedInAt = checkedInAt; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    public static class RosterExpert {
        private Long id;
        private Long batchId;
        private Long expertId;
        private Long expertUserId;
        private String expertUserUuid;
        private String expertName;
        private String email;
        private String status;
        private String invitationStatus;
        private Integer invitationAttempts;
        private String invitationFailureReason;
        private LocalDateTime invitationSentAt;
        private LocalDateTime checkedInAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getExpertId() { return expertId; }
        public void setExpertId(Long expertId) { this.expertId = expertId; }
        public Long getExpertUserId() { return expertUserId; }
        public void setExpertUserId(Long expertUserId) { this.expertUserId = expertUserId; }
        public String getExpertUserUuid() { return expertUserUuid; }
        public void setExpertUserUuid(String expertUserUuid) { this.expertUserUuid = expertUserUuid; }
        public String getExpertName() { return expertName; }
        public void setExpertName(String expertName) { this.expertName = expertName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getInvitationStatus() { return invitationStatus; }
        public void setInvitationStatus(String invitationStatus) { this.invitationStatus = invitationStatus; }
        public Integer getInvitationAttempts() { return invitationAttempts; }
        public void setInvitationAttempts(Integer invitationAttempts) { this.invitationAttempts = invitationAttempts; }
        public String getInvitationFailureReason() { return invitationFailureReason; }
        public void setInvitationFailureReason(String invitationFailureReason) { this.invitationFailureReason = invitationFailureReason; }
        public LocalDateTime getInvitationSentAt() { return invitationSentAt; }
        public void setInvitationSentAt(LocalDateTime invitationSentAt) { this.invitationSentAt = invitationSentAt; }
        public LocalDateTime getCheckedInAt() { return checkedInAt; }
        public void setCheckedInAt(LocalDateTime checkedInAt) { this.checkedInAt = checkedInAt; }
    }

    public static class Invitation {
        private Long invitationId;
        private Long batchId;
        private String batchName;
        private Long expertId;
        private String expertName;
        private String status;
        private String deliveryStatus;
        private String checkinStatus;
        private String qrValue;
        private LocalDateTime qrExpiresAt;
        private LocalDateTime tokenExpiresAt;
        private LocalDateTime checkedInAt;
        private LocalDateTime sentAt;
        private String failureReason;

        public Long getInvitationId() { return invitationId; }
        public void setInvitationId(Long invitationId) { this.invitationId = invitationId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public Long getExpertId() { return expertId; }
        public void setExpertId(Long expertId) { this.expertId = expertId; }
        public String getExpertName() { return expertName; }
        public void setExpertName(String expertName) { this.expertName = expertName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDeliveryStatus() { return deliveryStatus; }
        public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
        public String getCheckinStatus() { return checkinStatus; }
        public void setCheckinStatus(String checkinStatus) { this.checkinStatus = checkinStatus; }
        public String getQrValue() { return qrValue; }
        public void setQrValue(String qrValue) { this.qrValue = qrValue; }
        public LocalDateTime getQrExpiresAt() { return qrExpiresAt; }
        public void setQrExpiresAt(LocalDateTime qrExpiresAt) { this.qrExpiresAt = qrExpiresAt; }
        public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
        public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
        public LocalDateTime getCheckedInAt() { return checkedInAt; }
        public void setCheckedInAt(LocalDateTime checkedInAt) { this.checkedInAt = checkedInAt; }
        public LocalDateTime getSentAt() { return sentAt; }
        public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }

    public static class AssignmentTask {
        private Long assignmentId;
        private Long batchId;
        private String batchName;
        private Long candidateId;
        private String blindCode;
        private String candidateSnapshotJson;
        private String assignmentStatus;
        private Long criteriaVersionId;
        private BigDecimal scoreScale;
        private List<Criterion> criteria;
        private LocalDateTime dueAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime submittedAt;
        private Integer assignmentVersion;
        private Long latestSheetId;
        private Integer latestSheetVersion;
        private String latestSheetStatus;
        private BigDecimal latestTotalScore;
        private String latestReviewComment;
        private List<ScoreItem> latestScores;

        public Long getAssignmentId() { return assignmentId; }
        public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public String getBlindCode() { return blindCode; }
        public void setBlindCode(String blindCode) { this.blindCode = blindCode; }
        public String getCandidateSnapshotJson() { return candidateSnapshotJson; }
        public void setCandidateSnapshotJson(String candidateSnapshotJson) { this.candidateSnapshotJson = candidateSnapshotJson; }
        public String getAssignmentStatus() { return assignmentStatus; }
        public void setAssignmentStatus(String assignmentStatus) { this.assignmentStatus = assignmentStatus; }
        public Long getCriteriaVersionId() { return criteriaVersionId; }
        public void setCriteriaVersionId(Long criteriaVersionId) { this.criteriaVersionId = criteriaVersionId; }
        public BigDecimal getScoreScale() { return scoreScale; }
        public void setScoreScale(BigDecimal scoreScale) { this.scoreScale = scoreScale; }
        public List<Criterion> getCriteria() { return criteria; }
        public void setCriteria(List<Criterion> criteria) { this.criteria = criteria; }
        public LocalDateTime getDueAt() { return dueAt; }
        public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
        public LocalDateTime getAcceptedAt() { return acceptedAt; }
        public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public Integer getAssignmentVersion() { return assignmentVersion; }
        public void setAssignmentVersion(Integer assignmentVersion) { this.assignmentVersion = assignmentVersion; }
        public Long getLatestSheetId() { return latestSheetId; }
        public void setLatestSheetId(Long latestSheetId) { this.latestSheetId = latestSheetId; }
        public Integer getLatestSheetVersion() { return latestSheetVersion; }
        public void setLatestSheetVersion(Integer latestSheetVersion) { this.latestSheetVersion = latestSheetVersion; }
        public String getLatestSheetStatus() { return latestSheetStatus; }
        public void setLatestSheetStatus(String latestSheetStatus) { this.latestSheetStatus = latestSheetStatus; }
        public BigDecimal getLatestTotalScore() { return latestTotalScore; }
        public void setLatestTotalScore(BigDecimal latestTotalScore) { this.latestTotalScore = latestTotalScore; }
        public String getLatestReviewComment() { return latestReviewComment; }
        public void setLatestReviewComment(String latestReviewComment) { this.latestReviewComment = latestReviewComment; }
        public List<ScoreItem> getLatestScores() { return latestScores; }
        public void setLatestScores(List<ScoreItem> latestScores) { this.latestScores = latestScores; }
    }

    public static class ReviewSheet {
        private Long id;
        private Long assignmentId;
        private Long batchId;
        private Long candidateId;
        private Integer versionNo;
        private String status;
        private BigDecimal totalScore;
        private String reviewComment;
        private LocalDateTime submittedAt;
        private List<ScoreItem> scores;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAssignmentId() { return assignmentId; }
        public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Integer getVersionNo() { return versionNo; }
        public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalScore() { return totalScore; }
        public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public List<ScoreItem> getScores() { return scores; }
        public void setScores(List<ScoreItem> scores) { this.scores = scores; }
    }

    public static class ScoreItem {
        private Long criterionId;
        private BigDecimal score;
        private String comment;

        public Long getCriterionId() { return criterionId; }
        public void setCriterionId(Long criterionId) { this.criterionId = criterionId; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class Aggregate {
        private Long id;
        private Long batchId;
        private Long candidateId;
        private BigDecimal aggregateScore;
        private BigDecimal minimumScore;
        private BigDecimal maximumScore;
        private BigDecimal scoreStddev;
        private Integer submittedReviewerCount;
        private Integer validReviewerCount;
        private Integer rankNo;
        private String decision;
        private String decisionReason;
        private Long decidedBy;
        private String decidedByUuid;
        private LocalDateTime decidedAt;
        private String anomalyFlagsJson;
        private String status;
        private LocalDateTime calculatedAt;
        private LocalDateTime finalizedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public BigDecimal getAggregateScore() { return aggregateScore; }
        public void setAggregateScore(BigDecimal aggregateScore) { this.aggregateScore = aggregateScore; }
        public BigDecimal getMinimumScore() { return minimumScore; }
        public void setMinimumScore(BigDecimal minimumScore) { this.minimumScore = minimumScore; }
        public BigDecimal getMaximumScore() { return maximumScore; }
        public void setMaximumScore(BigDecimal maximumScore) { this.maximumScore = maximumScore; }
        public BigDecimal getScoreStddev() { return scoreStddev; }
        public void setScoreStddev(BigDecimal scoreStddev) { this.scoreStddev = scoreStddev; }
        public Integer getSubmittedReviewerCount() { return submittedReviewerCount; }
        public void setSubmittedReviewerCount(Integer submittedReviewerCount) { this.submittedReviewerCount = submittedReviewerCount; }
        public Integer getValidReviewerCount() { return validReviewerCount; }
        public void setValidReviewerCount(Integer validReviewerCount) { this.validReviewerCount = validReviewerCount; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getDecisionReason() { return decisionReason; }
        public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
        public Long getDecidedBy() { return decidedBy; }
        public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
        public String getDecidedByUuid() { return decidedByUuid; }
        public void setDecidedByUuid(String decidedByUuid) { this.decidedByUuid = decidedByUuid; }
        public LocalDateTime getDecidedAt() { return decidedAt; }
        public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
        public String getAnomalyFlagsJson() { return anomalyFlagsJson; }
        public void setAnomalyFlagsJson(String anomalyFlagsJson) { this.anomalyFlagsJson = anomalyFlagsJson; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCalculatedAt() { return calculatedAt; }
        public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
        public LocalDateTime getFinalizedAt() { return finalizedAt; }
        public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
    }

    public static class Publication {
        private Long id;
        private Long batchId;
        private Integer publicationVersion;
        private String status;
        private String payloadJson;
        private String payloadHash;
        private LocalDateTime publishedAt;
        private LocalDateTime revokedAt;
        private String revokeReason;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Integer getPublicationVersion() { return publicationVersion; }
        public void setPublicationVersion(Integer publicationVersion) { this.publicationVersion = publicationVersion; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPayloadJson() { return payloadJson; }
        public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
        public String getPayloadHash() { return payloadHash; }
        public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public LocalDateTime getRevokedAt() { return revokedAt; }
        public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
        public String getRevokeReason() { return revokeReason; }
        public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    }

    public static class PublishedResult {
        private Long publicationId;
        private Integer publicationVersion;
        private Long batchId;
        private Long competitionId;
        private Long stageId;
        private Long candidateId;
        private Long registrationId;
        private String competitionTitle;
        private String stageName;
        private String registrationNo;
        private BigDecimal aggregateScore;
        private Integer rankNo;
        private String decision;
        private LocalDateTime publishedAt;
        private Long appealId;
        private String appealStatus;

        public Long getPublicationId() { return publicationId; }
        public void setPublicationId(Long publicationId) { this.publicationId = publicationId; }
        public Integer getPublicationVersion() { return publicationVersion; }
        public void setPublicationVersion(Integer publicationVersion) { this.publicationVersion = publicationVersion; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public String getRegistrationNo() { return registrationNo; }
        public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
        public BigDecimal getAggregateScore() { return aggregateScore; }
        public void setAggregateScore(BigDecimal aggregateScore) { this.aggregateScore = aggregateScore; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public Long getAppealId() { return appealId; }
        public void setAppealId(Long appealId) { this.appealId = appealId; }
        public String getAppealStatus() { return appealStatus; }
        public void setAppealStatus(String appealStatus) { this.appealStatus = appealStatus; }
    }

    public static class Appeal {
        private Long id;
        private Long publicationId;
        private Long batchId;
        private Long competitionId;
        private Long stageId;
        private Long candidateId;
        private Long registrationId;
        private String appealNo;
        private BigDecimal aggregateScore;
        private Integer rankNo;
        private String decision;
        private String appealReason;
        private String status;
        private String resolution;
        private Long resolvedBy;
        private String resolvedByUuid;
        private LocalDateTime resolvedAt;
        private Long createdBy;
        private String createdByUuid;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getPublicationId() { return publicationId; }
        public void setPublicationId(Long publicationId) { this.publicationId = publicationId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public String getAppealNo() { return appealNo; }
        public void setAppealNo(String appealNo) { this.appealNo = appealNo; }
        public BigDecimal getAggregateScore() { return aggregateScore; }
        public void setAggregateScore(BigDecimal aggregateScore) { this.aggregateScore = aggregateScore; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getAppealReason() { return appealReason; }
        public void setAppealReason(String appealReason) { this.appealReason = appealReason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public Long getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
        public String getResolvedByUuid() { return resolvedByUuid; }
        public void setResolvedByUuid(String resolvedByUuid) { this.resolvedByUuid = resolvedByUuid; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public String getCreatedByUuid() { return createdByUuid; }
        public void setCreatedByUuid(String createdByUuid) { this.createdByUuid = createdByUuid; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
