package com.lumira.saas.modules.system.internal.app;

import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.system.internal.repository.InternalSystemRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-facing operations used by the internal-system HTTP adapter.
 * SQL is intentionally delegated to {@link InternalSystemRepository}.
 */
@Service
public class InternalSystemApplicationService {

    private static final Long SERVICE_PRINCIPAL_ID = 0L;
    private static final String SERVICE_PRINCIPAL_UUID = "00000000-0000-0000-0000-000000000000";

    private final InternalSystemRepository repository;

    public InternalSystemApplicationService(InternalSystemRepository repository) {
        this.repository = repository;
    }

    public List<SystemUserSnapshotDTO> findEnabledUserIdentities(List<Long> userIds) {
        return repository.findEnabledUserIdentities(userIds);
    }

    public List<SystemRoleSnapshotDTO> findRoleNames(List<Long> roleIds) {
        return repository.findRoleNames(roleIds);
    }

    public List<SystemUserSnapshotDTO> findEnabledRoleUserIdentities(Long roleId) {
        return repository.findEnabledRoleUserIdentities(roleId);
    }

    public List<SystemUserEmailRecipientDTO> findEmailRecipientsByUserIds(List<Long> userIds) {
        return repository.findEmailRecipientsByUserIds(userIds);
    }

    public List<SystemUserWechatRecipientDTO> findWechatRecipientsByUserIds(List<Long> userIds) {
        return repository.findWechatRecipientsByUserIds(userIds);
    }

    public List<SystemUserEmailRecipientDTO> findEmailRecipientsByRoleId(Long roleId) {
        return repository.findEmailRecipientsByRoleId(roleId);
    }

    public List<SystemUserWechatRecipientDTO> findWechatRecipientsByRoleId(Long roleId) {
        return repository.findWechatRecipientsByRoleId(roleId);
    }

    public List<SystemUserEmailRecipientDTO> findPlatformEmailRecipients() {
        return repository.findPlatformEmailRecipients();
    }

    public List<SystemUserWechatRecipientDTO> findPlatformWechatRecipients() {
        return repository.findPlatformWechatRecipients();
    }

    public void registerPluginPermissions(PluginPermissionRegistrationRequestDTO request) {
        if (request == null || request.permissions() == null || request.permissions().isEmpty()) {
            return;
        }
        for (PluginPermissionRegistrationRequestDTO.Permission permission : request.permissions()) {
            if (permission == null || !StringUtils.hasText(permission.permissionKey())) {
                continue;
            }
            int updated = repository.upsertPluginPermission(
                    permission.permissionKey(),
                    StringUtils.hasText(permission.permissionName()) ? permission.permissionName() : permission.permissionKey(),
                    StringUtils.hasText(permission.permissionGroup()) ? permission.permissionGroup() : request.pluginCode(),
                    request.pluginCode(),
                    SERVICE_PRINCIPAL_ID,
                    SERVICE_PRINCIPAL_UUID
            );
            if (updated <= 0 && !repository.hasActivePluginPermission(
                    permission.permissionKey(), request.pluginCode(), SERVICE_PRINCIPAL_UUID
            )) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Plugin permission changed, please retry");
            }
        }
        for (Long roleId : repository.findActiveAdminRoleIds()) {
            for (PluginPermissionRegistrationRequestDTO.Permission permission : request.permissions()) {
                if (permission == null || !StringUtils.hasText(permission.permissionKey())) {
                    continue;
                }
                int updated = repository.upsertRolePermission(
                        roleId,
                        permission.permissionKey(),
                        SERVICE_PRINCIPAL_ID,
                        SERVICE_PRINCIPAL_UUID
                );
                if (updated <= 0 && !repository.hasActivePluginRolePermission(
                        requirePositiveRoleId(roleId),
                        request.pluginCode(),
                        permission.permissionKey(),
                        SERVICE_PRINCIPAL_UUID
                )) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "Plugin role permission changed, please retry");
                }
            }
        }
    }

    public Map<String, String> platformConfigValues(List<String> configKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        for (InternalSystemRepository.PlatformConfigValue row : repository.findPlatformConfigValues(configKeys)) {
            if (StringUtils.hasText(row.key()) && row.value() != null && !values.containsKey(row.key())) {
                values.put(row.key(), row.value());
            }
        }
        return values;
    }

    public int updatePassword(String encodedPassword, Long userId, String userUuid) {
        return repository.updatePassword(
                encodedPassword,
                SERVICE_PRINCIPAL_ID,
                SERVICE_PRINCIPAL_UUID,
                userId,
                userUuid
        );
    }

    public List<SystemVO.MenuVO> findEnabledSystemMenus() {
        return repository.findEnabledSystemMenus();
    }

    public List<CurrentUserRoleOptionDTO> findRoleOptions(Long userId, String userUuid) {
        return repository.findRoleOptions(userId, userUuid);
    }

    public Long findEnabledUserIdByWechatBinding(String unionid, String openid) {
        return repository.findEnabledUserIdByWechatBinding(unionid, openid);
    }

    public boolean hasActiveWechatBinding(String unionid, String openid) {
        return repository.hasActiveWechatBinding(unionid, openid);
    }

    public int insertWechatUser(String userUuid, String username, String passwordHash) {
        return repository.insertWechatUser(userUuid, username, passwordHash, SERVICE_PRINCIPAL_ID, SERVICE_PRINCIPAL_UUID);
    }

    public int insertLoginCodeUser(
            String userUuid,
            String username,
            String passwordHash,
            String mobile,
            String nickname,
            String email
    ) {
        return repository.insertLoginCodeUser(
                userUuid,
                username,
                passwordHash,
                mobile,
                nickname,
                email,
                SERVICE_PRINCIPAL_ID,
                SERVICE_PRINCIPAL_UUID
        );
    }

    public Long findActiveUserIdByUsername(String username) {
        return repository.findActiveUserIdByUsername(username);
    }

    public int upsertWechatBinding(Long userId, String userUuid, String openid, String unionid, String scope) {
        return repository.upsertWechatBinding(userId, userUuid, openid, unionid, scope);
    }

    public boolean hasActiveWechatBindingOwnedByUser(Long userId, String userUuid, String openid, String unionid) {
        return repository.hasActiveWechatBindingOwnedByUser(userId, userUuid, openid, unionid);
    }

    public int updateWechatProfile(Long userId, String userUuid, String nickname, String avatarUrl) {
        return repository.updateWechatProfile(userId, userUuid, nickname, avatarUrl);
    }

    public int upsertUserRole(Long userId, String userUuid, Long roleId) {
        return repository.upsertUserRole(userId, userUuid, roleId);
    }

    public boolean hasActiveUserRole(Long userId, String userUuid, Long roleId) {
        return repository.hasActiveUserRole(userId, userUuid, roleId);
    }

    public RegistrationRole findActiveRoleByCode(String roleCode) {
        InternalSystemRepository.RegistrationRole role = repository.findActiveRoleByCode(roleCode);
        return role == null ? null : new RegistrationRole(role.id(), role.roleCode(), role.roleType());
    }

    public List<String> findActiveRolePermissionKeys(Long roleId) {
        return repository.findActiveRolePermissionKeys(roleId);
    }

    public String findLatestPlatformConfigValue(String configKey) {
        return repository.findLatestPlatformConfigValue(configKey);
    }

    private Long requirePositiveRoleId(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "roleId must be positive");
        }
        return roleId;
    }

    public record RegistrationRole(Long id, String roleCode, String roleType) {
    }
}
