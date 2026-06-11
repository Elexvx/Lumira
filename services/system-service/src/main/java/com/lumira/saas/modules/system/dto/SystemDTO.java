package com.lumira.saas.modules.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SystemDTO {

    private SystemDTO() {
    }

    public static class UserUpsertRequest extends com.lumira.saas.modules.system.user.dto.UserUpsertRequest {
    }

    public static class UserStatusRequest extends com.lumira.saas.modules.system.user.dto.UserStatusRequest {
    }

    public static class RoleUpsertRequest extends com.lumira.saas.modules.system.role.dto.RoleUpsertRequest {
    }

    public static class RolePermissionRequest extends com.lumira.saas.modules.system.role.dto.RolePermissionRequest {
    }

    public static class DefaultRegistrationRoleRequest {
        @NotNull
        private Long roleId;

        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
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
    }

    public static class MenuStatusRequest {
        @NotBlank
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class MenuReorderRequest {
        @NotEmpty
        @Valid
        private List<MenuOrderItem> items;

        public List<MenuOrderItem> getItems() { return items; }
        public void setItems(List<MenuOrderItem> items) { this.items = items; }
    }

    public static class MenuOrderItem {
        @NotNull
        private Long id;
        private Long parentId;
        @NotNull
        private Integer sortNo;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    }

    public static class DictTypeUpsertRequest extends com.lumira.saas.modules.system.dict.dto.DictTypeUpsertRequest {
    }

    public static class DictItemUpsertRequest extends com.lumira.saas.modules.system.dict.dto.DictItemUpsertRequest {
    }

    public static class ConfigUpsertRequest extends com.lumira.saas.modules.system.config.dto.ConfigUpsertRequest {
    }

    public static class ProfileFieldSettingsRequest extends com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingsRequest {
    }

    public static class ProfileFieldSettingItem extends com.lumira.saas.modules.system.profile.dto.ProfileFieldSettingItem {
    }

    public static class SecuritySettingsRequest extends com.lumira.saas.modules.system.security.dto.SecuritySettingsRequest {
    }

    public static class CaptchaSliderVerifyRequest extends com.lumira.saas.modules.system.captcha.dto.CaptchaSliderVerifyRequest {
    }

    public static class BrandingSettingsRequest extends com.lumira.saas.modules.system.branding.dto.BrandingSettingsRequest {
    }

    public static class AgreementSettingsRequest extends com.lumira.saas.modules.system.agreement.dto.AgreementSettingsRequest {
    }

    public static class SmtpSettingsRequest extends com.lumira.saas.modules.system.smtp.dto.SmtpSettingsRequest {
    }

    public static class WechatOfficialAccountSettingsRequest extends com.lumira.saas.modules.system.notification.dto.WechatOfficialAccountSettingsRequest {
    }

    public static class SmsVerificationSettingsRequest extends com.lumira.saas.modules.system.verification.dto.SmsVerificationSettingsRequest {
    }

    public static class VerificationSettingsRequest extends com.lumira.saas.modules.system.verification.dto.VerificationSettingsRequest {
    }

    public static class WechatLoginSettingsRequest extends com.lumira.saas.modules.system.verification.dto.WechatLoginSettingsRequest {
    }

    public static class PasskeySettingsRequest extends com.lumira.saas.modules.system.verification.dto.PasskeySettingsRequest {
    }

    public static class SmtpTestRequest extends com.lumira.saas.modules.system.smtp.dto.SmtpTestRequest {
    }

    public static class WatermarkSettingsRequest extends com.lumira.saas.modules.system.watermark.dto.WatermarkSettingsRequest {
    }

    public static class FloatingWindowSettingsRequest extends com.lumira.saas.modules.system.floating.dto.FloatingWindowSettingsRequest {
    }

}
