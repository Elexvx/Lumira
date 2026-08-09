package com.lumira.saas.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.ai.AiSystemManagementToolPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * System-owned, permission-checked implementation of the closed AI management-tool contract.
 *
 * <p>System DTOs and VOs are intentionally converted at this boundary.  AI receives only plain
 * maps for the native tool response and cannot reach SystemManagementAppService directly.</p>
 */
public class SystemAiSystemManagementToolPort implements AiSystemManagementToolPort {

    private static final Long DEFAULT_ADMINISTRATOR_ID = 1001L;

    private final SystemManagementAppService managementAppService;
    private final PermissionGuard permissionGuard;
    private final ObjectMapper objectMapper;

    public SystemAiSystemManagementToolPort(
            SystemManagementAppService managementAppService,
            PermissionGuard permissionGuard,
            ObjectMapper objectMapper
    ) {
        this.managementAppService = managementAppService;
        this.permissionGuard = permissionGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> execute(CurrentUser actor, Action action, Map<String, Object> arguments) {
        if (action == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "AI system management action cannot be blank");
        }
        permissionGuard.requirePermission(actor, action.requiredPermission());
        Map<String, Object> allowedArguments = action.allowedArguments(arguments);
        return switch (action) {
            case CREATE_USER -> createUser(actor, allowedArguments);
            case UPDATE_USER -> updateUser(actor, allowedArguments);
            case UPDATE_USER_STATUS -> updateUserStatus(actor, allowedArguments);
            case DELETE_USER -> deleteUser(actor, allowedArguments);
            case UPDATE_CURRENT_AVATAR -> updateCurrentAvatar(actor, allowedArguments);
            case CREATE_ROLE -> createRole(actor, allowedArguments);
            case UPDATE_ROLE -> updateRole(actor, allowedArguments);
            case UPDATE_ROLE_PERMISSIONS -> updateRolePermissions(actor, allowedArguments);
            case DELETE_ROLE -> deleteRole(actor, allowedArguments);
            case CREATE_MENU -> createMenu(actor, allowedArguments);
            case UPDATE_MENU -> updateMenu(actor, allowedArguments);
            case UPDATE_MENU_STATUS -> updateMenuStatus(actor, allowedArguments);
            case DELETE_MENU -> deleteMenu(actor, allowedArguments);
            case CREATE_DICT_TYPE -> createDictType(actor, allowedArguments);
            case UPDATE_DICT_TYPE -> updateDictType(actor, allowedArguments);
            case DELETE_DICT_TYPE -> deleteDictType(actor, allowedArguments);
            case CREATE_DICT_ITEM -> createDictItem(actor, allowedArguments);
            case UPDATE_DICT_ITEM -> updateDictItem(actor, allowedArguments);
            case DELETE_DICT_ITEM -> deleteDictItem(actor, allowedArguments);
            case CREATE_CONFIG -> createConfig(actor, allowedArguments);
            case UPDATE_CONFIG -> updateConfig(actor, allowedArguments);
            case UPDATE_BRANDING -> updateBrandingSettings(actor, allowedArguments);
            case UPDATE_AGREEMENT -> updateAgreementSettings(actor, allowedArguments);
            case UPDATE_WATERMARK -> updateWatermarkSettings(actor, allowedArguments);
            case UPDATE_FLOATING_WINDOW -> updateFloatingWindowSettings(actor, allowedArguments);
        };
    }

    @Override
    public String findConfigKeyForAiUpdate(CurrentUser actor, Long configId) {
        if (configId == null || configId <= 0) {
            return null;
        }
        permissionGuard.requirePermission(actor, Action.UPDATE_CONFIG.requiredPermission());
        SystemVO.ConfigVO config = managementAppService.getConfigForUpdate(actor, configId);
        return config == null ? null : config.getConfigKey();
    }

    private Map<String, Object> createUser(CurrentUser actor, Map<String, Object> arguments) {
        SystemDTO.UserUpsertRequest request = objectMapper.convertValue(arguments, SystemDTO.UserUpsertRequest.class);
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        return response("user", managementAppService.createUser(actor, request));
    }

    private Map<String, Object> updateUser(CurrentUser actor, Map<String, Object> arguments) {
        Long userId = requireLong(arguments, "userId");
        SystemVO.UserDetailVO existing = managementAppService.getUser(actor, userId);
        requireTargetUserUuid(arguments, existing);
        return response("user", managementAppService.updateUser(actor, userId, mergeUserRequest(existing, arguments)));
    }

    private Map<String, Object> updateUserStatus(CurrentUser actor, Map<String, Object> arguments) {
        Long userId = requireLong(arguments, "userId");
        String status = requiredString(arguments, "status");
        SystemVO.UserDetailVO existing = managementAppService.getUser(actor, userId);
        requireTargetUserUuid(arguments, existing);
        if (isActorUser(actor, userId) && "DISABLED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Current login account cannot be disabled via AI");
        }
        if (DEFAULT_ADMINISTRATOR_ID.equals(userId) && "DISABLED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default administrator account cannot be disabled via AI");
        }
        return Map.of(
                "updated", managementAppService.updateUserStatus(actor, userId, status),
                "userId", userId,
                "status", status.toUpperCase(Locale.ROOT)
        );
    }

    private Map<String, Object> deleteUser(CurrentUser actor, Map<String, Object> arguments) {
        Long userId = requireLong(arguments, "userId");
        SystemVO.UserDetailVO existing = managementAppService.getUser(actor, userId);
        requireTargetUserUuid(arguments, existing);
        if (isActorUser(actor, userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Current login account cannot be deleted via AI");
        }
        if (DEFAULT_ADMINISTRATOR_ID.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default administrator account cannot be deleted via AI");
        }
        return Map.of("deleted", managementAppService.deleteUser(actor, userId), "userId", userId);
    }

    private Map<String, Object> updateCurrentAvatar(CurrentUser actor, Map<String, Object> arguments) {
        String avatarUrl = requiredString(arguments, "avatarUrl");
        return response("currentUser", managementAppService.updateCurrentUserAvatar(actor, avatarUrl));
    }

    private Map<String, Object> createRole(CurrentUser actor, Map<String, Object> arguments) {
        SystemDTO.RoleUpsertRequest request = objectMapper.convertValue(arguments, SystemDTO.RoleUpsertRequest.class);
        if (!StringUtils.hasText(request.getRoleType())) {
            request.setRoleType("BUSINESS");
        }
        return response("role", managementAppService.createRole(actor, request));
    }

    private Map<String, Object> updateRole(CurrentUser actor, Map<String, Object> arguments) {
        Long roleId = requireLong(arguments, "roleId");
        return response("role", managementAppService.updateRole(
                actor,
                roleId,
                objectMapper.convertValue(withoutKeys(arguments, "roleId"), SystemDTO.RoleUpsertRequest.class)
        ));
    }

    private Map<String, Object> updateRolePermissions(CurrentUser actor, Map<String, Object> arguments) {
        Long roleId = requireLong(arguments, "roleId");
        List<String> permissionKeys = stringListArg(arguments.get("permissionKeys"));
        return Map.of(
                "updated", managementAppService.updateRolePermissions(actor, roleId, permissionKeys),
                "roleId", roleId,
                "permissionKeys", permissionKeys
        );
    }

    private Map<String, Object> deleteRole(CurrentUser actor, Map<String, Object> arguments) {
        Long roleId = requireLong(arguments, "roleId");
        return Map.of("deleted", managementAppService.deleteRole(actor, roleId), "roleId", roleId);
    }

    private Map<String, Object> createMenu(CurrentUser actor, Map<String, Object> arguments) {
        SystemDTO.MenuUpsertRequest request = objectMapper.convertValue(arguments, SystemDTO.MenuUpsertRequest.class);
        defaultMenuFields(request);
        return response("menu", managementAppService.createMenu(actor, request));
    }

    private Map<String, Object> updateMenu(CurrentUser actor, Map<String, Object> arguments) {
        Long menuId = requireLong(arguments, "menuId");
        SystemDTO.MenuUpsertRequest request = objectMapper.convertValue(
                withoutKeys(arguments, "menuId"), SystemDTO.MenuUpsertRequest.class
        );
        defaultMenuFields(request);
        return response("menu", managementAppService.updateMenu(actor, menuId, request));
    }

    private Map<String, Object> updateMenuStatus(CurrentUser actor, Map<String, Object> arguments) {
        Long menuId = requireLong(arguments, "menuId");
        String status = requiredString(arguments, "status");
        return Map.of(
                "updated", managementAppService.updateMenuStatus(actor, menuId, status),
                "menuId", menuId,
                "status", status
        );
    }

    private Map<String, Object> deleteMenu(CurrentUser actor, Map<String, Object> arguments) {
        Long menuId = requireLong(arguments, "menuId");
        return Map.of("deleted", managementAppService.deleteMenu(actor, menuId), "menuId", menuId);
    }

    private Map<String, Object> createDictType(CurrentUser actor, Map<String, Object> arguments) {
        SystemDTO.DictTypeUpsertRequest request = objectMapper.convertValue(arguments, SystemDTO.DictTypeUpsertRequest.class);
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        return response("dictType", managementAppService.createDictType(actor, request));
    }

    private Map<String, Object> updateDictType(CurrentUser actor, Map<String, Object> arguments) {
        Long dictTypeId = requireLong(arguments, "dictTypeId");
        return response("dictType", managementAppService.updateDictType(
                actor,
                dictTypeId,
                objectMapper.convertValue(withoutKeys(arguments, "dictTypeId"), SystemDTO.DictTypeUpsertRequest.class)
        ));
    }

    private Map<String, Object> deleteDictType(CurrentUser actor, Map<String, Object> arguments) {
        Long dictTypeId = requireLong(arguments, "dictTypeId");
        return Map.of("deleted", managementAppService.deleteDictType(actor, dictTypeId), "dictTypeId", dictTypeId);
    }

    private Map<String, Object> createDictItem(CurrentUser actor, Map<String, Object> arguments) {
        Long dictTypeId = requireLong(arguments, "dictTypeId");
        SystemDTO.DictItemUpsertRequest request = objectMapper.convertValue(
                withoutKeys(arguments, "dictTypeId"), SystemDTO.DictItemUpsertRequest.class
        );
        if (request.getSortNo() == null) {
            request.setSortNo(0);
        }
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
        return response("dictItem", managementAppService.createDictItem(actor, dictTypeId, request));
    }

    private Map<String, Object> updateDictItem(CurrentUser actor, Map<String, Object> arguments) {
        Long dictTypeId = requireLong(arguments, "dictTypeId");
        Long itemId = requireLong(arguments, "itemId");
        return response("dictItem", managementAppService.updateDictItem(
                actor,
                dictTypeId,
                itemId,
                objectMapper.convertValue(
                        withoutKeys(arguments, "dictTypeId", "itemId"), SystemDTO.DictItemUpsertRequest.class
                )
        ));
    }

    private Map<String, Object> deleteDictItem(CurrentUser actor, Map<String, Object> arguments) {
        Long dictTypeId = requireLong(arguments, "dictTypeId");
        Long itemId = requireLong(arguments, "itemId");
        return Map.of(
                "deleted", managementAppService.deleteDictItem(actor, dictTypeId, itemId),
                "dictTypeId", dictTypeId,
                "itemId", itemId
        );
    }

    private Map<String, Object> createConfig(CurrentUser actor, Map<String, Object> arguments) {
        return response("config", managementAppService.createConfig(
                actor, objectMapper.convertValue(arguments, SystemDTO.ConfigUpsertRequest.class)
        ));
    }

    private Map<String, Object> updateConfig(CurrentUser actor, Map<String, Object> arguments) {
        Long configId = requireLong(arguments, "configId");
        return response("config", managementAppService.updateConfig(
                actor,
                configId,
                objectMapper.convertValue(withoutKeys(arguments, "configId"), SystemDTO.ConfigUpsertRequest.class)
        ));
    }

    private Map<String, Object> updateBrandingSettings(CurrentUser actor, Map<String, Object> arguments) {
        return response("brandingSettings", managementAppService.updateBrandingSettings(
                actor, objectMapper.convertValue(arguments, SystemDTO.BrandingSettingsRequest.class)
        ));
    }

    private Map<String, Object> updateAgreementSettings(CurrentUser actor, Map<String, Object> arguments) {
        return response("agreementSettings", managementAppService.updateAgreementSettings(
                actor, objectMapper.convertValue(arguments, SystemDTO.AgreementSettingsRequest.class)
        ));
    }

    private Map<String, Object> updateWatermarkSettings(CurrentUser actor, Map<String, Object> arguments) {
        return response("watermarkSettings", managementAppService.updateWatermarkSettings(
                actor, objectMapper.convertValue(arguments, SystemDTO.WatermarkSettingsRequest.class)
        ));
    }

    private Map<String, Object> updateFloatingWindowSettings(CurrentUser actor, Map<String, Object> arguments) {
        return response("floatingWindowSettings", managementAppService.updateFloatingWindowSettings(
                actor, objectMapper.convertValue(arguments, SystemDTO.FloatingWindowSettingsRequest.class)
        ));
    }

    private SystemDTO.UserUpsertRequest mergeUserRequest(
            SystemVO.UserDetailVO existing,
            Map<String, Object> arguments
    ) {
        SystemDTO.UserUpsertRequest request = new SystemDTO.UserUpsertRequest();
        request.setUsername(stringArg(arguments, "username", existing.getUsername()));
        request.setMobile(stringArg(arguments, "mobile", existing.getMobile()));
        request.setNickname(stringArg(arguments, "nickname", existing.getNickname()));
        request.setRealName(stringArg(arguments, "realName", existing.getRealName()));
        request.setAvatarUrl(stringArg(arguments, "avatarUrl", existing.getAvatarUrl()));
        request.setEmail(stringArg(arguments, "email", existing.getEmail()));
        request.setBirthMonth(stringArg(arguments, "birthMonth", existing.getBirthMonth()));
        request.setGender(stringArg(arguments, "gender", existing.getGender()));
        request.setRegion(stringArg(arguments, "region", existing.getRegion()));
        request.setAvailableTime(stringArg(arguments, "availableTime", existing.getAvailableTime()));
        request.setIdCardNumber(stringArg(arguments, "idCardNumber", existing.getIdCardNumber()));
        request.setStatus(stringArg(arguments, "status", existing.getStatus()));
        request.setRoleIds(existing.getRoleIds());
        request.setDeptIds(existing.getDeptIds());
        request.setPrimaryDeptId(existing.getPrimaryDeptId());
        if (arguments.containsKey("roleIds")) {
            request.setRoleIds(longListArg(arguments.get("roleIds")));
        }
        if (arguments.containsKey("deptIds")) {
            request.setDeptIds(longListArg(arguments.get("deptIds")));
        }
        if (arguments.containsKey("primaryDeptId")) {
            request.setPrimaryDeptId(longValue(arguments.get("primaryDeptId"), "primaryDeptId"));
        }
        return request;
    }

    private void requireTargetUserUuid(Map<String, Object> arguments, SystemVO.UserDetailVO existing) {
        String expectedUserUuid = requiredString(arguments, "userUuid");
        if (existing == null || !StringUtils.hasText(existing.getUserUuid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Target user identity cannot be verified");
        }
        if (!expectedUserUuid.trim().equals(existing.getUserUuid().trim())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Target user identity mismatch");
        }
    }

    private boolean isActorUser(CurrentUser actor, Long userId) {
        return actor != null
                && userId != null
                && userId.equals(actor.getUserId())
                && StringUtils.hasText(actor.getUserUuid());
    }

    private void defaultMenuFields(SystemDTO.MenuUpsertRequest request) {
        if (request.getParentId() == null) {
            request.setParentId(0L);
        }
        if (!StringUtils.hasText(request.getMenuType())) {
            request.setMenuType("MENU");
        }
        if (request.getSortNo() == null) {
            request.setSortNo(0);
        }
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    private Map<String, Object> response(String key, Object value) {
        return Map.of(key, toPayload(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayload(Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<?, ?> raw = objectMapper.convertValue(value, Map.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        raw.forEach((key, item) -> payload.put(String.valueOf(key), item));
        return payload;
    }

    private Map<String, Object> withoutKeys(Map<String, Object> arguments, String... keys) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(arguments);
        for (String key : keys) {
            copy.remove(key);
        }
        return copy;
    }

    private Long requireLong(Map<String, Object> arguments, String key) {
        Long value = longValue(arguments == null ? null : arguments.get(key), key);
        if (value == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " cannot be blank");
        }
        return value;
    }

    private Long longValue(Object value, String key) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " must be a number");
        }
    }

    private List<Long> longListArg(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> longValue(item, "list item"))
                    .filter(item -> item != null && item > 0)
                    .toList();
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Parameter must be a numeric array");
    }

    private List<String> stringListArg(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> item == null ? null : String.valueOf(item).trim())
                    .filter(StringUtils::hasText)
                    .toList();
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Parameter must be a string array");
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        String value = stringArg(arguments, key, null);
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, key + " cannot be blank");
        }
        return value;
    }

    private String stringArg(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
