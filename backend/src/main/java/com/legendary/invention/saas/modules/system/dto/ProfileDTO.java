package com.legendary.invention.saas.modules.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ProfileDTO {

    private ProfileDTO() {
    }

    public static class EmailUpdateRequest {
        @NotBlank(message = "请输入邮箱")
        @Email(message = "请输入有效邮箱地址")
        private String email;
        private String challengeId;
        private String verificationCode;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getChallengeId() {
            return challengeId;
        }

        public void setChallengeId(String challengeId) {
            this.challengeId = challengeId;
        }

        public String getVerificationCode() {
            return verificationCode;
        }

        public void setVerificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
        }
    }

    public static class BasicInfoUpdateRequest {
        private String avatarUrl;
        private String nickname;
        private String realName;
        @Pattern(regexp = "^(?:$|1[3-9]\\d{9})$", message = "请输入有效手机号")
        private String mobile;
        private String email;
        private String birthMonth;
        private String gender;
        private String region;
        private String availableTime;
        @Pattern(regexp = "^(?:$|\\d{15}|\\d{17}[\\dXx])$", message = "请输入有效身份证号码")
        private String idCardNumber;

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile == null ? null : mobile.trim();
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getBirthMonth() {
            return birthMonth;
        }

        public void setBirthMonth(String birthMonth) {
            this.birthMonth = birthMonth;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAvailableTime() {
            return availableTime;
        }

        public void setAvailableTime(String availableTime) {
            this.availableTime = availableTime;
        }

        public String getIdCardNumber() {
            return idCardNumber;
        }

        public void setIdCardNumber(String idCardNumber) {
            this.idCardNumber = idCardNumber == null ? null : idCardNumber.trim();
        }
    }

    public static class ContactBindChallengeRequest {
        @NotBlank(message = "绑定类型不能为空")
        private String contactType;
        @NotBlank(message = "请输入绑定信息")
        private String value;

        public String getContactType() {
            return contactType;
        }

        public void setContactType(String contactType) {
            this.contactType = contactType == null ? null : contactType.trim();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value == null ? null : value.trim();
        }
    }

    public static class ContactBindRequest {
        @NotBlank(message = "绑定类型不能为空")
        private String contactType;
        @NotBlank(message = "请输入绑定信息")
        private String value;
        private String challengeId;
        private String verificationCode;

        public String getContactType() {
            return contactType;
        }

        public void setContactType(String contactType) {
            this.contactType = contactType == null ? null : contactType.trim();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value == null ? null : value.trim();
        }

        public String getChallengeId() {
            return challengeId;
        }

        public void setChallengeId(String challengeId) {
            this.challengeId = challengeId;
        }

        public String getVerificationCode() {
            return verificationCode;
        }

        public void setVerificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
        }
    }
}
