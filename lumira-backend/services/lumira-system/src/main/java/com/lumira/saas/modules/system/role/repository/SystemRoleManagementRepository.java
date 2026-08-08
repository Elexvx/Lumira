package com.lumira.saas.modules.system.role.repository;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.role.vo.RoleDataScopeVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persistence boundary for system-role administration.
 *
 * <p>The application service keeps authorization, assignment policy and
 * domain-event publication. SQL and guarded role/permission/data-scope writes
 * are isolated in the JDBC adapter.</p>
 */
public interface SystemRoleManagementRepository {
    PageResponse<SystemVO.RoleVO> findRoles(RoleSearch search);

    SystemVO.RoleVO findActiveRoleById(Long roleId);

    SystemVO.RoleVO findLatestActiveRoleByCode(String roleCode);

    int softDeleteRole(RoleVersion role, Actor actor, LocalDateTime updatedAt);

    void retireDeletedRoleRelations(Long roleId, Actor actor, LocalDateTime updatedAt);

    List<String> findActivePermissionKeys(Long roleId);

    RoleSaveResult saveRole(RoleSave command);

    int retireRolePermissions(Long roleId, RoleVersion existingRole, Actor actor, LocalDateTime updatedAt);

    int upsertRolePermissions(Long roleId, RoleVersion existingRole, List<String> permissionKeys, Actor actor);

    List<RoleDataScopeVO> findActiveDataScopes(Long roleId);

    int retireRoleDataScopes(Long roleId, RoleVersion existingRole, Actor actor, LocalDateTime updatedAt);

    int upsertRoleDataScopes(Long roleId, List<RoleDataScopeValue> scopes, Actor actor);

    int countActiveRolePermissions(Long roleId);

    int countActiveRoleUsers(Long roleId);

    Map<Long, Integer> countActiveRolePermissions(List<Long> roleIds);

    Map<Long, Integer> countActiveRoleUsers(List<Long> roleIds);

    Map<String, String> findPlatformConfigValues(List<String> configKeys);

    ConfigSaveResult savePlatformConfig(ConfigSave command);

    record Actor(Long userId, String userUuid) {}

    record RoleSearch(String roleCode, String roleName, String roleType, long pageNo, long pageSize) {}

    record RoleVersion(Long roleId, String roleCode, String roleType) {}

    record RoleSave(
            RoleVersion existingRole,
            String roleCode,
            String roleName,
            String roleType,
            String defaultHomePath,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record RoleSaveResult(int writeCount, Long roleId) {}

    record RoleDataScopeValue(String resourceCode, String scopeType, String customDeptIds, String customUserIds) {}

    record ConfigSave(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record ConfigSaveResult(int writeCount, boolean inserted) {}
}
