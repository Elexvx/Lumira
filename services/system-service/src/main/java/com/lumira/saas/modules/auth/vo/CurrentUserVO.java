package com.lumira.saas.modules.auth.vo;

import java.util.List;
import java.util.Map;

public class CurrentUserVO {

    private Long userId;
    private String username;
    private String nickname;
    private String realName;
    private String avatarUrl;
    private String mobile;
    private String email;
    private String birthMonth;
    private String gender;
    private String region;
    private String availableTime;
    private String idCardNumber;
    private Map<String, String> extraProfileValues;
    private String locale;
    private Long simulatedRoleId;
    private List<RoleOptionVO> availableRoles;
    private String sessionId;
    private String permissionsVersion;
    private Integer sessionVersion;
    private List<String> permissions;
    private String defaultHomePath;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
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

    public Map<String, String> getExtraProfileValues() {
        return extraProfileValues;
    }

    public void setExtraProfileValues(Map<String, String> extraProfileValues) {
        this.extraProfileValues = extraProfileValues;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Long getSimulatedRoleId() {
        return simulatedRoleId;
    }

    public void setSimulatedRoleId(Long simulatedRoleId) {
        this.simulatedRoleId = simulatedRoleId;
    }

    public List<RoleOptionVO> getAvailableRoles() {
        return availableRoles;
    }

    public void setAvailableRoles(List<RoleOptionVO> availableRoles) {
        this.availableRoles = availableRoles;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    public void setPermissionsVersion(String permissionsVersion) {
        this.permissionsVersion = permissionsVersion;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getDefaultHomePath() {
        return defaultHomePath;
    }

    public void setDefaultHomePath(String defaultHomePath) {
        this.defaultHomePath = defaultHomePath;
    }

    public static class RoleOptionVO {
        private Long id;
        private String roleCode;
        private String roleName;
        private String roleType;
        private Integer permissionCount;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getRoleType() {
            return roleType;
        }

        public void setRoleType(String roleType) {
            this.roleType = roleType;
        }

        public Integer getPermissionCount() {
            return permissionCount;
        }

        public void setPermissionCount(Integer permissionCount) {
            this.permissionCount = permissionCount;
        }
    }
}
