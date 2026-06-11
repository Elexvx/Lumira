package com.lumira.saas.modules.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class UserUpsertRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "用户名只能包含英文字母、数字、下划线和连字符")
    private String username;
    private String password;
    @Pattern(regexp = "^(?:$|1[3-9]\\d{9})$", message = "请输入有效手机号")
    private String mobile;
    private String nickname;
    private String realName;
    private String avatarUrl;
    @Email(message = "请输入有效邮箱地址")
    private String email;
    private String birthMonth;
    private String gender;
    private String region;
    private String availableTime;
    @Pattern(regexp = "^(?:$|\\d{15}|\\d{17}[\\dXx])$", message = "请输入有效身份证号码")
    private String idCardNumber;
    @NotBlank(message = "用户状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "用户状态只能是 ENABLED 或 DISABLED")
    private String status;
    private List<@Positive(message = "角色ID必须为正整数") Long> roleIds;
    private List<@Positive(message = "部门ID必须为正整数") Long> deptIds;
    @Positive(message = "主部门ID必须为正整数")
    private Long primaryDeptId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? null : username.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile == null ? null : mobile.trim(); }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl == null ? null : avatarUrl.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public String getBirthMonth() { return birthMonth; }
    public void setBirthMonth(String birthMonth) { this.birthMonth = birthMonth == null ? null : birthMonth.trim(); }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender == null ? null : gender.trim(); }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region == null ? null : region.trim(); }
    public String getAvailableTime() { return availableTime; }
    public void setAvailableTime(String availableTime) { this.availableTime = availableTime == null ? null : availableTime.trim(); }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber == null ? null : idCardNumber.trim(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? null : status.trim().toUpperCase(); }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
    public List<Long> getDeptIds() { return deptIds; }
    public void setDeptIds(List<Long> deptIds) { this.deptIds = deptIds; }
    public Long getPrimaryDeptId() { return primaryDeptId; }
    public void setPrimaryDeptId(Long primaryDeptId) { this.primaryDeptId = primaryDeptId; }
}
