package com.lumira.saas.modules.system.profile.vo;

import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;

import java.util.List;

public class ProfileSummaryVO {

    private CurrentUserVO currentUser;
    private List<String> roleNames;
    private Integer permissionCount;
    private List<AuditLogVO> recentLoginLogs;
    private List<ProfileFieldSettingVO> profileFieldSettings;
    private ProfileCompletionSummaryVO profileCompletion;
    private Boolean mobileBindAvailable;
    private Boolean emailBindAvailable;
    private Boolean mobileBindVerificationRequired;
    private Boolean emailBindVerificationRequired;

    public CurrentUserVO getCurrentUser() { return currentUser; }
    public void setCurrentUser(CurrentUserVO currentUser) { this.currentUser = currentUser; }
    public List<String> getRoleNames() { return roleNames; }
    public void setRoleNames(List<String> roleNames) { this.roleNames = roleNames; }
    public Integer getPermissionCount() { return permissionCount; }
    public void setPermissionCount(Integer permissionCount) { this.permissionCount = permissionCount; }
    public List<AuditLogVO> getRecentLoginLogs() { return recentLoginLogs; }
    public void setRecentLoginLogs(List<AuditLogVO> recentLoginLogs) { this.recentLoginLogs = recentLoginLogs; }
    public List<ProfileFieldSettingVO> getProfileFieldSettings() { return profileFieldSettings; }
    public void setProfileFieldSettings(List<ProfileFieldSettingVO> profileFieldSettings) { this.profileFieldSettings = profileFieldSettings; }
    public ProfileCompletionSummaryVO getProfileCompletion() { return profileCompletion; }
    public void setProfileCompletion(ProfileCompletionSummaryVO profileCompletion) { this.profileCompletion = profileCompletion; }
    public Boolean getMobileBindAvailable() { return mobileBindAvailable; }
    public void setMobileBindAvailable(Boolean mobileBindAvailable) { this.mobileBindAvailable = mobileBindAvailable; }
    public Boolean getEmailBindAvailable() { return emailBindAvailable; }
    public void setEmailBindAvailable(Boolean emailBindAvailable) { this.emailBindAvailable = emailBindAvailable; }
    public Boolean getMobileBindVerificationRequired() { return mobileBindVerificationRequired; }
    public void setMobileBindVerificationRequired(Boolean mobileBindVerificationRequired) { this.mobileBindVerificationRequired = mobileBindVerificationRequired; }
    public Boolean getEmailBindVerificationRequired() { return emailBindVerificationRequired; }
    public void setEmailBindVerificationRequired(Boolean emailBindVerificationRequired) { this.emailBindVerificationRequired = emailBindVerificationRequired; }
}
