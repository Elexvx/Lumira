package com.lumira.saas.modules.system.user.vo;

import java.time.LocalDateTime;
import java.util.List;

public class UserVO {

    private Long id;
    private String userNo;
    private String username;
    private String mobile;
    private String idCardNumber;
    private String nickname;
    private String realName;
    private String avatarUrl;
    private String email;
    private String birthMonth;
    private String gender;
    private String region;
    private String availableTime;
    private String status;
    private String source;
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private List<String> tenantNames;
    private List<String> roleNames;
    private List<String> deptNames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserNo() { return userNo; }
    public void setUserNo(String userNo) { this.userNo = userNo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBirthMonth() { return birthMonth; }
    public void setBirthMonth(String birthMonth) { this.birthMonth = birthMonth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAvailableTime() { return availableTime; }
    public void setAvailableTime(String availableTime) { this.availableTime = availableTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public List<String> getTenantNames() { return tenantNames; }
    public void setTenantNames(List<String> tenantNames) { this.tenantNames = tenantNames; }
    public List<String> getRoleNames() { return roleNames; }
    public void setRoleNames(List<String> roleNames) { this.roleNames = roleNames; }
    public List<String> getDeptNames() { return deptNames; }
    public void setDeptNames(List<String> deptNames) { this.deptNames = deptNames; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
