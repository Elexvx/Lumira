package com.lumira.api.ai;

import com.lumira.common.security.CurrentUser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed System-management capability surface used by AI native tools.
 *
 * <p>This is deliberately not a proxy for SystemManagementAppService.  The action enum and
 * per-action field allow-list are the complete contract available to AI; System keeps its DTOs,
 * VOs, permission checks, and aggregate rules on the implementation side.</p>
 */
public interface AiSystemManagementToolPort {

    /** Execute one of the explicitly supported AI native-management actions. */
    Map<String, Object> execute(CurrentUser actor, Action action, Map<String, Object> arguments);

    /** Returns only the config key needed to enforce AI's configuration-scope policy. */
    String findConfigKeyForAiUpdate(CurrentUser actor, Long configId);

    enum Action {
        CREATE_USER("system:user:create", fields(
                "username", "password", "mobile", "nickname", "realName", "avatarUrl", "email",
                "birthMonth", "gender", "region", "availableTime", "idCardNumber", "status",
                "roleIds", "deptIds", "primaryDeptId")),
        UPDATE_USER("system:user:update", fields(
                "userId", "userUuid", "username", "mobile", "nickname", "realName", "avatarUrl",
                "email", "birthMonth", "gender", "region", "availableTime", "idCardNumber", "status",
                "roleIds", "deptIds", "primaryDeptId")),
        UPDATE_USER_STATUS("system:user:status", fields("userId", "userUuid", "status")),
        DELETE_USER("system:user:delete", fields("userId", "userUuid")),
        UPDATE_CURRENT_AVATAR("profile:view", fields("avatarUrl")),
        CREATE_ROLE("system:role:create", fields(
                "roleCode", "roleName", "roleType", "defaultHomePath", "permissionKeys", "dataScopes")),
        UPDATE_ROLE("system:role:update", fields(
                "roleId", "roleCode", "roleName", "roleType", "defaultHomePath", "permissionKeys", "dataScopes")),
        UPDATE_ROLE_PERMISSIONS("system:role:grant", fields("roleId", "permissionKeys")),
        DELETE_ROLE("system:role:delete", fields("roleId")),
        CREATE_MENU("system:menu:create", fields(
                "parentId", "menuCode", "menuName", "menuType", "path", "component", "icon", "sortNo",
                "permissionKey", "status")),
        UPDATE_MENU("system:menu:update", fields(
                "menuId", "parentId", "menuCode", "menuName", "menuType", "path", "component", "icon",
                "sortNo", "permissionKey", "status")),
        UPDATE_MENU_STATUS("system:menu:status", fields("menuId", "status")),
        DELETE_MENU("system:menu:delete", fields("menuId")),
        CREATE_DICT_TYPE("system:dict:create", fields("dictCode", "dictName", "status", "remark")),
        UPDATE_DICT_TYPE("system:dict:update", fields("dictTypeId", "dictCode", "dictName", "status", "remark")),
        DELETE_DICT_TYPE("system:dict:delete", fields("dictTypeId")),
        CREATE_DICT_ITEM("system:dict:create", fields(
                "dictTypeId", "itemLabel", "itemValue", "sortNo", "status", "remark")),
        UPDATE_DICT_ITEM("system:dict:update", fields(
                "dictTypeId", "itemId", "itemLabel", "itemValue", "sortNo", "status", "remark")),
        DELETE_DICT_ITEM("system:dict:delete", fields("dictTypeId", "itemId")),
        CREATE_CONFIG("system:config:update", fields(
                "configKey", "configName", "configValue", "remark", "expectedConfigVersion", "changeReason")),
        UPDATE_CONFIG("system:config:update", fields(
                "configId", "configKey", "configName", "configValue", "remark", "expectedConfigVersion", "changeReason")),
        UPDATE_BRANDING("system:config:update", fields(
                "websiteName", "websiteFaviconUrl", "websiteLogoUrl", "loginBackgroundUrl", "githubLinkEnabled",
                "githubLinkUrl", "helpLinkEnabled", "helpLinkUrl", "companyName", "copyrightStartYear",
                "footerIcp", "footerPoliceBeian", "footerCopyright", "maintenanceModeEnabled", "maintenanceTitle",
                "maintenanceMessage", "maintenanceEndAt", "expectedConfigVersion", "changeReason")),
        UPDATE_AGREEMENT("system:config:update", fields(
                "userAgreementMarkdown", "privacyAgreementMarkdown", "expectedConfigVersion", "changeReason")),
        UPDATE_WATERMARK("system:config:update", fields(
                "enabled", "mode", "textLines", "imageUrl", "fontColor", "fontSize", "fontWeight", "rotate",
                "gapX", "gapY", "offsetX", "offsetY", "zIndex", "opacity", "expectedConfigVersion", "changeReason")),
        UPDATE_FLOATING_WINDOW("system:config:update", fields(
                "apiDocsQrEnabled", "apiDocsQrTitle", "apiDocsQrImageUrl", "expectedConfigVersion", "changeReason"));

        private final String requiredPermission;
        private final Set<String> allowedFields;

        Action(String requiredPermission, Set<String> allowedFields) {
            this.requiredPermission = requiredPermission;
            this.allowedFields = allowedFields;
        }

        public String requiredPermission() {
            return requiredPermission;
        }

        public Set<String> allowedFields() {
            return allowedFields;
        }

        /** Drops transport-only or unknown fields before the request crosses the owner boundary. */
        public Map<String, Object> allowedArguments(Map<String, Object> arguments) {
            if (arguments == null || arguments.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> filtered = new LinkedHashMap<>();
            arguments.forEach((key, value) -> {
                if (allowedFields.contains(key)) {
                    filtered.put(key, value);
                }
            });
            return Collections.unmodifiableMap(filtered);
        }
    }

    private static Set<String> fields(String... fieldNames) {
        Objects.requireNonNull(fieldNames, "fieldNames");
        return Set.of(fieldNames);
    }
}
