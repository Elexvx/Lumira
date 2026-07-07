package com.lumira.saas.modules.system.user.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.support.UserUidGenerator;
import com.lumira.saas.modules.system.user.vo.UserDetailVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemUserManagementAppService {

    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final String ASYNC_EXPORT_SESSION_PREFIX = "internal-export-task-";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String RESOURCE_SYSTEM_USER = "system:user";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final MyBatisQueryOperations jdbcTemplate;
    private final UserDomainService userDomainService;
    private final IamUserService iamUserService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final OperationAuditService operationAuditService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final boolean enforceTrustedUserResolution;

    public SystemUserManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        this(
                jdbcTemplate,
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                null,
                null,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService,
                false
        );
    }

    @Autowired
    public SystemUserManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        this(
                jdbcTemplate,
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService,
                true
        );
    }

    private SystemUserManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDomainService = userDomainService;
        this.iamUserService = iamUserService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.operationAuditService = operationAuditService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public PageResponse<SystemVO.UserVO> listUsers(
            CurrentUser currentUser,
            Long userId,
            String uid,
            String username,
            String mobile,
            String email,
            Long deptId,
            String status,
            String source,
            String registeredStart,
            String registeredEnd,
            String lastLoginStart,
            String lastLoginEnd,
            Long cursorId,
            String cursorCreatedAt,
            long pageNo,
            long pageSize
    ) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:view");
        return listUsersAfterAuthentication(
                currentUser,
                userId,
                uid,
                username,
                mobile,
                email,
                deptId,
                status,
                source,
                registeredStart,
                registeredEnd,
                lastLoginStart,
                lastLoginEnd,
                cursorId,
                cursorCreatedAt,
                pageNo,
                pageSize,
                shouldBypassSessionAuthentication(currentUser, false)
        );
    }

    public PageResponse<SystemVO.UserVO> listUsersFromTrustedSnapshot(
            CurrentUser currentUser,
            Long userId,
            String uid,
            String username,
            String mobile,
            String email,
            Long deptId,
            String status,
            String source,
            String registeredStart,
            String registeredEnd,
            String lastLoginStart,
            String lastLoginEnd,
            Long cursorId,
            String cursorCreatedAt,
            long pageNo,
            long pageSize
    ) {
        assertAuthenticatedFromTrustedSnapshot(currentUser);
        requirePermission(currentUser, "system:user:export");
        return listUsersAfterAuthentication(
                currentUser,
                userId,
                uid,
                username,
                mobile,
                email,
                deptId,
                status,
                source,
                registeredStart,
                registeredEnd,
                lastLoginStart,
                lastLoginEnd,
                cursorId,
                cursorCreatedAt,
                pageNo,
                pageSize,
                shouldBypassSessionAuthentication(currentUser, true)
        );
    }

    private PageResponse<SystemVO.UserVO> listUsersAfterAuthentication(
            CurrentUser currentUser,
            Long userId,
            String uid,
            String username,
            String mobile,
            String email,
            Long deptId,
            String status,
            String source,
            String registeredStart,
            String registeredEnd,
            String lastLoginStart,
            String lastLoginEnd,
            Long cursorId,
            String cursorCreatedAt,
            long pageNo,
            long pageSize,
            boolean bypassSessionAuthentication
    ) {
        if (bypassSessionAuthentication) {
            assertAuthenticatedFromTrustedSnapshot(currentUser);
        } else {
            assertAuthenticated(currentUser);
        }
        String baseSql = """
                from sys_user u
                left join iam_user iu
                  on iu.id = u.id
                 and iu.deleted = 0
                where u.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(uid)) {
            String normalizedUid = uid.trim();
            baseSql += " and u.uuid = ?";
            params.add(normalizedUid);
        } else if (userId != null) {
            baseSql += " and u.id = ?";
            params.add(userId);
        }
        if (StringUtils.hasText(username)) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'USERNAME'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_USERNAME, username));
        }
        if (StringUtils.hasText(mobile)) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'MOBILE'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_MOBILE, mobile));
        }
        if (StringUtils.hasText(email)) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'EMAIL'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, email));
        }
        if (deptId != null) {
            Set<Long> visibleDeptIds = queryDepartmentAndDescendantIds(deptId);
            if (visibleDeptIds.isEmpty()) {
                baseSql += " and 1 = 0";
            } else {
                baseSql += """
                         and exists (
                             select 1
                             from sys_user_department ud_filter
                             where ud_filter.user_id = u.id
                               and ud_filter.dept_id in (%s)
                               and ud_filter.deleted = 0
                          )
                         """.formatted(placeholders(visibleDeptIds.size()));
                params.addAll(visibleDeptIds);
            }
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and u.status = ?";
            params.add(normalizeUserStatus(status));
        }
        if (StringUtils.hasText(source)) {
            baseSql += " and iu.source = ?";
            params.add(source.trim().toUpperCase(Locale.ROOT));
        }
        LocalDateTime registeredStartAt = parseDateTimeParam(registeredStart, false);
        LocalDateTime registeredEndAt = parseDateTimeParam(registeredEnd, true);
        LocalDateTime lastLoginStartAt = parseDateTimeParam(lastLoginStart, false);
        LocalDateTime lastLoginEndAt = parseDateTimeParam(lastLoginEnd, true);
        LocalDateTime cursorCreatedAtValue = parseDateTimeParam(cursorCreatedAt, false);
        if (registeredStartAt != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) >= ?";
            params.add(registeredStartAt);
        }
        if (registeredEndAt != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) <= ?";
            params.add(registeredEndAt);
        }
        if (lastLoginStartAt != null) {
            baseSql += " and iu.last_login_at >= ?";
            params.add(lastLoginStartAt);
        }
        if (lastLoginEndAt != null) {
            baseSql += " and iu.last_login_at <= ?";
            params.add(lastLoginEndAt);
        }
        DataPermissionSql dataPermissionSql = userDataPermissionClause(currentUser, "u");
        baseSql += dataPermissionSql.sql();
        params.addAll(dataPermissionSql.params());
        boolean cursorMode = cursorId != null || cursorCreatedAtValue != null;
        if (cursorCreatedAtValue != null && cursorId != null) {
            baseSql += " and (coalesce(iu.registered_at, u.created_at) < ? or (coalesce(iu.registered_at, u.created_at) = ? and u.id < ?))";
            params.add(cursorCreatedAtValue);
            params.add(cursorCreatedAtValue);
            params.add(cursorId);
        } else if (cursorId != null) {
            baseSql += " and u.id < ?";
            params.add(cursorId);
        } else if (cursorCreatedAtValue != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) < ?";
            params.add(cursorCreatedAtValue);
        }
        String selectSql = """
                select u.id, u.uuid as uid, u.uuid as user_uuid, iu.user_no as userNo, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName,
                       u.avatar_url as avatarUrl, u.email, u.birth_month as birthMonth, u.gender, u.region,
                       u.available_time as availableTime, u.status, iu.source,
                       coalesce(iu.registered_at, u.created_at) as registeredAt,
                       iu.last_login_at as lastLoginAt,
                       u.created_at as createdAt, u.updated_at as updatedAt
                """ + baseSql + """
                order by coalesce(iu.registered_at, u.created_at) desc, u.id desc
                """;
        PageResponse<SystemVO.UserVO> page = cursorMode
                ? cursorQuery(selectSql, SystemVO.UserVO.class, pageSize, params)
                : pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.UserVO.class, pageNo, pageSize, params);
        decorateUsers(page.getRecords());
        maskSensitiveUsers(page.getRecords(), canViewSensitiveUserInfo(currentUser));
        return page;
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:view");
        requireAccessibleUserRecord(currentUser, userId);
        return buildUserDetail(currentUser, userId, false);
    }

    private SystemVO.UserDetailVO buildUserDetail(CurrentUser currentUser, Long userId, boolean bypassSessionAuthentication) {
        if (bypassSessionAuthentication) {
            assertAuthenticatedFromTrustedSnapshot(currentUser);
        } else {
            assertAuthenticated(currentUser);
        }
        SystemVO.UserVO user = queryUser(userId);
        boolean canViewSensitive = canViewSensitiveUserInfo(currentUser);
        if (!canViewSensitive) {
            maskSensitiveUser(user);
        }
        SystemVO.UserDetailVO detail = new SystemVO.UserDetailVO();
        copyUser(detail, user);
        String userUuid = user.getUserUuid();
        CompletableFuture<List<Long>> roleIdsFuture = CompletableFuture.supplyAsync(() -> listUserRoleIds(userId, userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<Long>> deptIdsFuture = CompletableFuture.supplyAsync(() -> listUserDeptIds(userId, userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> roleNamesFuture = CompletableFuture.supplyAsync(() -> listUserRoleNames(userId, userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> deptNamesFuture = CompletableFuture.supplyAsync(() -> listUserDeptNames(userId, userUuid), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<UserDetailVO.UserIdentityVO>> identitiesFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.listIdentities(userId, userUuid).stream()
                        .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                        .toList(),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<List<UserDetailVO.UserDeviceVO>> devicesFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.listRecentDevices(userId, userUuid, 10).stream()
                        .map(device -> toUserDeviceVO(device, canViewSensitive))
                        .toList(),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<UserDetailVO.UserSecuritySettingVO> securitySettingFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.findSecuritySetting(userId, userUuid)
                        .map(this::toUserSecuritySettingVO)
                        .orElse(null),
                BLOCKING_IO_EXECUTOR
        );
        detail.setRoleIds(roleIdsFuture.join());
        List<Long> deptIds = deptIdsFuture.join();
        detail.setDeptIds(deptIds);
        detail.setPrimaryDeptId(deptIds.isEmpty() ? null : deptIds.get(0));
        detail.setRoleNames(roleNamesFuture.join());
        detail.setDeptNames(deptNamesFuture.join());
        detail.setIdentities(identitiesFuture.join());
        detail.setRecentDevices(devicesFuture.join());
        detail.setSecuritySetting(securitySettingFuture.join());
        return detail;
    }

    @Transactional
    public SystemVO.UserDetailVO createUser(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        return createUserAfterAuthentication(currentUser, request, false);
    }

    @Transactional
    public SystemVO.UserDetailVO createUserFromTrustedSnapshot(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        assertAuthenticatedFromTrustedSnapshot(currentUser);
        return createUserAfterAuthentication(currentUser, request, true);
    }

    private SystemVO.UserDetailVO createUserAfterAuthentication(CurrentUser currentUser, SystemDTO.UserUpsertRequest request, boolean bypassSessionAuthentication) {
        if (bypassSessionAuthentication) {
            assertAuthenticatedFromTrustedSnapshot(currentUser);
        } else {
            assertAuthenticated(currentUser);
        }
        requirePermission(currentUser, "system:user:create");
        validateUserUpsertRequest(request);
        validateRoleAssignment(currentUser, request.getRoleIds());
        Long userId = insertOrUpdateUser(null, null, request, currentUser.getUserId(), currentUser.getUserUuid());
        String userUuid = requireUserUuid(userId);
        replaceUserRoles(userId, userUuid, request.getRoleIds(), currentUser.getUserId(), currentUser.getUserUuid());
        replaceUserDepartments(userId, userUuid, request.getDeptIds(), request.getPrimaryDeptId(), currentUser.getUserId(), currentUser.getUserUuid(), true);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "create", "CREATE", "SUCCESS", "创建用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId, bypassSessionAuthentication);
    }

    @Transactional
    public SystemVO.UserDetailVO updateUser(CurrentUser currentUser, Long userId, SystemDTO.UserUpsertRequest request) {
        assertAuthenticated(currentUser);
        validateUserUpsertRequest(request);
        requirePermission(currentUser, "system:user:update");
        validateRoleAssignment(currentUser, request.getRoleIds());
        requireAccessibleUserRecord(currentUser, userId);
        String userUuid = requireUserUuid(userId);
        insertOrUpdateUser(userId, userUuid, request, currentUser.getUserId(), currentUser.getUserUuid());
        replaceUserRoles(userId, userUuid, request.getRoleIds(), currentUser.getUserId(), currentUser.getUserUuid());
        replaceUserDepartments(userId, userUuid, request.getDeptIds(), request.getPrimaryDeptId(), currentUser.getUserId(), currentUser.getUserUuid(), false);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "update", "UPDATE", "SUCCESS", "更新用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId, false);
    }

    @Transactional
    public boolean updateUserStatus(CurrentUser currentUser, Long userId, String status) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:status");
        requireAccessibleUserRecord(currentUser, userId);
        String normalizedStatus = normalizeUserStatus(status);
        if (isProtectedAdminAccount(userId, null) && "DISABLED".equals(normalizedStatus)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default admin account cannot be disabled");
        }
        String userUuid = requireUserUuid(userId);
        int updated = jdbcTemplate.update(
                "update sys_user set status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                normalizedStatus,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                userId,
                userUuid
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "User changed, please retry");
        }
        iamUserService.changeUserStatus(userId, userUuid, normalizedStatus);
        if ("DISABLED".equals(normalizedStatus)) {
            onlineSessionManagementAppService.revokeUserSessions(userId, userUuid);
        }
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "status", "UPDATE", "SUCCESS", "更新用户状态: " + userId + " -> " + normalizedStatus);
        return true;
    }

    @Transactional
    public boolean deleteUser(CurrentUser currentUser, Long userId) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:delete");
        if (currentUser.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Current login user cannot be deleted");
        }
        if (isProtectedAdminAccount(userId, null)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default admin account cannot be deleted");
        }
        requireAccessibleUserRecord(currentUser, userId);
        SystemVO.UserVO user = queryUser(userId);
        String userUuid = user.getUserUuid();
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "User identity UUID is unavailable");
        }
        userUuid = userUuid.trim();
        LocalDateTime now = LocalDateTime.now();

        int deleted = jdbcTemplate.update(
                """
                        update sys_user
                        set username = concat(left(username, 32), '#deleted#', id),
                            status = 'DISABLED',
                            deleted = 1,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        if (deleted <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "User changed, please retry");
        }
        jdbcTemplate.update(
                "update sys_user_role set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        jdbcTemplate.update(
                "update sys_user_department set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        jdbcTemplate.update(
                "update sys_user_passkey_credential set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        jdbcTemplate.update(
                "update sys_verification_binding set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        jdbcTemplate.update(
                "update sys_verification_challenge set consumed_flag = 1, deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        jdbcTemplate.update(
                "update sys_user_wechat_binding set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                now,
                userId,
                userUuid
        );
        iamUserService.softDeleteUser(userId, userUuid);

        onlineSessionManagementAppService.revokeUserSessions(userId, userUuid);
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "delete", "DELETE", "SUCCESS", "删除用户: " + user.getUsername());
        return true;
    }

    public List<SystemVO.RoleVO> listUserRoles(CurrentUser currentUser, Long userId) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:view");
        requireAccessibleUserRecord(currentUser, userId);
        String userUuid = requireUserUuid(userId);
        return jdbcTemplate.query(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by r.id desc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.RoleVO.class),
                userId,
                userUuid
        );
    }

    private SystemVO.UserVO queryUser(Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.uuid as uid, u.uuid as user_uuid, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.birth_month as birthMonth, u.gender, u.region, u.available_time as availableTime,
                               u.status, iu.user_no as userNo, iu.source,
                               coalesce(iu.registered_at, u.created_at) as registeredAt,
                               iu.last_login_at as lastLoginAt,
                               u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        left join iam_user iu on iu.id = u.id and iu.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                userId
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User does not exist");
        }
        return user;
    }

    private Long insertOrUpdateUser(Long userId, String userUuid, SystemDTO.UserUpsertRequest request, Long operatorId, String operatorUuid) {
        String username = normalizeUsername(request.getUsername());
        request.setUsername(username);
        String normalizedStatus = normalizeUserStatus(request.getStatus());
        if (userId != null && isProtectedAdminAccount(userId, username) && "DISABLED".equals(normalizedStatus)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default admin account cannot be disabled");
        }
        ensureUsernameAvailable(username, userId);
        if (userId == null) {
            if (!StringUtils.hasText(request.getPassword())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Initial password must not be blank");
            }
            String password = request.getPassword();
            passwordPolicyService.validatePassword(password);
            try {
                int inserted = jdbcTemplate.update(
                        """
                                insert into sys_user (
                                    uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                    available_time, id_card_number, status,
                                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                """,
                        UserUidGenerator.nextNumericUid(),
                        request.getUsername(),
                        passwordEncoder.encode(password),
                        normalizeNullableText(request.getMobile()),
                        normalizeNullableText(request.getNickname()),
                        normalizeNullableText(request.getRealName()),
                        normalizeNullableText(request.getAvatarUrl()),
                        normalizeNullableText(request.getEmail()),
                        normalizeNullableText(request.getBirthMonth()),
                        normalizeNullableText(request.getGender()),
                        normalizeNullableText(request.getRegion()),
                        normalizeNullableText(request.getAvailableTime()),
                        normalizeNullableText(request.getIdCardNumber()),
                        normalizedStatus,
                        operatorId,
                        operatorUuid,
                        operatorId,
                        operatorUuid
                );
                requireRelationshipWrite(inserted, "User changed, please retry");
            } catch (DuplicateKeyException exception) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
            }
            Long createdUserId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            userDomainService.findById(createdUserId).ifPresent(user -> {
                iamUserService.createUserWithIdentity(user, username, "ADMIN_CREATE");
                iamUserService.recordUserRegistered(user.getId(), user.getUuid(), "ADMIN_CREATE", null, null);
            });
            return createdUserId;
        }
        try {
            int updated = jdbcTemplate.update(
                    """
                            update sys_user
                            set username = ?, mobile = ?, nickname = ?, real_name = ?, avatar_url = ?, email = ?,
                                birth_month = ?, gender = ?, region = ?, available_time = ?, id_card_number = ?, status = ?,
                                updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ? and uuid = ? and deleted = 0
                            """,
                    request.getUsername(),
                    normalizeNullableText(request.getMobile()),
                    normalizeNullableText(request.getNickname()),
                    normalizeNullableText(request.getRealName()),
                    normalizeNullableText(request.getAvatarUrl()),
                    normalizeNullableText(request.getEmail()),
                    normalizeNullableText(request.getBirthMonth()),
                    normalizeNullableText(request.getGender()),
                    normalizeNullableText(request.getRegion()),
                    normalizeNullableText(request.getAvailableTime()),
                    normalizeNullableText(request.getIdCardNumber()),
                    normalizedStatus,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now(),
                    userId,
                    userUuid
            );
            requireRelationshipWrite(updated, "User changed, please retry");
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.validatePassword(request.getPassword());
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            int passwordUpdated = jdbcTemplate.update(
                    "update sys_user set password_hash = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                    encodedPassword,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now(),
                    userId,
                    userUuid
            );
            requireRelationshipWrite(passwordUpdated, "User changed, please retry");
            iamUserService.upsertPasswordCredential(userId, userUuid, encodedPassword);
        }
        userDomainService.findById(userId).ifPresent(iamUserService::updateProfile);
        return userId;
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username must not be blank");
        }
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username may contain only letters, numbers, underscores, and hyphens");
        }
        return normalized;
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        Long existingUserId = queryNullableLong(
                "select id from sys_user where username = ? and deleted = 0 limit 1",
                username
        );
        if (existingUserId != null && !existingUserId.equals(currentUserId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
        Long existingIdentityUserId = queryNullableLong(
                """
                        select user_id
                        from iam_user_identity
                        where identity_type = 'USERNAME'
                          and identifier_normalized = ?
                          and deleted = 0
                        limit 1
                        """,
                iamUserService.normalizeIdentifier(IamUserService.IDENTITY_USERNAME, username)
        );
        if (existingIdentityUserId != null && !existingIdentityUserId.equals(currentUserId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
    }

    private void replaceUserRoles(Long userId, String userUuid, List<Long> roleIds, Long operatorId, String operatorUuid) {
        if (CollectionUtils.isEmpty(roleIds)) {
            jdbcTemplate.update(
                    """
                            update sys_user_role
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where user_id = ? and user_uuid = ? and deleted = 0
                            """,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now(),
                    userId,
                    userUuid
            );
            return;
        }
        if (roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User role id must be a positive integer");
        }
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        Long existingRoleCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_role where deleted = 0 and status = 'ENABLED' and id in (" + placeholders(distinctRoleIds.size()) + ")",
                Long.class,
                distinctRoleIds.toArray()
        );
        if (existingRoleCount == null || existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role does not exist or is disabled");
        }
        jdbcTemplate.update(
                """
                        update sys_user_role
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                userId,
                userUuid
        );
        for (Long roleId : distinctRoleIds) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_user_role (user_id, user_uuid, role_id, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            select ?, ?, r.id, ?, ?, ?, ?, 0
                            from sys_role r
                            where r.id = ? and r.status = 'ENABLED' and r.deleted = 0
                            on duplicate key update
                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then 0 else deleted end,
                                updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then values(updated_by) else updated_by end,
                                updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then values(updated_by_uuid) else updated_by_uuid end,
                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then current_timestamp else updated_at end
                            """,
                    userId,
                    userUuid,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid,
                    roleId
            );
            requireRelationshipWrite(inserted, "Role changed, please retry");
        }
    }

    private void replaceUserDepartments(
            Long userId,
            String userUuid,
            List<Long> deptIds,
            Long primaryDeptId,
            Long operatorId,
            String operatorUuid,
            boolean createMode
    ) {
        if (deptIds == null && !createMode) {
            return;
        }
        jdbcTemplate.update(
                """
                        update sys_user_department
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                userId,
                userUuid
        );
        if (CollectionUtils.isEmpty(deptIds)) {
            return;
        }
        if (deptIds.stream().anyMatch(deptId -> deptId == null || deptId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User department id must be a positive integer");
        }
        List<Long> distinctDeptIds = new ArrayList<>(new LinkedHashSet<>(deptIds));
        Long existingDeptCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_department where deleted = 0 and status = 'ENABLED' and id in (" + placeholders(distinctDeptIds.size()) + ")",
                Long.class,
                distinctDeptIds.toArray()
        );
        if (existingDeptCount == null || existingDeptCount != distinctDeptIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Department does not exist or is disabled");
        }
        Long effectivePrimaryDeptId = primaryDeptId != null && distinctDeptIds.contains(primaryDeptId)
                ? primaryDeptId
                : distinctDeptIds.get(0);
        for (Long deptId : distinctDeptIds) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into sys_user_department (user_id, user_uuid, dept_id, primary_flag, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            select ?, ?, d.id, ?, ?, ?, ?, ?, 0
                            from sys_department d
                            where d.id = ? and d.status = 'ENABLED' and d.deleted = 0
                            on duplicate key update
                                primary_flag = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then values(primary_flag) else primary_flag end,
                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then 0 else deleted end,
                                updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then values(updated_by) else updated_by end,
                                updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then values(updated_by_uuid) else updated_by_uuid end,
                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) and dept_id = values(dept_id) then current_timestamp else updated_at end
                            """,
                    userId,
                    userUuid,
                    deptId.equals(effectivePrimaryDeptId) ? 1 : 0,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid,
                    deptId
            );
            requireRelationshipWrite(inserted, "Department changed, please retry");
        }
    }

    private void requireRelationshipWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
    }

    private void decorateUsers(List<SystemVO.UserVO> users) {
        List<Long> userIds = users.stream()
                .map(SystemVO.UserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, List<String>> roleNames = listUserRoleNames(userIds);
        Map<Long, List<String>> deptNames = listUserDeptNames(userIds);
        Map<Long, String> userUuids = listUserUuids(userIds);
        users.forEach(user -> {
            String userUuid = userUuids.get(user.getId());
            if (StringUtils.hasText(userUuid)) {
                user.setUid(userUuid);
                user.setUserUuid(userUuid);
            }
            user.setRoleNames(roleNames.getOrDefault(user.getId(), List.of()));
            user.setDeptNames(deptNames.getOrDefault(user.getId(), List.of()));
        });
    }

    private Map<Long, String> listUserUuids(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        return jdbcTemplate.query(
                """
                        select id, uuid
                        from sys_user
                        where deleted = 0
                          and id in (%s)
                        """.formatted(placeholders(userIds.size())),
                (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("uuid")),
                userIds.toArray()
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
    }

    private String requireUserUuid(Long userId) {
        String userUuid;
        try {
            userUuid = jdbcTemplate.queryForObject(
                    "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                    String.class,
                    userId
            );
        } catch (EmptyResultDataAccessException exception) {
            userUuid = null;
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "User identity UUID is unavailable");
        }
        return userUuid.trim();
    }

    private void decorateIamUserDetail(SystemVO.UserDetailVO detail, Long userId, boolean canViewSensitive) {
        String userUuid = requireUserUuid(userId);
        detail.setIdentities(iamUserService.listIdentities(userId, userUuid).stream()
                .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                .toList());
        detail.setRecentDevices(iamUserService.listRecentDevices(userId, userUuid, 10).stream()
                .map(device -> toUserDeviceVO(device, canViewSensitive))
                .toList());
        detail.setSecuritySetting(iamUserService.findSecuritySetting(userId, userUuid)
                .map(this::toUserSecuritySettingVO)
                .orElse(null));
    }

    private List<Long> listUserRoleIds(Long userId, String userUuid) {
        return jdbcTemplate.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId,
                userUuid
        );
    }

    private List<Long> listUserDeptIds(Long userId, String userUuid) {
        return jdbcTemplate.queryForList(
                """
                        select ud.dept_id
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.user_uuid = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, ud.dept_id asc
                        """,
                Long.class,
                userId,
                userUuid
        );
    }

    private List<String> listUserRoleNames(Long userId, String userUuid) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                userUuid
        );
    }

    private List<String> listUserDeptNames(Long userId, String userUuid) {
        return jdbcTemplate.queryForList(
                """
                        select d.dept_name
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.user_uuid = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, d.sort_no asc, d.id asc
                        """,
                String.class,
                userId,
                userUuid
        );
    }

    private Map<Long, List<String>> listUserRoleNames(List<Long> userIds) {
        String placeholders = placeholders(userIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(userIds);
        return jdbcTemplate.query(
                """
                        select ur.user_id as userId, r.role_name as roleName
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id in (%s)
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        order by ur.user_id asc, r.id asc
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, List<String>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getLong("userId"), ignored -> new ArrayList<>())
                                .add(rs.getString("roleName"));
                    }
                    return result;
                },
                params.toArray()
        );
    }

    private Map<Long, List<String>> listUserDeptNames(List<Long> userIds) {
        String placeholders = placeholders(userIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(userIds);
        return jdbcTemplate.query(
                """
                        select ud.user_id as userId, d.dept_name as deptName
                        from sys_user_department ud
                        join sys_user u
                          on u.id = ud.user_id
                         and u.uuid = ud.user_uuid
                         and u.deleted = 0
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id in (%s)
                          and ud.user_uuid is not null
                          and trim(ud.user_uuid) <> ''
                          and ud.deleted = 0
                        order by ud.user_id asc, ud.primary_flag desc, d.sort_no asc, d.id asc
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, List<String>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getLong("userId"), ignored -> new ArrayList<>())
                                .add(rs.getString("deptName"));
                    }
                    return result;
                },
                params.toArray()
        );
    }

    private boolean canViewSensitiveUserInfo(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains("system:user:sensitive:view"));
    }

    private void maskSensitiveUsers(List<SystemVO.UserVO> users, boolean canViewSensitive) {
        if (canViewSensitive || users == null || users.isEmpty()) {
            return;
        }
        users.forEach(this::maskSensitiveUser);
    }

    private void maskSensitiveUser(SystemVO.UserVO user) {
        if (user == null) {
            return;
        }
        user.setMobile(maskMobile(user.getMobile()));
        user.setEmail(maskEmail(user.getEmail()));
        user.setIdCardNumber(maskIdCard(user.getIdCardNumber()));
    }

    private UserDetailVO.UserIdentityVO toUserIdentityVO(IamUserAccount.IdentityView identity, boolean canViewSensitive) {
        UserDetailVO.UserIdentityVO vo = new UserDetailVO.UserIdentityVO();
        vo.setId(identity.getId());
        vo.setIdentityType(identity.getIdentityType());
        vo.setIdentifier(canViewSensitive ? identity.getIdentifier() : maskIdentity(identity.getIdentityType(), identity.getIdentifier()));
        vo.setVerified(identity.getVerified());
        vo.setPrimaryIdentity(identity.getPrimaryIdentity());
        vo.setStatus(identity.getStatus());
        return vo;
    }

    private UserDetailVO.UserDeviceVO toUserDeviceVO(IamUserAccount.DeviceView device, boolean canViewSensitive) {
        UserDetailVO.UserDeviceVO vo = new UserDetailVO.UserDeviceVO();
        vo.setId(device.getId());
        vo.setDeviceId(canViewSensitive ? device.getDeviceId() : maskDeviceId(device.getDeviceId()));
        vo.setDeviceName(device.getDeviceName());
        vo.setDeviceType(device.getDeviceType());
        vo.setOs(device.getOs());
        vo.setBrowser(device.getBrowser());
        vo.setLastIp(canViewSensitive ? device.getLastIp() : maskIp(device.getLastIp()));
        vo.setLastActiveAt(device.getLastActiveAt());
        vo.setTrusted(device.getTrusted());
        return vo;
    }

    private UserDetailVO.UserSecuritySettingVO toUserSecuritySettingVO(IamUserAccount.SecuritySettingView setting) {
        UserDetailVO.UserSecuritySettingVO vo = new UserDetailVO.UserSecuritySettingVO();
        vo.setMfaEnabled(setting.getMfaEnabled());
        vo.setPasswordLoginEnabled(setting.getPasswordLoginEnabled());
        vo.setSmsLoginEnabled(setting.getSmsLoginEnabled());
        vo.setEmailLoginEnabled(setting.getEmailLoginEnabled());
        vo.setPasskeyEnabled(setting.getPasskeyEnabled());
        vo.setLoginNotifyEnabled(setting.getLoginNotifyEnabled());
        return vo;
    }

    private String maskIdentity(String identityType, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return identifier;
        }
        String type = identityType == null ? "" : identityType.toUpperCase(Locale.ROOT);
        if (IamUserService.IDENTITY_MOBILE.equals(type)) {
            return maskMobile(identifier);
        }
        if (IamUserService.IDENTITY_EMAIL.equals(type)) {
            return maskEmail(identifier);
        }
        if (IamUserService.IDENTITY_USERNAME.equals(type)) {
            return maskUsername(identifier);
        }
        return maskLongIdentifier(identifier);
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskIdCard(String idCardNumber) {
        if (!StringUtils.hasText(idCardNumber) || idCardNumber.length() < 8) {
            return idCardNumber;
        }
        return idCardNumber.substring(0, 4) + "********" + idCardNumber.substring(idCardNumber.length() - 4);
    }

    private String maskUsername(String username) {
        if (!StringUtils.hasText(username) || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private String maskLongIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier) || identifier.length() <= 8) {
            return "***";
        }
        return identifier.substring(0, 4) + "****" + identifier.substring(identifier.length() - 4);
    }

    private String maskDeviceId(String deviceId) {
        return maskLongIdentifier(deviceId);
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        if (ip.contains(".")) {
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot + 1) + "*" : "*";
        }
        if (ip.contains(":")) {
            int idx = ip.indexOf(':');
            return idx > 0 ? ip.substring(0, idx) + ":****" : "****";
        }
        return "*";
    }

    private boolean isProtectedAdminAccount(Long userId, String username) {
        return DEFAULT_ADMIN_USER_ID.equals(userId);
    }

    private String normalizeUserStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User status must not be blank");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户状态只能是 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = value == null ? "" : value.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private LocalDateTime parseDateTimeParam(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                LocalDate date = LocalDate.parse(normalized);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(normalized.replace(" ", "T"));
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "时间格式不正确，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        String pagedSql = selectSql + " limit ? offset ?";
        List<T> records = jdbcTemplate.query(pagedSql, new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private <T> PageResponse<T> cursorQuery(String selectSql, Class<T> voClass, long pageSize, List<Object> params) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize + 1);
        List<T> records = jdbcTemplate.query(selectSql + " limit ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        boolean hasMore = records.size() > safePageSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, (int) safePageSize));
        }
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(-1);
        response.setPageNo(1);
        response.setPageSize(safePageSize);
        response.setHasMore(hasMore);
        if (!records.isEmpty() && records.get(records.size() - 1) instanceof SystemVO.UserVO user) {
            response.setNextCursorId(user.getId());
            response.setNextCursorCreatedAt(user.getRegisteredAt() == null ? null : user.getRegisteredAt().toString());
        }
        return response;
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private Set<Long> queryDepartmentAndDescendantIds(Long deptId) {
        boolean rootExists = existsByQuery(
                "select 1 from sys_department where id = ? and deleted = 0 limit 1",
                deptId
        );
        if (!rootExists) {
            return Set.of();
        }

        Set<Long> result = new LinkedHashSet<>();
        result.add(deptId);
        Set<Long> frontier = new LinkedHashSet<>();
        frontier.add(deptId);
        while (!frontier.isEmpty()) {
            List<Object> params = new ArrayList<>();
            params.addAll(frontier);
            List<Long> children = jdbcTemplate.queryForList(
                    "select id from sys_department where deleted = 0 and parent_id in (" + placeholders(frontier.size()) + ")",
                    Long.class,
                    params.toArray()
            );
            frontier = new LinkedHashSet<>();
            for (Long childId : children) {
                if (childId != null && result.add(childId)) {
                    frontier.add(childId);
                }
            }
        }
        return result;
    }

    private void assertAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser, false);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private void assertAuthenticatedFromTrustedSnapshot(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser, true);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser, false);
    }

    private boolean shouldBypassSessionAuthentication(CurrentUser currentUser, boolean bypassSessionAuthentication) {
        return bypassSessionAuthentication || isAsyncExportSession(currentUser);
    }

    private boolean isAsyncExportSession(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.getSessionId() != null
                && currentUser.getSessionId().startsWith(ASYNC_EXPORT_SESSION_PREFIX);
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser, boolean bypassSessionAuthentication) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (!shouldBypassSessionAuthentication(currentUser, bypassSessionAuthentication) && sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(currentUsername);
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private void validateUserUpsertRequest(SystemDTO.UserUpsertRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User request is required");
        }
    }

    private void requirePermission(CurrentUser currentUser, String permission) {
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || (!permissions.contains("*") && !permissions.contains(permission))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private void validateRoleAssignment(CurrentUser currentUser, List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        if (roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Role id must be positive");
        }
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        Long existingRoleCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_role where deleted = 0 and id in (" + placeholders(distinctRoleIds.size()) + ")",
                Long.class,
                distinctRoleIds.toArray()
        );
        if (existingRoleCount == null || existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role not found");
        }
        if (currentUser.getPermissions() != null && currentUser.getPermissions().contains("*")) {
            return;
        }
        Long privilegedPermissionCount = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_role_permission rp
                        join sys_permission p on p.id = rp.permission_id and p.deleted = 0
                        where rp.deleted = 0
                          and rp.role_id in (%s)
                          and p.perm_code = '*'
                        """.formatted(placeholders(distinctRoleIds.size())),
                Long.class,
                distinctRoleIds.toArray()
        );
        if (privilegedPermissionCount != null && privilegedPermissionCount > 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "Privileged role assignment denied");
        }
    }

    private void requireAccessibleUserRecord(CurrentUser currentUser, Long userId) {
        if (!canAccessUserRecord(currentUser, userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "User does not exist");
        }
    }

    private boolean canAccessUserRecord(CurrentUser currentUser, Long userId) {
        if (userId == null) {
            return false;
        }
        DataPermissionSql dataPermissionSql = userDataPermissionClause(currentUser, "u");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(dataPermissionSql.params());
        return existsByQuery(
                """
                        select 1
                        from sys_user u
                        where u.deleted = 0
                          and u.id = ?
                        """ + dataPermissionSql.sql() + " limit 1",
                params.toArray()
        );
    }

    private DataPermissionSql userDataPermissionClause(CurrentUser currentUser, String userAlias) {
        PermissionSnapshotService.PermissionSnapshot snapshot = resolvePermissionSnapshot(currentUser);
        if (snapshot == null) {
            snapshot = PermissionSnapshotService.PermissionSnapshot.empty();
        }
        Set<String> permissions = snapshot.getPermissions() == null ? Set.of() : snapshot.getPermissions();
        Set<Long> deptIdsFromSnapshot = snapshot.getDeptIds() == null ? Set.of() : snapshot.getDeptIds();
        Set<Long> descendantDeptIdsFromSnapshot = snapshot.getDescendantDeptIds() == null ? Set.of() : snapshot.getDescendantDeptIds();
        List<DataPermissionRule> dataScopes = snapshot.getDataScopes() == null ? List.of() : snapshot.getDataScopes();
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                RESOURCE_SYSTEM_USER,
                currentUser.getUserId(),
                deptIdsFromSnapshot,
                descendantDeptIdsFromSnapshot,
                dataScopes,
                permissions
        );
        if (decision.scopeType() == DataScopeType.ALL) {
            return DataPermissionSql.empty();
        }
        Set<Long> deptIds = new LinkedHashSet<>(decision.deptIds());
        Set<Long> userIds = new LinkedHashSet<>(decision.userIds());
        if (decision.hasDeptRestriction() && currentUser.getUserId() != null) {
            userIds.add(currentUser.getUserId());
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) {
            userIds.add(currentUser.getUserId());
        }

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!deptIds.isEmpty()) {
            conditions.add("""
                    exists (
                        select 1
                        from sys_user_department sud
                        where sud.user_id = %s.id
                          and sud.dept_id in (%s)
                          and sud.deleted = 0
                    )
                    """.formatted(userAlias, placeholders(deptIds.size())));
            params.addAll(deptIds);
        }
        if (!userIds.isEmpty()) {
            conditions.add("%s.id in (%s)".formatted(userAlias, placeholders(userIds.size())));
            params.addAll(userIds);
        }
        return new DataPermissionSql(" and (" + String.join(" or ", conditions) + ")", params);
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(CurrentUser currentUser) {
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser == null ? null : currentUser.getSimulatedRoleId());
        if (currentUser != null) {
            currentUser.setSimulatedRoleId(simulatedRoleId);
        }
        if (currentUser == null || simulatedRoleId != null) {
            if (currentUser == null || simulatedRoleId == null || currentUser.getUserId() == null) {
                return PermissionSnapshotService.PermissionSnapshot.empty();
            }
            return permissionSnapshotService.loadGrantedRoleSnapshot(
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    simulatedRoleId
            );
        }
        if (!org.springframework.util.StringUtils.hasText(currentUser.getPermissionsVersion())) {
            if (!org.springframework.util.StringUtils.hasText(currentUser.getUserUuid())) {
                return PermissionSnapshotService.PermissionSnapshot.empty();
            }
            return permissionSnapshotService.loadSnapshot(currentUser.getUserId(), currentUser.getUserUuid());
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        return new PermissionSnapshotService.PermissionSnapshot(
                currentUser.getPermissionsVersion(),
                currentUser.getPermissions(),
                currentUser.getRoleIds(),
                currentUser.getPrimaryDeptId(),
                currentUser.getDeptIds(),
                currentUser.getDescendantDeptIds(),
                currentUser.getDataScopes(),
                currentUser.getDefaultHomePath()
        );
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private <T> T queryOne(String sql, Class<T> voClass, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Long queryNullableLong(String sql, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private boolean existsByQuery(String sql, Object... params) {
        return jdbcTemplate.exists(sql, params);
    }

    private void copyUser(SystemVO.UserDetailVO target, SystemVO.UserVO source) {
        target.setId(source.getId());
        target.setUid(source.getUid());
        target.setUserUuid(source.getUserUuid());
        target.setUserNo(source.getUserNo());
        target.setUsername(source.getUsername());
        target.setMobile(source.getMobile());
        target.setIdCardNumber(source.getIdCardNumber());
        target.setNickname(source.getNickname());
        target.setRealName(source.getRealName());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setEmail(source.getEmail());
        target.setBirthMonth(source.getBirthMonth());
        target.setGender(source.getGender());
        target.setRegion(source.getRegion());
        target.setAvailableTime(source.getAvailableTime());
        target.setStatus(source.getStatus());
        target.setSource(source.getSource());
        target.setRegisteredAt(source.getRegisteredAt());
        target.setLastLoginAt(source.getLastLoginAt());
        target.setRoleNames(source.getRoleNames());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private record DataPermissionSql(String sql, List<Object> params) {
        static DataPermissionSql empty() {
            return new DataPermissionSql("", List.of());
        }
    }
}
