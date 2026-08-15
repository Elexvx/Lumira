package com.lumira.saas.modules.expert.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class ExpertDTO {
    private ExpertDTO() {
    }

    public static class ExpertUpsertRequest {
        @Size(max = 64)
        private String code;
        @Size(max = 64)
        private String competitionUuid;
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[\\p{IsHan}A-Za-z·\\s]{2,64}$", message = "专家姓名只能包含中文、英文字母、空格和间隔号")
        private String name;
        @Size(max = 128)
        private String title;
        @Size(max = 128)
        private String organization;
        @Size(max = 128)
        private String position;
        @NotBlank
        @Size(max = 255)
        private String expertise;
        @Size(max = 64)
        @Pattern(regexp = "^(?:$|1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}(?:-\\d{1,6})?)$", message = "请输入有效联系电话")
        private String phone;
        @Size(max = 32)
        @Pattern(regexp = "^(?:$|1[3-9]\\d{9})$", message = "请输入有效手机号")
        private String mobile;
        @Size(max = 32)
        @Pattern(regexp = "^(?:$|\\d{15}|\\d{17}[\\dXx])$", message = "请输入有效身份证号码")
        private String idCardNumber;
        @Email
        @Size(max = 128)
        private String email;
        @Size(max = 512)
        private String avatarUrl;
        @Size(max = 1000)
        private String bio;
        @Size(max = 1000)
        private String tags;
        @Size(max = 32)
        private String status;
        private Integer sort;
        private Map<String, Object> extraValues;
        private String extraValuesJson;

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
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public Map<String, Object> getExtraValues() { return extraValues; }
        public void setExtraValues(Map<String, Object> extraValues) { this.extraValues = extraValues; }
        public String getExtraValuesJson() { return extraValuesJson; }
        public void setExtraValuesJson(String extraValuesJson) { this.extraValuesJson = extraValuesJson; }
    }
}
