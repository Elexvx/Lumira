package com.lumira.saas.modules.competition.vo;

import java.time.LocalDateTime;

public final class CompetitionRegistrationVO {
    private CompetitionRegistrationVO() {
    }

    public static class Registration {
        private Long id;
        private String registrationNo;
        private Long competitionId;
        private Long teamId;
        private Long projectId;
        private Long ownerUserId;
        private String status;
        private String feeMode;
        private Long entryFeeMinor;
        private Integer memberCount;
        private Long payableAmountMinor;
        private String currency;
        private String paymentOrderNo;
        private String participantNo;
        private String teamSnapshotJson;
        private String projectSnapshotJson;
        private String memberSnapshotJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRegistrationNo() { return registrationNo; }
        public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFeeMode() { return feeMode; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public Long getEntryFeeMinor() { return entryFeeMinor; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
        public Long getPayableAmountMinor() { return payableAmountMinor; }
        public void setPayableAmountMinor(Long payableAmountMinor) { this.payableAmountMinor = payableAmountMinor; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getPaymentOrderNo() { return paymentOrderNo; }
        public void setPaymentOrderNo(String paymentOrderNo) { this.paymentOrderNo = paymentOrderNo; }
        public String getParticipantNo() { return participantNo; }
        public void setParticipantNo(String participantNo) { this.participantNo = participantNo; }
        public String getTeamSnapshotJson() { return teamSnapshotJson; }
        public void setTeamSnapshotJson(String teamSnapshotJson) { this.teamSnapshotJson = teamSnapshotJson; }
        public String getProjectSnapshotJson() { return projectSnapshotJson; }
        public void setProjectSnapshotJson(String projectSnapshotJson) { this.projectSnapshotJson = projectSnapshotJson; }
        public String getMemberSnapshotJson() { return memberSnapshotJson; }
        public void setMemberSnapshotJson(String memberSnapshotJson) { this.memberSnapshotJson = memberSnapshotJson; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Stage {
        private Long id;
        private Long competitionId;
        private String stageCode;
        private String stageName;
        private String status;
        private Integer sort;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public String getStageCode() { return stageCode; }
        public void setStageCode(String stageCode) { this.stageCode = stageCode; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
    }

    public static class StageForm {
        private Long id;
        private Long competitionId;
        private Long stageId;
        private String formName;
        private String formSchemaJson;
        private Integer version;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public String getFormName() { return formName; }
        public void setFormName(String formName) { this.formName = formName; }
        public String getFormSchemaJson() { return formSchemaJson; }
        public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PaymentOrder {
        private String orderNo;
        private Long amountMinor;
        private String currency;
        private String status;
        private String paymentUrl;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Long getAmountMinor() { return amountMinor; }
        public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPaymentUrl() { return paymentUrl; }
        public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    }

    public static class PaymentRecord {
        private Long registrationId;
        private String registrationNo;
        private Long competitionId;
        private String competitionCode;
        private String competitionTitle;
        private Long teamId;
        private String teamName;
        private Long projectId;
        private String projectTitle;
        private Long ownerUserId;
        private String registrationStatus;
        private String participantNo;
        private Integer memberCount;
        private Long payableAmountMinor;
        private String orderNo;
        private String providerCode;
        private String providerOrderNo;
        private String subject;
        private Long amountMinor;
        private String currency;
        private String paymentStatus;
        private String paymentUrl;
        private String failureCode;
        private String failureMessage;
        private LocalDateTime orderCreatedAt;
        private LocalDateTime paidAt;
        private LocalDateTime registrationCreatedAt;
        private LocalDateTime updatedAt;

        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public String getRegistrationNo() { return registrationNo; }
        public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public String getCompetitionCode() { return competitionCode; }
        public void setCompetitionCode(String competitionCode) { this.competitionCode = competitionCode; }
        public String getCompetitionTitle() { return competitionTitle; }
        public void setCompetitionTitle(String competitionTitle) { this.competitionTitle = competitionTitle; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public String getProjectTitle() { return projectTitle; }
        public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getRegistrationStatus() { return registrationStatus; }
        public void setRegistrationStatus(String registrationStatus) { this.registrationStatus = registrationStatus; }
        public String getParticipantNo() { return participantNo; }
        public void setParticipantNo(String participantNo) { this.participantNo = participantNo; }
        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
        public Long getPayableAmountMinor() { return payableAmountMinor; }
        public void setPayableAmountMinor(Long payableAmountMinor) { this.payableAmountMinor = payableAmountMinor; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getProviderOrderNo() { return providerOrderNo; }
        public void setProviderOrderNo(String providerOrderNo) { this.providerOrderNo = providerOrderNo; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public Long getAmountMinor() { return amountMinor; }
        public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        public String getPaymentUrl() { return paymentUrl; }
        public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
        public String getFailureMessage() { return failureMessage; }
        public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
        public LocalDateTime getOrderCreatedAt() { return orderCreatedAt; }
        public void setOrderCreatedAt(LocalDateTime orderCreatedAt) { this.orderCreatedAt = orderCreatedAt; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
        public LocalDateTime getRegistrationCreatedAt() { return registrationCreatedAt; }
        public void setRegistrationCreatedAt(LocalDateTime registrationCreatedAt) { this.registrationCreatedAt = registrationCreatedAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class MaterialSubmission {
        private Long id;
        private Long registrationId;
        private Long competitionId;
        private Long stageId;
        private Integer formVersion;
        private Long submitterUserId;
        private String status;
        private LocalDateTime submittedAt;
        private LocalDateTime lockedAt;
        private java.util.List<MaterialValue> values = java.util.List.of();

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRegistrationId() { return registrationId; }
        public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public Integer getFormVersion() { return formVersion; }
        public void setFormVersion(Integer formVersion) { this.formVersion = formVersion; }
        public Long getSubmitterUserId() { return submitterUserId; }
        public void setSubmitterUserId(Long submitterUserId) { this.submitterUserId = submitterUserId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public LocalDateTime getLockedAt() { return lockedAt; }
        public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
        public java.util.List<MaterialValue> getValues() { return values; }
        public void setValues(java.util.List<MaterialValue> values) { this.values = values == null ? java.util.List.of() : values; }
    }

    public static class MaterialValue {
        private Long id;
        private Long submissionId;
        private String fieldKey;
        private String fieldType;
        private String textValue;
        private Long fileId;
        private String jsonValue;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSubmissionId() { return submissionId; }
        public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
        public String getFieldKey() { return fieldKey; }
        public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public String getTextValue() { return textValue; }
        public void setTextValue(String textValue) { this.textValue = textValue; }
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getJsonValue() { return jsonValue; }
        public void setJsonValue(String jsonValue) { this.jsonValue = jsonValue; }
    }
}
