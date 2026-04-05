package com.yourcompany.saas.modules.system.vo;

import com.yourcompany.saas.modules.auth.vo.CurrentUserVO;
import com.yourcompany.saas.modules.plugin.vo.PluginVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;

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
    }

    public static class UserVO {
        private Long id;
        private String username;
        private String mobile;
        private String nickname;
        private String realName;
        private String avatarUrl;
        private String email;
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

    public static class SecuritySettingsVO {
        private Long idleTimeoutSeconds;
        private Long accessTokenExpireSeconds;
        private Long refreshTokenExpireSeconds;

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
    }

    public static class BrandingSettingsVO {
        private String websiteName;
        private String websiteFaviconUrl;
        private String websiteLogoUrl;
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
