package com.lumira.saas.modules.system.internal.repository;

import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.saas.modules.system.vo.SystemVO;

import java.util.List;

/**
 * Persistence boundary for internal system API operations.
 *
 * <p>The controller and application facade use only these typed operations;
 * SQL implementation details belong to the infrastructure adapter.</p>
 */
public interface InternalSystemRepository {

    List<SystemUserSnapshotDTO> findEnabledUserIdentities(List<Long> userIds);

    List<SystemRoleSnapshotDTO> findRoleNames(List<Long> roleIds);

    List<SystemUserSnapshotDTO> findEnabledRoleUserIdentities(Long roleId);

    List<SystemUserEmailRecipientDTO> findEmailRecipientsByUserIds(List<Long> userIds);

    List<SystemUserWechatRecipientDTO> findWechatRecipientsByUserIds(List<Long> userIds);

    List<SystemUserEmailRecipientDTO> findEmailRecipientsByRoleId(Long roleId);

    List<SystemUserWechatRecipientDTO> findWechatRecipientsByRoleId(Long roleId);

    List<SystemUserEmailRecipientDTO> findPlatformEmailRecipients();

    List<SystemUserWechatRecipientDTO> findPlatformWechatRecipients();

    int upsertPluginPermission(
            String permissionKey,
            String permissionName,
            String permissionGroup,
            String pluginCode,
            Long actorId,
            String actorUuid
    );

    List<Long> findActiveAdminRoleIds();

    int upsertRolePermission(Long roleId, String permissionKey, Long actorId, String actorUuid);

    boolean hasActivePluginPermission(String permissionKey, String pluginCode, String actorUuid);

    boolean hasActivePluginRolePermission(Long roleId, String pluginCode, String permissionKey, String actorUuid);

    List<PlatformConfigValue> findPlatformConfigValues(List<String> configKeys);

    int updatePassword(
            String encodedPassword,
            Long actorId,
            String actorUuid,
            Long userId,
            String userUuid
    );

    List<SystemVO.MenuVO> findEnabledSystemMenus();

    List<CurrentUserRoleOptionDTO> findRoleOptions(Long userId, String userUuid);

    Long findEnabledUserIdByWechatBinding(String unionid, String openid);

    boolean hasActiveWechatBinding(String unionid, String openid);

    int insertWechatUser(String userUuid, String username, String passwordHash, Long actorId, String actorUuid);

    int insertLoginCodeUser(
            String userUuid,
            String username,
            String passwordHash,
            String mobile,
            String nickname,
            String email,
            Long actorId,
            String actorUuid
    );

    Long findActiveUserIdByUsername(String username);

    int upsertWechatBinding(
            Long userId,
            String userUuid,
            String openid,
            String unionid,
            String scope
    );

    boolean hasActiveWechatBindingOwnedByUser(
            Long userId,
            String userUuid,
            String openid,
            String unionid
    );

    int updateWechatProfile(Long userId, String userUuid, String nickname, String avatarUrl);

    int upsertUserRole(Long userId, String userUuid, Long roleId);

    boolean hasActiveUserRole(Long userId, String userUuid, Long roleId);

    RegistrationRole findActiveRoleByCode(String roleCode);

    List<String> findActiveRolePermissionKeys(Long roleId);

    String findLatestPlatformConfigValue(String configKey);

    record PlatformConfigValue(String key, String value) {
    }

    record RegistrationRole(Long id, String roleCode, String roleType) {
    }
}
