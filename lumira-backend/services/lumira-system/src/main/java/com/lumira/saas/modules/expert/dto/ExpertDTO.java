package com.lumira.saas.modules.expert.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ExpertDTO {
    private ExpertDTO() {
    }

    public static class ExpertUpsertRequest {
        @Size(max = 64)
        private String code;
        @NotBlank
        @Size(max = 64)
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
        private String phone;
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

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
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
    }
}
