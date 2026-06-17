package com.lumira.saas.modules.system.vo;

import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.permission.vo.PermissionActionVO;
import com.lumira.saas.modules.system.permission.vo.PermissionVO;
import com.lumira.saas.modules.system.session.vo.OnlineSessionVO;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemVO {

    private SystemVO() {
    }

    public static class ShortcutVO extends com.lumira.saas.modules.system.dashboard.vo.ShortcutVO {
    }

    public static class DashboardSummaryVO extends com.lumira.saas.modules.system.dashboard.vo.DashboardSummaryVO {
    }

    public static class ProfileSummaryVO extends com.lumira.saas.modules.system.profile.vo.ProfileSummaryVO {
    }

    public static class ProfileFieldSettingVO extends com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO {
    }

    public static class ProfileCompletionSummaryVO extends com.lumira.saas.modules.system.profile.vo.ProfileCompletionSummaryVO {
    }

    public static class ProfileCompletionGroupVO extends com.lumira.saas.modules.system.profile.vo.ProfileCompletionGroupVO {
    }

    public static class ProfileCompletionItemVO extends com.lumira.saas.modules.system.profile.vo.ProfileCompletionItemVO {
    }

    public static class VerificationProviderVO extends com.lumira.saas.modules.system.verification.vo.VerificationProviderVO {
    }

    public static class VerificationChallengeVO extends com.lumira.saas.modules.system.verification.vo.VerificationChallengeVO {
    }

    public static class VerificationVerificationVO extends com.lumira.saas.modules.system.verification.vo.VerificationVerificationVO {
    }

    public static class UserVO extends com.lumira.saas.modules.system.user.vo.UserVO {
    }

    public static class UserDetailVO extends com.lumira.saas.modules.system.user.vo.UserDetailVO {
    }

    public static class OnlineSessionVO extends com.lumira.saas.modules.system.session.vo.OnlineSessionVO {
    }

    public static class RoleVO extends com.lumira.saas.modules.system.role.vo.RoleVO {
    }

    public static class RoleDetailVO extends com.lumira.saas.modules.system.role.vo.RoleDetailVO {
    }

    public static class DefaultRegistrationRoleVO extends RoleDetailVO {
    }

    public static class PermissionVO extends com.lumira.saas.modules.system.permission.vo.PermissionVO {
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

    public static class PermissionActionVO extends com.lumira.saas.modules.system.permission.vo.PermissionActionVO {
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
        private boolean builtin;
        private List<MenuVO> children;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getMenuCode() { return menuCode; }
        public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
        public String getMenuName() { return menuName; }
        public void setMenuName(String menuName) { this.menuName = menuName; }
        public String getMenuType() { return menuType; }
        public void setMenuType(String menuType) { this.menuType = menuType; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        public String getPermissionKey() { return permissionKey; }
        public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isBuiltin() { return builtin; }
        public void setBuiltin(boolean builtin) { this.builtin = builtin; }
        public List<MenuVO> getChildren() { return children; }
        public void setChildren(List<MenuVO> children) { this.children = children; }
    }

    public static class DictTypeVO extends com.lumira.saas.modules.system.dict.vo.DictTypeVO {
    }

    public static class DictItemVO extends com.lumira.saas.modules.system.dict.vo.DictItemVO {
    }

    public static class ConfigVO extends com.lumira.saas.modules.system.config.vo.ConfigVO {
    }

    public static class SmtpSettingsVO extends com.lumira.saas.modules.system.smtp.vo.SmtpSettingsVO {
    }

    public static class WechatOfficialAccountSettingsVO extends com.lumira.saas.modules.system.notification.vo.WechatOfficialAccountSettingsVO {
    }

    public static class SmsVerificationSettingsVO extends com.lumira.saas.modules.system.verification.vo.SmsVerificationSettingsVO {
    }

    public static class VerificationSettingsVO extends com.lumira.saas.modules.system.verification.vo.VerificationSettingsVO {
    }

    public static class LoginCapabilitiesVO extends com.lumira.saas.modules.system.verification.vo.LoginCapabilitiesVO {
    }

    public static class PublicBootstrapVO {
        private BrandingSettingsVO brandingSettings;
        private SecuritySettingsVO securitySettings;
        private AgreementSettingsVO agreementSettings;
        private LoginCapabilitiesVO loginCapabilities;

        public BrandingSettingsVO getBrandingSettings() {
            return brandingSettings;
        }

        public void setBrandingSettings(BrandingSettingsVO brandingSettings) {
            this.brandingSettings = brandingSettings;
        }

        public SecuritySettingsVO getSecuritySettings() {
            return securitySettings;
        }

        public void setSecuritySettings(SecuritySettingsVO securitySettings) {
            this.securitySettings = securitySettings;
        }

        public AgreementSettingsVO getAgreementSettings() {
            return agreementSettings;
        }

        public void setAgreementSettings(AgreementSettingsVO agreementSettings) {
            this.agreementSettings = agreementSettings;
        }

        public LoginCapabilitiesVO getLoginCapabilities() {
            return loginCapabilities;
        }

        public void setLoginCapabilities(LoginCapabilitiesVO loginCapabilities) {
            this.loginCapabilities = loginCapabilities;
        }
    }

    public static class WechatLoginSettingsVO extends com.lumira.saas.modules.system.verification.vo.WechatLoginSettingsVO {
    }

    public static class PasskeySettingsVO extends com.lumira.saas.modules.system.verification.vo.PasskeySettingsVO {
    }

    public static class LoginCodeChallengeVO extends com.lumira.saas.modules.system.verification.vo.LoginCodeChallengeVO {
    }

    public static class SmtpTestVO extends com.lumira.saas.modules.system.smtp.vo.SmtpTestVO {
    }

    public static class SecuritySettingsVO extends com.lumira.saas.modules.system.security.vo.SecuritySettingsVO {
    }

    public static class CaptchaChallengeVO extends com.lumira.saas.modules.system.captcha.vo.CaptchaChallengeVO {
    }

    public static class CaptchaVerifyVO extends com.lumira.saas.modules.system.captcha.vo.CaptchaVerifyVO {
    }

    public static class BrandingSettingsVO extends com.lumira.saas.modules.system.branding.vo.BrandingSettingsVO {
    }

    public static class AgreementSettingsVO extends com.lumira.saas.modules.system.agreement.vo.AgreementSettingsVO {
    }

    public static class AuditLogVO extends com.lumira.saas.modules.system.audit.vo.AuditLogVO {
    }
    public static class WatermarkSettingsVO extends com.lumira.saas.modules.system.watermark.vo.WatermarkSettingsVO {
    }

    public static class FloatingWindowSettingsVO extends com.lumira.saas.modules.system.floating.vo.FloatingWindowSettingsVO {
    }

    public static class RuntimeAppearanceSettingsVO {
        private BrandingSettingsVO brandingSettings;
        private WatermarkSettingsVO watermarkSettings;
        private FloatingWindowSettingsVO floatingWindowSettings;

        public BrandingSettingsVO getBrandingSettings() {
            return brandingSettings;
        }

        public void setBrandingSettings(BrandingSettingsVO brandingSettings) {
            this.brandingSettings = brandingSettings;
        }

        public WatermarkSettingsVO getWatermarkSettings() {
            return watermarkSettings;
        }

        public void setWatermarkSettings(WatermarkSettingsVO watermarkSettings) {
            this.watermarkSettings = watermarkSettings;
        }

        public FloatingWindowSettingsVO getFloatingWindowSettings() {
            return floatingWindowSettings;
        }

        public void setFloatingWindowSettings(FloatingWindowSettingsVO floatingWindowSettings) {
            this.floatingWindowSettings = floatingWindowSettings;
        }
    }

}
