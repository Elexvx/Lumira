package com.lumira.saas.modules.system.user.repository;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Durable state boundary for system-user administration.
 *
 * <p>Authentication, permission decisions, password policy, masking and audit
 * intent remain in the application service. SQL, data-scope query predicates,
 * row mapping and guarded membership writes live in the JDBC adapter.</p>
 */
public interface SystemUserManagementRepository {
    PageResponse<SystemVO.UserVO> findUsers(UserSearch search);

    boolean canAccessActiveUser(Long userId, DataVisibility visibility);

    SystemVO.UserVO findActiveUser(Long userId);

    int updateStatus(Long userId, String userUuid, String status, Actor actor, LocalDateTime updatedAt);

    int softDeleteUser(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt);

    void retireUserRelations(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt);

    List<SystemVO.RoleVO> findActiveUserRoles(Long userId, String userUuid);

    UserSaveResult saveUser(UserSave command);

    int updatePasswordHash(Long userId, String userUuid, String passwordHash, Actor actor, LocalDateTime updatedAt);

    Long findActiveUserIdByUsername(String username);

    Long findActiveIdentityUserId(String normalizedUsername);

    int countActiveRoles(List<Long> roleIds);

    int countPrivilegedRoles(List<Long> roleIds);

    int countEnabledDepartments(List<Long> departmentIds);

    void retireUserRoles(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt);

    int upsertUserRole(Long userId, String userUuid, Long roleId, Actor actor);

    void retireUserDepartments(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt);

    int upsertUserDepartment(Long userId, String userUuid, Long departmentId, boolean primary, Actor actor);

    Map<Long, String> findActiveUserUuids(List<Long> userIds);

    String findActiveUserUuid(Long userId);

    List<Long> findActiveUserRoleIds(Long userId, String userUuid);

    List<Long> findActiveUserDepartmentIds(Long userId, String userUuid);

    List<String> findActiveUserRoleNames(Long userId, String userUuid);

    List<String> findActiveUserDepartmentNames(Long userId, String userUuid);

    Map<Long, List<String>> findActiveUserRoleNames(List<Long> userIds);

    Map<Long, List<String>> findActiveUserDepartmentNames(List<Long> userIds);

    Set<Long> findActiveDepartmentTree(Long departmentId);

    record Actor(Long userId, String userUuid) {}

    record DataVisibility(boolean all, Set<Long> departmentIds, Set<Long> userIds) {}

    record UserSearch(
            Long userId,
            String uid,
            String normalizedUsername,
            String normalizedMobile,
            String normalizedEmail,
            Set<Long> departmentFilterIds,
            String status,
            String source,
            LocalDateTime registeredStart,
            LocalDateTime registeredEnd,
            LocalDateTime lastLoginStart,
            LocalDateTime lastLoginEnd,
            Long cursorId,
            LocalDateTime cursorCreatedAt,
            long pageNo,
            long pageSize,
            DataVisibility visibility
    ) {}

    record UserSave(
            Long userId,
            String userUuid,
            String generatedUuid,
            String username,
            String passwordHash,
            String mobile,
            String nickname,
            String realName,
            String avatarUrl,
            String email,
            String birthMonth,
            String gender,
            String region,
            String availableTime,
            String idCardNumber,
            String status,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record UserSaveResult(int writeCount, Long userId) {}
}
