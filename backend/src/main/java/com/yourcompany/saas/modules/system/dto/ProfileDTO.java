package com.yourcompany.saas.modules.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class ProfileDTO {

    private ProfileDTO() {
    }

    public static class EmailUpdateRequest {
        @NotBlank(message = "请输入邮箱")
        @Email(message = "请输入有效邮箱地址")
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class BasicInfoUpdateRequest {
        private String avatarUrl;
        private String nickname;
        private String realName;
        private String mobile;
        private String email;
        private String birthMonth;
        private String gender;
        private String region;
        private String availableTime;
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
            this.mobile = mobile;
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
            this.idCardNumber = idCardNumber;
        }
    }
}
