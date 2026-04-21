package com.legendary.invention.saas.modules.system.vo;

import com.legendary.invention.saas.modules.auth.vo.CurrentUserVO;
import com.legendary.invention.saas.modules.plugin.vo.PluginVO;
import com.legendary.invention.saas.modules.tenant.vo.TenantSummaryVO;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemVO {

    private SystemVO() {
    }

    public static class ShortcutVO {
        private String title;
        private String description;
        private String path;
        private String permission;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }
    }

    public static class DashboardSummaryVO {
        private CurrentUserVO currentUser;
        private TenantSummaryVO currentTenant;
        private List<PluginVO.TenantPluginVO> tenantPlugins;
        private Integer menuCount;
        private Integer permissionCount;
        private List<AuditLogVO> recentLoginLogs;
        private List<AuditLogVO> recentOperationLogs;
        private List<ShortcutVO> shortcuts;

        public CurrentUserVO getCurrentUser() {
            return currentUser;
        }

        public void setCurrentUser(CurrentUserVO currentUser) {
            this.currentUser = currentUser;
        }

        public TenantSummaryVO getCurrentTenant() {
            return currentTenant;
        }

        public void setCurrentTenant(TenantSummaryVO currentTenant) {
            this.currentTenant = currentTenant;
        }

        public List<PluginVO.TenantPluginVO> getTenantPlugins() {
            return tenantPlugins;
        }

        public void setTenantPlugins(List<PluginVO.TenantPluginVO> tenantPlugins) {
            this.tenantPlugins = tenantPlugins;
        }

        public Integer getMenuCount() {
            return menuCount;
        }

        public void setMenuCount(Integer menuCount) {
            this.menuCount = menuCount;
        }

        public Integer getPermissionCount() {
            return permissionCount;
        }

        public void setPermissionCount(Integer permissionCount) {
            this.permissionCount = permissionCount;
        }

        public List<AuditLogVO> getRecentLoginLogs() {
            return recentLoginLogs;
        }

        public void setRecentLoginLogs(List<AuditLogVO> recentLoginLogs) {
            this.recentLoginLogs = recentLoginLogs;
        }

        public List<AuditLogVO> getRecentOperationLogs() {
            return recentOperationLogs;
        }

        public void setRecentOperationLogs(List<AuditLogVO> recentOperationLogs) {
            this.recentOperationLogs = recentOperationLogs;
        }

        public List<ShortcutVO> getShortcuts() {
            return shortcuts;
        }

        public void setShortcuts(List<ShortcutVO> shortcuts) {
            this.shortcuts = shortcuts;
        }
    }

    public static class ProfileSummaryVO {
        private CurrentUserVO currentUser;
        private TenantSummaryVO currentTenant;
        private List<String> roleNames;
        private Integer permissionCount;
        private List<AuditLogVO> recentLoginLogs;
        private List<ProfileFieldSettingVO> profileFieldSettings;

        public CurrentUserVO getCurrentUser() {
            return currentUser;
        }

        public void setCurrentUser(CurrentUserVO currentUser) {
            this.currentUser = currentUser;
        }

        public TenantSummaryVO getCurrentTenant() {
            return currentTenant;
        }

        public void setCurrentTenant(TenantSummaryVO currentTenant) {
            this.currentTenant = currentTenant;
        }

        public List<String> getRoleNames() {
            return roleNames;
        }

        public void setRoleNames(List<String> roleNames) {
            this.roleNames = roleNames;
        }

        public Integer getPermissionCount() {
            return permissionCount;
        }

        public void setPermissionCount(Integer permissionCount) {
            this.permissionCount = permissionCount;
        }

        public List<AuditLogVO> getRecentLoginLogs() {
            return recentLoginLogs;
        }

        public void setRecentLoginLogs(List<AuditLogVO> recentLoginLogs) {
            this.recentLoginLogs = recentLoginLogs;
        }

        public List<ProfileFieldSettingVO> getProfileFieldSettings() {
            return profileFieldSettings;
        }

        public void setProfileFieldSettings(List<ProfileFieldSettingVO> profileFieldSettings) {
            this.profileFieldSettings = profileFieldSettings;
        }
    }

    public static class ProfileFieldSettingVO {
        private String fieldKey;
        private String fieldLabel;
        private String fieldDescription;
        private Boolean visible;

        public String getFieldKey() {
            return fieldKey;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        public String getFieldLabel() {
            return fieldLabel;
        }

        public void setFieldLabel(String fieldLabel) {
            this.fieldLabel = fieldLabel;
        }

        public String getFieldDescription() {
            return fieldDescription;
        }

        public void setFieldDescription(String fieldDescription) {
            this.fieldDescription = fieldDescription;
        }

        public Boolean getVisible() {
            return visible;
        }

        public void setVisible(Boolean visible) {
            this.visible = visible;
        }
    }

    public static class UserVO {
        private Long id;
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
        private List<String> tenantNames;
        private List<String> roleNames;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }

        public String getIdCardNumber() {
            return idCardNumber;
        }

        public void setIdCardNumber(String idCardNumber) {
            this.idCardNumber = idCardNumber;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<String> getTenantNames() {
            return tenantNames;
        }

        public void setTenantNames(List<String> tenantNames) {
            this.tenantNames = tenantNames;
        }

        public List<String> getRoleNames() {
            return roleNames;
        }

        public void setRoleNames(List<String> roleNames) {
            this.roleNames = roleNames;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class UserDetailVO extends UserVO {
        private List<Long> roleIds;
        private List<Long> tenantIds;

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
        }

        public List<Long> getTenantIds() {
            return tenantIds;
        }

        public void setTenantIds(List<Long> tenantIds) {
            this.tenantIds = tenantIds;
        }
    }

    public static class OnlineSessionVO {
        private String sessionId;
        private Long userId;
        private String username;
        private String nickname;
        private String realName;
        private Long currentTenantId;
        private LocalDateTime loginTime;
        private LocalDateTime lastActivityAt;
        private LocalDateTime expireTime;
        private String clientType;
        private String loginIp;
        private String userAgent;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

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

        public Long getCurrentTenantId() {
            return currentTenantId;
        }

        public void setCurrentTenantId(Long currentTenantId) {
            this.currentTenantId = currentTenantId;
        }

        public LocalDateTime getLoginTime() {
            return loginTime;
        }

        public void setLoginTime(LocalDateTime loginTime) {
            this.loginTime = loginTime;
        }

        public LocalDateTime getLastActivityAt() {
            return lastActivityAt;
        }

        public void setLastActivityAt(LocalDateTime lastActivityAt) {
            this.lastActivityAt = lastActivityAt;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }

        public String getClientType() {
            return clientType;
        }

        public void setClientType(String clientType) {
            this.clientType = clientType;
        }

        public String getLoginIp() {
            return loginIp;
        }

        public void setLoginIp(String loginIp) {
            this.loginIp = loginIp;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class RoleVO {
        private Long id;
        private Long tenantId;
        private String roleCode;
        private String roleName;
        private String roleType;
        private Integer permissionCount;
        private Integer userCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
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

        public Integer getUserCount() {
            return userCount;
        }

        public void setUserCount(Integer userCount) {
            this.userCount = userCount;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class RoleDetailVO extends RoleVO {
        private List<String> permissionKeys;

        public List<String> getPermissionKeys() {
            return permissionKeys;
        }

        public void setPermissionKeys(List<String> permissionKeys) {
            this.permissionKeys = permissionKeys;
        }
    }

    public static class PermissionVO {
        private String permissionKey;
        private String permissionName;
        private String permissionGroup;
        private String sourceType;
        private String pluginCode;

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getPermissionName() {
            return permissionName;
        }

        public void setPermissionName(String permissionName) {
            this.permissionName = permissionName;
        }

        public String getPermissionGroup() {
            return permissionGroup;
        }

        public void setPermissionGroup(String permissionGroup) {
            this.permissionGroup = permissionGroup;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getPluginCode() {
            return pluginCode;
        }

        public void setPluginCode(String pluginCode) {
            this.pluginCode = pluginCode;
        }
    }

    public static class PermissionTreeVO {
        private String nodeType;
        private String pageKey;
        private String pageName;
        private String routePath;
        private String icon;
        private String permissionKey;
        private String permissionGroup;
        private String sourceType;
        private boolean selectable;
        private List<PermissionTreeVO> children;
        private List<PermissionActionVO> actionPermissions;

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public String getPageKey() {
            return pageKey;
        }

        public void setPageKey(String pageKey) {
            this.pageKey = pageKey;
        }

        public String getPageName() {
            return pageName;
        }

        public void setPageName(String pageName) {
            this.pageName = pageName;
        }

        public String getRoutePath() {
            return routePath;
        }

        public void setRoutePath(String routePath) {
            this.routePath = routePath;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getPermissionGroup() {
            return permissionGroup;
        }

        public void setPermissionGroup(String permissionGroup) {
            this.permissionGroup = permissionGroup;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public boolean isSelectable() {
            return selectable;
        }

        public void setSelectable(boolean selectable) {
            this.selectable = selectable;
        }

        public List<PermissionTreeVO> getChildren() {
            return children;
        }

        public void setChildren(List<PermissionTreeVO> children) {
            this.children = children;
        }

        public List<PermissionActionVO> getActionPermissions() {
            return actionPermissions;
        }

        public void setActionPermissions(List<PermissionActionVO> actionPermissions) {
            this.actionPermissions = actionPermissions;
        }
    }

    public static class PermissionActionVO {
        private String permissionKey;
        private String permissionName;
        private String permissionGroup;
        private String sourceType;

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getPermissionName() {
            return permissionName;
        }

        public void setPermissionName(String permissionName) {
            this.permissionName = permissionName;
        }

        public String getPermissionGroup() {
            return permissionGroup;
        }

        public void setPermissionGroup(String permissionGroup) {
            this.permissionGroup = permissionGroup;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }
    }

    public static class MenuVO {
        private Long id;
        private Long tenantId;
        private Long parentId;
        private String menuCode;
        private String menuName;
        private String menuType;
        private String path;
        private String component;
        private String icon;
        private Integer sortNo;
        private String permissionKey;
        private String status;
        private List<MenuVO> children;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public String getMenuCode() {
            return menuCode;
        }

        public void setMenuCode(String menuCode) {
            this.menuCode = menuCode;
        }

        public String getMenuName() {
            return menuName;
        }

        public void setMenuName(String menuName) {
            this.menuName = menuName;
        }

        public String getMenuType() {
            return menuType;
        }

        public void setMenuType(String menuType) {
            this.menuType = menuType;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public String getPermissionKey() {
            return permissionKey;
        }

        public void setPermissionKey(String permissionKey) {
            this.permissionKey = permissionKey;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<MenuVO> getChildren() {
            return children;
        }

        public void setChildren(List<MenuVO> children) {
            this.children = children;
        }
    }

    public static class DictTypeVO {
        private Long id;
        private Long tenantId;
        private String dictCode;
        private String dictName;
        private String status;
        private Integer isSystem;
        private String remark;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getDictCode() {
            return dictCode;
        }

        public void setDictCode(String dictCode) {
            this.dictCode = dictCode;
        }

        public String getDictName() {
            return dictName;
        }

        public void setDictName(String dictName) {
            this.dictName = dictName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getIsSystem() {
            return isSystem;
        }

        public void setIsSystem(Integer isSystem) {
            this.isSystem = isSystem;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class DictItemVO {
        private Long id;
        private Long dictTypeId;
        private String itemLabel;
        private String itemValue;
        private Integer sortNo;
        private String status;
        private String remark;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getDictTypeId() {
            return dictTypeId;
        }

        public void setDictTypeId(Long dictTypeId) {
            this.dictTypeId = dictTypeId;
        }

        public String getItemLabel() {
            return itemLabel;
        }

        public void setItemLabel(String itemLabel) {
            this.itemLabel = itemLabel;
        }

        public String getItemValue() {
            return itemValue;
        }

        public void setItemValue(String itemValue) {
            this.itemValue = itemValue;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class ConfigVO {
        private Long id;
        private Long tenantId;
        private String configKey;
        private String configName;
        private String configValue;
        private String configScope;
        private Integer isSystem;
        private String remark;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getConfigKey() {
            return configKey;
        }

        public void setConfigKey(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigName() {
            return configName;
        }

        public void setConfigName(String configName) {
            this.configName = configName;
        }

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }

        public String getConfigScope() {
            return configScope;
        }

        public void setConfigScope(String configScope) {
            this.configScope = configScope;
        }

        public Integer getIsSystem() {
            return isSystem;
        }

        public void setIsSystem(Integer isSystem) {
            this.isSystem = isSystem;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class SmtpSettingsVO {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String from;
        private Boolean authEnabled;
        private Boolean startTlsEnabled;
        private Boolean sslEnabled;
        private Boolean configured;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public Boolean getAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(Boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public Boolean getStartTlsEnabled() {
            return startTlsEnabled;
        }

        public void setStartTlsEnabled(Boolean startTlsEnabled) {
            this.startTlsEnabled = startTlsEnabled;
        }

        public Boolean getSslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(Boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }

        public Boolean getConfigured() {
            return configured;
        }

        public void setConfigured(Boolean configured) {
            this.configured = configured;
        }
    }

    public static class SmtpTestVO {
        private Boolean success;
        private String message;
        private String toEmail;

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getToEmail() {
            return toEmail;
        }

        public void setToEmail(String toEmail) {
            this.toEmail = toEmail;
        }
    }

    public static class SecuritySettingsVO {
        private Long idleTimeoutSeconds;
        private Long accessTokenExpireSeconds;
        private Long refreshTokenExpireSeconds;
        private Boolean allowMultiDeviceLogin;
        private Boolean captchaEnabled;
        private String captchaType;
        private Long loginDefenseWindowMinutes;
        private Long loginMaxValidationAttempts;
        private Long loginMaxFailureCount;
        private Long passwordMinLength;
        private Boolean passwordRequireUppercase;
        private Boolean passwordRequireLowercase;
        private Boolean passwordRequireSpecialCharacter;
        private Boolean passwordAllowConsecutiveCharacters;

        public Long getIdleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        public void setIdleTimeoutSeconds(Long idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
        }

        public Long getAccessTokenExpireSeconds() {
            return accessTokenExpireSeconds;
        }

        public void setAccessTokenExpireSeconds(Long accessTokenExpireSeconds) {
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
        }

        public Long getRefreshTokenExpireSeconds() {
            return refreshTokenExpireSeconds;
        }

        public void setRefreshTokenExpireSeconds(Long refreshTokenExpireSeconds) {
            this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
        }

        public Boolean getAllowMultiDeviceLogin() {
            return allowMultiDeviceLogin;
        }

        public void setAllowMultiDeviceLogin(Boolean allowMultiDeviceLogin) {
            this.allowMultiDeviceLogin = allowMultiDeviceLogin;
        }

        public Boolean getCaptchaEnabled() {
            return captchaEnabled;
        }

        public void setCaptchaEnabled(Boolean captchaEnabled) {
            this.captchaEnabled = captchaEnabled;
        }

        public String getCaptchaType() {
            return captchaType;
        }

        public void setCaptchaType(String captchaType) {
            this.captchaType = captchaType;
        }

        public Long getLoginDefenseWindowMinutes() {
            return loginDefenseWindowMinutes;
        }

        public void setLoginDefenseWindowMinutes(Long loginDefenseWindowMinutes) {
            this.loginDefenseWindowMinutes = loginDefenseWindowMinutes;
        }

        public Long getLoginMaxValidationAttempts() {
            return loginMaxValidationAttempts;
        }

        public void setLoginMaxValidationAttempts(Long loginMaxValidationAttempts) {
            this.loginMaxValidationAttempts = loginMaxValidationAttempts;
        }

        public Long getLoginMaxFailureCount() {
            return loginMaxFailureCount;
        }

        public void setLoginMaxFailureCount(Long loginMaxFailureCount) {
            this.loginMaxFailureCount = loginMaxFailureCount;
        }

        public Long getPasswordMinLength() {
            return passwordMinLength;
        }

        public void setPasswordMinLength(Long passwordMinLength) {
            this.passwordMinLength = passwordMinLength;
        }

        public Boolean getPasswordRequireUppercase() {
            return passwordRequireUppercase;
        }

        public void setPasswordRequireUppercase(Boolean passwordRequireUppercase) {
            this.passwordRequireUppercase = passwordRequireUppercase;
        }

        public Boolean getPasswordRequireLowercase() {
            return passwordRequireLowercase;
        }

        public void setPasswordRequireLowercase(Boolean passwordRequireLowercase) {
            this.passwordRequireLowercase = passwordRequireLowercase;
        }

        public Boolean getPasswordRequireSpecialCharacter() {
            return passwordRequireSpecialCharacter;
        }

        public void setPasswordRequireSpecialCharacter(Boolean passwordRequireSpecialCharacter) {
            this.passwordRequireSpecialCharacter = passwordRequireSpecialCharacter;
        }

        public Boolean getPasswordAllowConsecutiveCharacters() {
            return passwordAllowConsecutiveCharacters;
        }

        public void setPasswordAllowConsecutiveCharacters(Boolean passwordAllowConsecutiveCharacters) {
            this.passwordAllowConsecutiveCharacters = passwordAllowConsecutiveCharacters;
        }
    }

    public static class CaptchaChallengeVO {
        private String captchaId;
        private String captchaType;
        private String imageUrl;
        private String bgUrl;
        private String puzzleUrl;
        private Integer bgWidth;
        private Integer bgHeight;
        private Integer puzzleWidth;
        private Integer puzzleHeight;
        private Integer puzzleLeft;
        private Integer puzzleTop;
        private Integer expiresInSeconds;

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptchaType() {
            return captchaType;
        }

        public void setCaptchaType(String captchaType) {
            this.captchaType = captchaType;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getBgUrl() {
            return bgUrl;
        }

        public void setBgUrl(String bgUrl) {
            this.bgUrl = bgUrl;
        }

        public String getPuzzleUrl() {
            return puzzleUrl;
        }

        public void setPuzzleUrl(String puzzleUrl) {
            this.puzzleUrl = puzzleUrl;
        }

        public Integer getBgWidth() {
            return bgWidth;
        }

        public void setBgWidth(Integer bgWidth) {
            this.bgWidth = bgWidth;
        }

        public Integer getBgHeight() {
            return bgHeight;
        }

        public void setBgHeight(Integer bgHeight) {
            this.bgHeight = bgHeight;
        }

        public Integer getPuzzleWidth() {
            return puzzleWidth;
        }

        public void setPuzzleWidth(Integer puzzleWidth) {
            this.puzzleWidth = puzzleWidth;
        }

        public Integer getPuzzleHeight() {
            return puzzleHeight;
        }

        public void setPuzzleHeight(Integer puzzleHeight) {
            this.puzzleHeight = puzzleHeight;
        }

        public Integer getPuzzleLeft() {
            return puzzleLeft;
        }

        public void setPuzzleLeft(Integer puzzleLeft) {
            this.puzzleLeft = puzzleLeft;
        }

        public Integer getPuzzleTop() {
            return puzzleTop;
        }

        public void setPuzzleTop(Integer puzzleTop) {
            this.puzzleTop = puzzleTop;
        }

        public Integer getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(Integer expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static class CaptchaVerifyVO {
        private String captchaId;
        private String captchaProof;
        private Integer expiresInSeconds;

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptchaProof() {
            return captchaProof;
        }

        public void setCaptchaProof(String captchaProof) {
            this.captchaProof = captchaProof;
        }

        public Integer getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(Integer expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static class BrandingSettingsVO {
        private String websiteName;
        private String websiteFaviconUrl;
        private String websiteLogoUrl;
        private String githubLinkUrl;
        private String helpLinkUrl;
        private String companyName;
        private Integer copyrightStartYear;
        private String footerIcp;
        private String footerCopyright;

        public String getWebsiteName() {
            return websiteName;
        }

        public void setWebsiteName(String websiteName) {
            this.websiteName = websiteName;
        }

        public String getWebsiteFaviconUrl() {
            return websiteFaviconUrl;
        }

        public void setWebsiteFaviconUrl(String websiteFaviconUrl) {
            this.websiteFaviconUrl = websiteFaviconUrl;
        }

        public String getWebsiteLogoUrl() {
            return websiteLogoUrl;
        }

        public void setWebsiteLogoUrl(String websiteLogoUrl) {
            this.websiteLogoUrl = websiteLogoUrl;
        }

        public String getGithubLinkUrl() {
            return githubLinkUrl;
        }

        public void setGithubLinkUrl(String githubLinkUrl) {
            this.githubLinkUrl = githubLinkUrl;
        }

        public String getHelpLinkUrl() {
            return helpLinkUrl;
        }

        public void setHelpLinkUrl(String helpLinkUrl) {
            this.helpLinkUrl = helpLinkUrl;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public Integer getCopyrightStartYear() {
            return copyrightStartYear;
        }

        public void setCopyrightStartYear(Integer copyrightStartYear) {
            this.copyrightStartYear = copyrightStartYear;
        }

        public String getFooterIcp() {
            return footerIcp;
        }

        public void setFooterIcp(String footerIcp) {
            this.footerIcp = footerIcp;
        }

        public String getFooterCopyright() {
            return footerCopyright;
        }

        public void setFooterCopyright(String footerCopyright) {
            this.footerCopyright = footerCopyright;
        }
    }

    public static class AgreementSettingsVO {
        private String userAgreementMarkdown;
        private String privacyAgreementMarkdown;

        public String getUserAgreementMarkdown() {
            return userAgreementMarkdown;
        }

        public void setUserAgreementMarkdown(String userAgreementMarkdown) {
            this.userAgreementMarkdown = userAgreementMarkdown;
        }

        public String getPrivacyAgreementMarkdown() {
            return privacyAgreementMarkdown;
        }

        public void setPrivacyAgreementMarkdown(String privacyAgreementMarkdown) {
            this.privacyAgreementMarkdown = privacyAgreementMarkdown;
        }
    }

    public static class AuditLogVO {
        private Long id;
        private Long tenantId;
        private Long userId;
        private String username;
        private String logType;
        private String logResult;
        private String moduleName;
        private String actionName;
        private String operationType;
        private String detailMessage;
        private String failReason;
        private String requestId;
        private String traceId;
        private String loginIp;
        private String userAgent;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

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

        public String getLogType() {
            return logType;
        }

        public void setLogType(String logType) {
            this.logType = logType;
        }

        public String getLogResult() {
            return logResult;
        }

        public void setLogResult(String logResult) {
            this.logResult = logResult;
        }

        public String getModuleName() {
            return moduleName;
        }

        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }

        public String getActionName() {
            return actionName;
        }

        public void setActionName(String actionName) {
            this.actionName = actionName;
        }

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public String getDetailMessage() {
            return detailMessage;
        }

        public void setDetailMessage(String detailMessage) {
            this.detailMessage = detailMessage;
        }

        public String getFailReason() {
            return failReason;
        }

        public void setFailReason(String failReason) {
            this.failReason = failReason;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getLoginIp() {
            return loginIp;
        }

        public void setLoginIp(String loginIp) {
            this.loginIp = loginIp;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
    public static class WatermarkSettingsVO {
        private Boolean enabled;
        private String mode;
        private List<String> textLines;
        private String imageUrl;
        private String fontColor;
        private Integer fontSize;
        private String fontWeight;
        private Integer rotate;
        private Integer gapX;
        private Integer gapY;
        private Integer offsetX;
        private Integer offsetY;
        private Integer zIndex;
        private Double opacity;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public List<String> getTextLines() { return textLines; }
        public void setTextLines(List<String> textLines) { this.textLines = textLines; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getFontColor() { return fontColor; }
        public void setFontColor(String fontColor) { this.fontColor = fontColor; }
        public Integer getFontSize() { return fontSize; }
        public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
        public String getFontWeight() { return fontWeight; }
        public void setFontWeight(String fontWeight) { this.fontWeight = fontWeight; }
        public Integer getRotate() { return rotate; }
        public void setRotate(Integer rotate) { this.rotate = rotate; }
        public Integer getGapX() { return gapX; }
        public void setGapX(Integer gapX) { this.gapX = gapX; }
        public Integer getGapY() { return gapY; }
        public void setGapY(Integer gapY) { this.gapY = gapY; }
        public Integer getOffsetX() { return offsetX; }
        public void setOffsetX(Integer offsetX) { this.offsetX = offsetX; }
        public Integer getOffsetY() { return offsetY; }
        public void setOffsetY(Integer offsetY) { this.offsetY = offsetY; }
        public Integer getZIndex() { return zIndex; }
        public void setZIndex(Integer zIndex) { this.zIndex = zIndex; }
        public Double getOpacity() { return opacity; }
        public void setOpacity(Double opacity) { this.opacity = opacity; }
    }

}
