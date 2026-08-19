package com.lumira.saas.modules.activity.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lumira.saas.modules.activity.model.ActivityRegistrationAnswer;
import java.time.LocalDateTime;
import java.util.List;

public class ActivityRegistrationVO {
    private Long id;
    private String applicationNo;
    private Long activityId;
    private String activityTitle;
    private String name;
    private String mobile;
    private String email;
    private String organization;
    private String position;
    private String remark;
    private String status;
    private LocalDateTime submittedAt;
    private Long ownerUserId;
    private String ownerUsername;
    @JsonIgnore
    private String formDataJson;
    private List<ActivityRegistrationAnswer> answers;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getApplicationNo() { return applicationNo; } public void setApplicationNo(String value) { applicationNo = value; }
    public Long getActivityId() { return activityId; } public void setActivityId(Long value) { activityId = value; }
    public String getActivityTitle() { return activityTitle; } public void setActivityTitle(String value) { activityTitle = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getMobile() { return mobile; } public void setMobile(String value) { mobile = value; }
    public String getEmail() { return email; } public void setEmail(String value) { email = value; }
    public String getOrganization() { return organization; } public void setOrganization(String value) { organization = value; }
    public String getPosition() { return position; } public void setPosition(String value) { position = value; }
    public String getRemark() { return remark; } public void setRemark(String value) { remark = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public Long getOwnerUserId() { return ownerUserId; } public void setOwnerUserId(Long value) { ownerUserId = value; }
    public String getOwnerUsername() { return ownerUsername; } public void setOwnerUsername(String value) { ownerUsername = value; }
    public String getFormDataJson() { return formDataJson; } public void setFormDataJson(String value) { formDataJson = value; }
    public List<ActivityRegistrationAnswer> getAnswers() { return answers; } public void setAnswers(List<ActivityRegistrationAnswer> value) { answers = value; }
}
