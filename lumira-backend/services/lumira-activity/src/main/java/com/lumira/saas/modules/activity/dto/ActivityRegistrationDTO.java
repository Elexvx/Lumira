package com.lumira.saas.modules.activity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ActivityRegistrationDTO {
    private ActivityRegistrationDTO() {}

    public static class CreateRequest {
        @NotNull private Long activityId;
        @NotBlank @Size(max = 128) private String name;
        @Size(max = 32) private String mobile;
        @Email @Size(max = 255) private String email;
        @Size(max = 255) private String organization;
        @Size(max = 128) private String position;
        @Size(max = 1000) private String remark;
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
