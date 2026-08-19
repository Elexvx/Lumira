package com.lumira.saas.modules.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CompetitionRegistrationDTO {
    private CompetitionRegistrationDTO() {
    }

    public static class RegistrationCreateRequest {
        @NotNull
        private Long competitionId;
        private Long teamId;
        private Long projectId;
        private Map<String, Object> registrationExtraValues;
        @Valid
        private TeamSnapshotRequest teamSnapshot;
        @Valid
        private ProjectSnapshotRequest projectSnapshot;
        @Valid
        private List<MemberSnapshotRequest> members = new ArrayList<>();

        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public Map<String, Object> getRegistrationExtraValues() { return registrationExtraValues; }
        public void setRegistrationExtraValues(Map<String, Object> registrationExtraValues) { this.registrationExtraValues = registrationExtraValues; }
        public TeamSnapshotRequest getTeamSnapshot() { return teamSnapshot; }
        public void setTeamSnapshot(TeamSnapshotRequest teamSnapshot) { this.teamSnapshot = teamSnapshot; }
        public ProjectSnapshotRequest getProjectSnapshot() { return projectSnapshot; }
        public void setProjectSnapshot(ProjectSnapshotRequest projectSnapshot) { this.projectSnapshot = projectSnapshot; }
        public List<MemberSnapshotRequest> getMembers() { return members; }
        public void setMembers(List<MemberSnapshotRequest> members) { this.members = members == null ? new ArrayList<>() : members; }
    }

    /** Atomically confirms the complete registration after the user reviews every section. */
    public static class RegistrationConfirmRequest {
        @NotNull
        private RegistrationCreateRequest registration;
        @Valid
        private ProjectDraftRequest project;
        @Valid
        private MaterialSubmitRequest materials;

        public RegistrationCreateRequest getRegistration() { return registration; }
        public void setRegistration(RegistrationCreateRequest registration) { this.registration = registration; }
        public ProjectDraftRequest getProject() { return project; }
        public void setProject(ProjectDraftRequest project) { this.project = project; }
        public MaterialSubmitRequest getMaterials() { return materials; }
        public void setMaterials(MaterialSubmitRequest materials) { this.materials = materials; }
    }

    public static class ProjectDraftRequest {
        @NotBlank
        @Size(max = 128)
        private String title;
        @Size(max = 64)
        private String category;
        @Size(max = 1000)
        private String description;
        @Size(max = 1024)
        private String imageUrl;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    public static class TeamSnapshotRequest {
        @Size(max = 128)
        private String teamName;
        @Size(max = 64)
        private String teamType;
        @Size(max = 1024)
        private String avatarUrl;
        @Size(max = 32)
        private String visibility;
        @Size(max = 32)
        private String joinMode;
        @Size(max = 1000)
        private String description;
        private Map<String, Object> extraValues;

        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getTeamType() { return teamType; }
        public void setTeamType(String teamType) { this.teamType = teamType; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public String getJoinMode() { return joinMode; }
        public void setJoinMode(String joinMode) { this.joinMode = joinMode; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getExtraValues() { return extraValues; }
        public void setExtraValues(Map<String, Object> extraValues) { this.extraValues = extraValues; }
    }

    public static class ProjectSnapshotRequest {
        @Size(max = 128)
        private String title;
        @Size(max = 64)
        private String category;
        @Size(max = 1000)
        private String description;
        @Size(max = 1024)
        private String imageUrl;
        private Map<String, Object> extraValues;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Map<String, Object> getExtraValues() { return extraValues; }
        public void setExtraValues(Map<String, Object> extraValues) { this.extraValues = extraValues; }
    }

    public static class MemberSnapshotRequest {
        @Pattern(regexp = "^(STUDENT|TEACHER)$", message = "Participant type must be STUDENT or TEACHER")
        private String participantType;
        @Size(max = 128)
        @Pattern(regexp = "^[\\p{IsHan}A-Za-z·]{2,64}$", message = "Member name may only contain Chinese characters, English letters, or a middle dot")
        private String memberName;
        @Size(max = 64)
        private String employeeNo;
        @Size(max = 128)
        private String departmentName;
        @Size(max = 32)
        private String role;
        @Size(max = 512)
        private String remark;
        private Map<String, Object> extraValues;

        public String getParticipantType() { return participantType; }
        public void setParticipantType(String participantType) { this.participantType = participantType; }
        public String getMemberName() { return memberName; }
        public void setMemberName(String memberName) { this.memberName = memberName; }
        public String getEmployeeNo() { return employeeNo; }
        public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public Map<String, Object> getExtraValues() { return extraValues; }
        public void setExtraValues(Map<String, Object> extraValues) { this.extraValues = extraValues; }
    }

    public static class StageUpsertRequest {
        @NotBlank
        @Size(max = 32)
        private String stageCode;
        @NotBlank
        @Size(max = 128)
        private String stageName;
        @Size(max = 32)
        private String status;
        private Integer sort;
        private LocalDateTime materialSubmitStart;
        private LocalDateTime materialSubmitEnd;
        private LocalDateTime reviewStart;
        private LocalDateTime reviewEnd;
        @Size(max = 16)
        private String promotionRuleType;
        private BigDecimal promotionRuleValue;
        @Size(max = 32)
        private String promotionTiePolicy;

        public String getStageCode() { return stageCode; }
        public void setStageCode(String stageCode) { this.stageCode = stageCode; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public LocalDateTime getMaterialSubmitStart() { return materialSubmitStart; }
        public void setMaterialSubmitStart(LocalDateTime materialSubmitStart) { this.materialSubmitStart = materialSubmitStart; }
        public LocalDateTime getMaterialSubmitEnd() { return materialSubmitEnd; }
        public void setMaterialSubmitEnd(LocalDateTime materialSubmitEnd) { this.materialSubmitEnd = materialSubmitEnd; }
        public LocalDateTime getReviewStart() { return reviewStart; }
        public void setReviewStart(LocalDateTime reviewStart) { this.reviewStart = reviewStart; }
        public LocalDateTime getReviewEnd() { return reviewEnd; }
        public void setReviewEnd(LocalDateTime reviewEnd) { this.reviewEnd = reviewEnd; }
        public String getPromotionRuleType() { return promotionRuleType; }
        public void setPromotionRuleType(String promotionRuleType) { this.promotionRuleType = promotionRuleType; }
        public BigDecimal getPromotionRuleValue() { return promotionRuleValue; }
        public void setPromotionRuleValue(BigDecimal promotionRuleValue) { this.promotionRuleValue = promotionRuleValue; }
        public String getPromotionTiePolicy() { return promotionTiePolicy; }
        public void setPromotionTiePolicy(String promotionTiePolicy) { this.promotionTiePolicy = promotionTiePolicy; }
    }

    public static class StageReviewDecisionRequest {
        private BigDecimal score;
        @NotBlank
        @Size(max = 32)
        private String decision;
        @Size(max = 1000)
        private String comment;

        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class StageFormUpsertRequest {
        @NotBlank
        @Size(max = 128)
        private String formName;
        @NotBlank
        private String formSchemaJson;
        private Integer version;
        @Size(max = 32)
        private String status;

        public String getFormName() { return formName; }
        public void setFormName(String formName) { this.formName = formName; }
        public String getFormSchemaJson() { return formSchemaJson; }
        public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class MaterialSubmitRequest {
        @NotNull
        private Long stageId;
        @Valid
        private List<MaterialValueRequest> values = new ArrayList<>();

        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public List<MaterialValueRequest> getValues() { return values; }
        public void setValues(List<MaterialValueRequest> values) { this.values = values == null ? new ArrayList<>() : values; }
    }

    public static class MaterialValueRequest {
        @NotBlank
        @Size(max = 128)
        private String fieldKey;
        @NotBlank
        @Size(max = 32)
        private String fieldType;
        private String textValue;
        private Long fileId;
        private String jsonValue;

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

    public static class PaymentOrderRequest {
        @Size(max = 64)
        private String providerCode;
        @Size(max = 32)
        private String clientType;
        @Size(max = 64)
        private String clientIp;
        @Size(max = 1024)
        private String notifyUrl;
        @Size(max = 1024)
        private String returnUrl;

        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getClientType() { return clientType; }
        public void setClientType(String clientType) { this.clientType = clientType; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    }

    public static class RegistrationExportRequest {
        @NotNull
        private Long competitionId;
        @Size(max = 32)
        private String status;
        @Size(max = 128)
        private String keyword;
        @Size(max = 500)
        private java.util.List<@NotNull Long> registrationIds;

        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public java.util.List<Long> getRegistrationIds() { return registrationIds; }
        public void setRegistrationIds(java.util.List<Long> registrationIds) { this.registrationIds = registrationIds; }
    }
}
