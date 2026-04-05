package com.yourcompany.saas.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class SystemDTO {

    private SystemDTO() {
    }

    public static class UserUpsertRequest {
        @NotBlank
        private String username;
        private String password;
        private String mobile;
        private String nickname;
        private String realName;
        private String avatarUrl;
        private String email;
        @NotBlank
        private String status;
        private List<Long> roleIds;

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

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
        }
    }

    public static class UserStatusRequest {
        @NotBlank
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class RoleUpsertRequest {
        @NotBlank
        private String roleCode;
        @NotBlank
        private String roleName;
        @NotBlank
        private String roleType;
        private List<String> permissionKeys;

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

        public List<String> getPermissionKeys() {
            return permissionKeys;
        }

        public void setPermissionKeys(List<String> permissionKeys) {
            this.permissionKeys = permissionKeys;
        }
    }

    public static class RolePermissionRequest {
        private List<String> permissionKeys;

        public List<String> getPermissionKeys() {
            return permissionKeys;
        }

        public void setPermissionKeys(List<String> permissionKeys) {
            this.permissionKeys = permissionKeys;
        }
    }

    public static class MenuUpsertRequest {
        private Long parentId;
        @NotBlank
        private String menuCode;
        @NotBlank
        private String menuName;
        @NotBlank
        private String menuType;
        private String path;
        private String component;
        private String icon;
        private Integer sortNo;
        private String permissionKey;
        @NotBlank
        private String status;

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
    }

    public static class MenuStatusRequest {
        @NotBlank
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class DictTypeUpsertRequest {
        @NotBlank
        private String dictCode;
        @NotBlank
        private String dictName;
        @NotBlank
        private String status;
        private String remark;

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

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class DictItemUpsertRequest {
        @NotBlank
        private String itemLabel;
        @NotBlank
        private String itemValue;
        @NotNull
        private Integer sortNo;
        @NotBlank
        private String status;
        private String remark;

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

    public static class ConfigUpsertRequest {
        @NotBlank
        private String configKey;
        @NotBlank
        private String configName;
        @NotBlank
        private String configValue;
        @NotBlank
        private String configScope;
        private String remark;

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

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class SecuritySettingsRequest {
        @NotNull
        @Positive
        private Long idleTimeoutSeconds;

        @NotNull
        @Positive
        private Long accessTokenExpireSeconds;

        @NotNull
        @Positive
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

    public static class BrandingSettingsRequest {
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
    public static class WatermarkSettingsRequest {
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
