package com.lumira.saas.modules.expert.vo;

import java.time.LocalDateTime;

public final class ExpertVO {
    private ExpertVO() {
    }

    public static class Expert {
        private Long id;
        private String code;
        private String competitionUuid;
        private String name;
        private String title;
        private String organization;
        private String position;
        private String expertise;
        private String phone;
        private String mobile;
        private String idCardNumber;
        private Long userId;
        private String userUuid;
        private String accountStatus;
        private Boolean initialPasswordResetRequired;
        private String initialPassword;
        private String email;
        private String avatarUrl;
        private String bio;
        private String tags;
        private String status;
        private String approvalStatus;
        private Long approvalInstanceId;
        private Integer sort;
        private String extraValuesJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getCompetitionUuid() { return competitionUuid; }
        public void setCompetitionUuid(String competitionUuid) { this.competitionUuid = competitionUuid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getExpertise() { return expertise; }
        public void setExpertise(String expertise) { this.expertise = expertise; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getIdCardNumber() { return idCardNumber; }
        public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserUuid() { return userUuid; }
        public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
        public Boolean getInitialPasswordResetRequired() { return initialPasswordResetRequired; }
        public void setInitialPasswordResetRequired(Boolean initialPasswordResetRequired) { this.initialPasswordResetRequired = initialPasswordResetRequired; }
        public String getInitialPassword() { return initialPassword; }
        public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getApprovalStatus() { return approvalStatus; }
        public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
        public Long getApprovalInstanceId() { return approvalInstanceId; }
        public void setApprovalInstanceId(Long approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public String getExtraValuesJson() { return extraValuesJson; }
        public void setExtraValuesJson(String extraValuesJson) { this.extraValuesJson = extraValuesJson; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
