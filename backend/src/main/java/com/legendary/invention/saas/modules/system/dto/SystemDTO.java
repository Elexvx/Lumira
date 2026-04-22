package com.legendary.invention.saas.modules.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class SystemDTO {

    private SystemDTO() {
    }

    public static class UserUpsertRequest {
        @NotBlank
        private String username;
        private String password;
        @Pattern(regexp = "^(?:$|1[3-9]\\d{9})$", message = "请输入有效手机号")
        private String mobile;
        private String nickname;
        private String realName;
        private String avatarUrl;
        private String email;
        private String birthMonth;
        private String gender;
        private String region;
        private String availableTime;
        @Pattern(regexp = "^(?:$|\\d{15}|\\d{17}[\\dXx])$", message = "请输入有效身份证号码")
        private String idCardNumber;
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
            this.mobile = mobile == null ? null : mobile.trim();
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
            this.avatarUrl = avatarUrl == null ? null : avatarUrl.trim();
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email == null ? null : email.trim();
        }

        public String getBirthMonth() {
            return birthMonth;
        }

        public void setBirthMonth(String birthMonth) {
            this.birthMonth = birthMonth == null ? null : birthMonth.trim();
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender == null ? null : gender.trim();
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region == null ? null : region.trim();
        }

        public String getAvailableTime() {
            return availableTime;
        }

        public void setAvailableTime(String availableTime) {
            this.availableTime = availableTime == null ? null : availableTime.trim();
        }

        public String getIdCardNumber() {
            return idCardNumber;
        }

        public void setIdCardNumber(String idCardNumber) {
            this.idCardNumber = idCardNumber == null ? null : idCardNumber.trim();
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

    public static class MenuReorderRequest {
        @NotEmpty
        @Valid
        private List<MenuOrderItem> items;

        public List<MenuOrderItem> getItems() {
            return items;
        }

        public void setItems(List<MenuOrderItem> items) {
            this.items = items;
        }
    }

    public static class MenuOrderItem {
        @NotNull
        private Long id;
        private Long parentId;
        @NotNull
        private Integer sortNo;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
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

    public static class ProfileFieldSettingsRequest {
        @Valid
        @NotEmpty
        private List<ProfileFieldSettingItem> items;

        public List<ProfileFieldSettingItem> getItems() {
            return items;
        }

        public void setItems(List<ProfileFieldSettingItem> items) {
            this.items = items;
        }
    }

    public static class ProfileFieldSettingItem {
        @NotBlank
        private String fieldKey;
        @NotNull
        private Boolean visible;

        public String getFieldKey() {
            return fieldKey;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        public Boolean getVisible() {
            return visible;
        }

        public void setVisible(Boolean visible) {
            this.visible = visible;
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

        @NotNull
        private Boolean allowMultiDeviceLogin;

        @NotNull
        private Boolean captchaEnabled;

        @NotBlank
        private String captchaType;

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
    }

    public static class CaptchaSliderVerifyRequest {
        @NotBlank
        private String captchaId;
        @NotNull
        private Double x;
        @NotNull
        private Double y;
        @NotNull
        private Double sliderOffsetX;
        @NotNull
        @Positive
        private Long duration;
        private List<List<Double>> trail;
        private String targetType;
        private Integer errorCount;

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public Double getX() {
            return x;
        }

        public void setX(Double x) {
            this.x = x;
        }

        public Double getY() {
            return y;
        }

        public void setY(Double y) {
            this.y = y;
        }

        public Double getSliderOffsetX() {
            return sliderOffsetX;
        }

        public void setSliderOffsetX(Double sliderOffsetX) {
            this.sliderOffsetX = sliderOffsetX;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public List<List<Double>> getTrail() {
            return trail;
        }

        public void setTrail(List<List<Double>> trail) {
            this.trail = trail;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public Integer getErrorCount() {
            return errorCount;
        }

        public void setErrorCount(Integer errorCount) {
            this.errorCount = errorCount;
        }
    }

    public static class BrandingSettingsRequest {
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

    public static class AgreementSettingsRequest {
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

    public static class SmtpSettingsRequest {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String from;
        private Boolean authEnabled;
        private Boolean startTlsEnabled;
        private Boolean sslEnabled;

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
    }

    public static class SmsVerificationSettingsRequest {
        private Boolean enabled;
        private String provider;
        private String signName;
        private String templateCode;
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint;
        private String region;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class SmtpTestRequest {
        @NotBlank
        private String toEmail;
        private String subject;
        private String content;

        public String getToEmail() {
            return toEmail;
        }

        public void setToEmail(String toEmail) {
            this.toEmail = toEmail;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
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
