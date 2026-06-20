package com.lumira.saas.modules.system.user.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.vo.UserDetailVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
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

@Service
public class SystemUserManagementAppService {

    private static final Long DEFAULT_PUBLIC_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String RESOURCE_SYSTEM_USER = "system:user";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final MyBatisQueryOperations jdbcTemplate;
    private final UserDomainService userDomainService;
    private final IamUserService iamUserService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final OperationAuditService operationAuditService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;

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
        this.jdbcTemplate = jdbcTemplate;
        this.userDomainService = userDomainService;
        this.iamUserService = iamUserService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.operationAuditService = operationAuditService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
    }

    public PageResponse<SystemVO.UserVO> listUsers(
            CurrentUser currentUser,
            Long userId,
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
        Long tenantId = currentTenantId(currentUser);
        String baseSql = """
                from sys_user u
                left join iam_user iu
                  on iu.id = u.id
                 and iu.deleted = 0
                join sys_user_tenant ut
                  on ut.user_id = u.id
                 and ut.tenant_id = ?
                 and ut.deleted = 0
                 and ut.status = 'ENABLED'
                where u.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (userId != null) {
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
            Set<Long> visibleDeptIds = queryDepartmentAndDescendantIds(tenantId, deptId);
            if (visibleDeptIds.isEmpty()) {
                baseSql += " and 1 = 0";
            } else {
                baseSql += """
                         and exists (
                             select 1
                             from sys_user_department ud_filter
                             where ud_filter.tenant_id = ?
                               and ud_filter.user_id = u.id
                               and ud_filter.dept_id in (%s)
                               and ud_filter.deleted = 0
                         )
                        """.formatted(placeholders(visibleDeptIds.size()));
                params.add(tenantId);
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
                select u.id, iu.user_no as userNo, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName,
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
        decorateUsers(page.getRecords(), tenantId);
        maskSensitiveUsers(page.getRecords(), canViewSensitiveUserInfo(currentUser));
        return page;
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        requireAccessibleUserRecord(currentUser, userId);
        return buildUserDetail(currentUser, userId);
    }

    private SystemVO.UserDetailVO buildUserDetail(CurrentUser currentUser, Long userId) {
        SystemVO.UserVO user = queryUser(currentTenantId(currentUser), userId);
        boolean canViewSensitive = canViewSensitiveUserInfo(currentUser);
        if (!canViewSensitive) {
            maskSensitiveUser(user);
        }
        SystemVO.UserDetailVO detail = new SystemVO.UserDetailVO();
        copyUser(detail, user);
        Long tenantId = currentTenantId(currentUser);
        CompletableFuture<List<Long>> roleIdsFuture = CompletableFuture.supplyAsync(() -> listUserRoleIds(userId, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<Long>> deptIdsFuture = CompletableFuture.supplyAsync(() -> listUserDeptIds(userId, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<Long>> tenantIdsFuture = CompletableFuture.supplyAsync(() -> listUserTenantIds(userId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> tenantNamesFuture = CompletableFuture.supplyAsync(() -> listUserTenantNames(userId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> roleNamesFuture = CompletableFuture.supplyAsync(() -> listUserRoleNames(userId, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<String>> deptNamesFuture = CompletableFuture.supplyAsync(() -> listUserDeptNames(userId, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<List<UserDetailVO.UserIdentityVO>> identitiesFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.listIdentities(userId).stream()
                        .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                        .toList(),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<List<UserDetailVO.UserDeviceVO>> devicesFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.listRecentDevices(userId, 10).stream()
                        .map(device -> toUserDeviceVO(device, canViewSensitive))
                        .toList(),
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<UserDetailVO.UserSecuritySettingVO> securitySettingFuture = CompletableFuture.supplyAsync(
                () -> iamUserService.findSecuritySetting(userId)
                        .map(this::toUserSecuritySettingVO)
                        .orElse(null),
                BLOCKING_IO_EXECUTOR
        );
        detail.setRoleIds(roleIdsFuture.join());
        List<Long> deptIds = deptIdsFuture.join();
        detail.setDeptIds(deptIds);
        detail.setPrimaryDeptId(deptIds.isEmpty() ? null : deptIds.get(0));
        detail.setTenantIds(tenantIdsFuture.join());
        detail.setTenantNames(tenantNamesFuture.join());
        detail.setRoleNames(roleNamesFuture.join());
        detail.setDeptNames(deptNamesFuture.join());
        detail.setIdentities(identitiesFuture.join());
        detail.setRecentDevices(devicesFuture.join());
        detail.setSecuritySetting(securitySettingFuture.join());
        return detail;
    }

    @Transactional
    public SystemVO.UserDetailVO createUser(CurrentUser currentUser, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long userId = insertOrUpdateUser(null, request, currentUser.getUserId());
        upsertUserTenantRelation(userId, tenantId, true, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        replaceUserDepartments(userId, tenantId, request.getDeptIds(), request.getPrimaryDeptId(), currentUser.getUserId(), true);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "create", "CREATE", "SUCCESS", "创建用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId);
    }

    @Transactional
    public SystemVO.UserDetailVO updateUser(CurrentUser currentUser, Long userId, SystemDTO.UserUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requireAccessibleUserRecord(currentUser, userId);
        insertOrUpdateUser(userId, request, currentUser.getUserId());
        replaceUserRoles(userId, tenantId, request.getRoleIds(), currentUser.getUserId());
        replaceUserDepartments(userId, tenantId, request.getDeptIds(), request.getPrimaryDeptId(), currentUser.getUserId(), false);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "update", "UPDATE", "SUCCESS", "更新用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId);
    }

    @Transactional
    public boolean updateUserStatus(CurrentUser currentUser, Long userId, String status) {
        requireAccessibleUserRecord(currentUser, userId);
        String normalizedStatus = normalizeUserStatus(status);
        if (isProtectedAdminAccount(userId, null) && "DISABLED".equals(normalizedStatus)) {
            throw new BizException(ErrorCode.FORBIDDEN, "默认管理员账户不允许被禁用");
        }
        jdbcTemplate.update(
                "update sys_user set status = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                normalizedStatus,
                currentUser.getUserId(),
                LocalDateTime.now(),
                userId
        );
        iamUserService.changeUserStatus(userId, normalizedStatus);
        if ("DISABLED".equals(normalizedStatus)) {
            onlineSessionManagementAppService.revokeUserSessions(userId);
        }
        operationAuditService.log(currentTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "user", "status", "UPDATE", "SUCCESS", "更新用户状态: " + userId + " -> " + normalizedStatus);
        return true;
    }

    @Transactional
    public boolean deleteUser(CurrentUser currentUser, Long userId) {
        if (currentUser.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能删除当前登录用户");
        }
        if (isProtectedAdminAccount(userId, null)) {
            throw new BizException(ErrorCode.FORBIDDEN, "默认管理员账户不允许被删除");
        }
        Long tenantId = currentTenantId(currentUser);
        requireAccessibleUserRecord(currentUser, userId);
        SystemVO.UserVO user = queryUser(tenantId, userId);
        LocalDateTime now = LocalDateTime.now();

        int tenantRows = jdbcTemplate.update(
                """
                        update sys_user_tenant
                        set status = 'DISABLED', deleted = 1, updated_by = ?, updated_at = ?
                        where tenant_id = ? and user_id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );
        if (tenantRows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        jdbcTemplate.update(
                "update sys_user_role set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );
        jdbcTemplate.update(
                "update sys_user_department set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );
        jdbcTemplate.update(
                "update sys_user_passkey_credential set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );
        jdbcTemplate.update(
                "update sys_verification_binding set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );
        jdbcTemplate.update(
                "update sys_verification_challenge set consumed_flag = 1, deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and user_id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                userId
        );

        boolean activeTenantExists = existsByQuery(
                "select 1 from sys_user_tenant where user_id = ? and deleted = 0 and status = 'ENABLED' limit 1",
                userId
        );
        if (!activeTenantExists) {
            jdbcTemplate.update(
                    """
                            update sys_user
                            set username = concat(left(username, 32), '#deleted#', id),
                                status = 'DISABLED',
                                deleted = 1,
                                updated_by = ?,
                                updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    currentUser.getUserId(),
                    now,
                    userId
            );
            jdbcTemplate.update(
                    "update sys_user_wechat_binding set deleted = 1, updated_by = ?, updated_at = ? where user_id = ? and deleted = 0",
                    currentUser.getUserId(),
                    now,
                    userId
            );
            iamUserService.softDeleteUser(userId);
        }

        onlineSessionManagementAppService.revokeUserSessions(userId);
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "user", "delete", "DELETE", "SUCCESS", "删除用户: " + user.getUsername());
        return true;
    }

    public List<SystemVO.RoleVO> listUserRoles(CurrentUser currentUser, Long userId) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select r.id, r.tenant_id as tenantId, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.tenant_id = ? and ur.user_id = ? and ur.deleted = 0
                        order by r.id desc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.RoleVO.class),
                tenantId,
                userId
        );
    }

    private SystemVO.UserVO queryUser(Long tenantId, Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.birth_month as birthMonth, u.gender, u.region, u.available_time as availableTime,
                               u.status, iu.user_no as userNo, iu.source,
                               coalesce(iu.registered_at, u.created_at) as registeredAt,
                               iu.last_login_at as lastLoginAt,
                               u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        left join iam_user iu on iu.id = u.id and iu.deleted = 0
                        join sys_user_tenant ut on ut.user_id = u.id and ut.tenant_id = ? and ut.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                tenantId,
                userId
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private Long insertOrUpdateUser(Long userId, SystemDTO.UserUpsertRequest request, Long operatorId) {
        String username = normalizeUsername(request.getUsername());
        request.setUsername(username);
        String normalizedStatus = normalizeUserStatus(request.getStatus());
        if (userId != null && isProtectedAdminAccount(userId, username) && "DISABLED".equals(normalizedStatus)) {
            throw new BizException(ErrorCode.FORBIDDEN, "默认管理员账户不允许被禁用");
        }
        ensureUsernameAvailable(username, userId);
        if (userId == null) {
            if (!StringUtils.hasText(request.getPassword())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "初始密码不能为空");
            }
            String password = request.getPassword();
            passwordPolicyService.validatePassword(password);
            try {
                jdbcTemplate.update(
                        """
                                insert into sys_user (
                                    username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                    available_time, id_card_number, status,
                                    created_by, updated_by, deleted
                                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                """,
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
                        operatorId
                );
            } catch (DuplicateKeyException exception) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名已存在，请更换后重试");
            }
            Long createdUserId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            userDomainService.findById(createdUserId).ifPresent(user -> {
                iamUserService.createUserWithIdentity(user, username, "ADMIN_CREATE");
                iamUserService.recordUserRegistered(user.getId(), "ADMIN_CREATE", null, null);
            });
            return createdUserId;
        }
        try {
            jdbcTemplate.update(
                    """
                            update sys_user
                            set username = ?, mobile = ?, nickname = ?, real_name = ?, avatar_url = ?, email = ?,
                                birth_month = ?, gender = ?, region = ?, available_time = ?, id_card_number = ?, status = ?,
                                updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
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
                    LocalDateTime.now(),
                    userId
            );
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名已存在，请更换后重试");
        }
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.validatePassword(request.getPassword());
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            jdbcTemplate.update(
                    "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    encodedPassword,
                    operatorId,
                    LocalDateTime.now(),
                    userId
            );
            iamUserService.upsertPasswordCredential(userId, encodedPassword);
        }
        userDomainService.findById(userId).ifPresent(iamUserService::updateProfile);
        return userId;
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名只能包含英文字母、数字、下划线和连字符");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名已存在，请更换后重试");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名已存在，请更换后重试", "用户名已存在，请更换后重试");
        }
    }

    private void upsertUserTenantRelation(Long userId, Long tenantId, boolean isDefault, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, ?, 'ENABLED', ?, ?, 0)
                        on duplicate key update is_default = values(is_default), status = values(status),
                                                 updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                isDefault ? 1 : 0,
                operatorId,
                operatorId
        );
    }

    private void replaceUserRoles(Long userId, Long tenantId, List<Long> roleIds, Long operatorId) {
        jdbcTemplate.update(
                "delete from sys_user_role where tenant_id = ? and user_id = ?",
                tenantId,
                userId
        );
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        if (roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户角色ID必须为正整数");
        }
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        Long existingRoleCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_role where tenant_id = ? and deleted = 0 and id in (" + placeholders(distinctRoleIds.size()) + ")",
                Long.class,
                buildTenantAndIdsParams(tenantId, distinctRoleIds)
        );
        if (existingRoleCount == null || existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        for (Long roleId : distinctRoleIds) {
            jdbcTemplate.update(
                    """
                            insert into sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    userId,
                    roleId,
                    operatorId,
                    operatorId
            );
        }
    }

    private void replaceUserDepartments(
            Long userId,
            Long tenantId,
            List<Long> deptIds,
            Long primaryDeptId,
            Long operatorId,
            boolean createMode
    ) {
        if (deptIds == null && !createMode) {
            return;
        }
        jdbcTemplate.update(
                "delete from sys_user_department where tenant_id = ? and user_id = ?",
                tenantId,
                userId
        );
        if (CollectionUtils.isEmpty(deptIds)) {
            return;
        }
        if (deptIds.stream().anyMatch(deptId -> deptId == null || deptId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户部门ID必须为正整数");
        }
        List<Long> distinctDeptIds = new ArrayList<>(new LinkedHashSet<>(deptIds));
        Long existingDeptCount = jdbcTemplate.queryForObject(
                "select count(1) from sys_department where tenant_id = ? and deleted = 0 and status = 'ENABLED' and id in (" + placeholders(distinctDeptIds.size()) + ")",
                Long.class,
                buildTenantAndIdsParams(tenantId, distinctDeptIds)
        );
        if (existingDeptCount == null || existingDeptCount != distinctDeptIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在或已停用");
        }
        Long effectivePrimaryDeptId = primaryDeptId != null && distinctDeptIds.contains(primaryDeptId)
                ? primaryDeptId
                : distinctDeptIds.get(0);
        for (Long deptId : distinctDeptIds) {
            jdbcTemplate.update(
                    """
                            insert into sys_user_department (tenant_id, user_id, dept_id, primary_flag, created_by, updated_by, deleted)
                            values (?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId,
                    userId,
                    deptId,
                    deptId.equals(effectivePrimaryDeptId) ? 1 : 0,
                    operatorId,
                    operatorId
            );
        }
    }

    private void decorateUsers(List<SystemVO.UserVO> users, Long tenantId) {
        List<Long> userIds = users.stream()
                .map(SystemVO.UserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, List<String>> roleNames = listUserRoleNames(userIds, tenantId);
        Map<Long, List<String>> deptNames = listUserDeptNames(userIds, tenantId);
        users.forEach(user -> {
            user.setTenantNames(List.of());
            user.setRoleNames(roleNames.getOrDefault(user.getId(), List.of()));
            user.setDeptNames(deptNames.getOrDefault(user.getId(), List.of()));
        });
    }

    private void decorateIamUserDetail(SystemVO.UserDetailVO detail, Long userId, boolean canViewSensitive) {
        detail.setIdentities(iamUserService.listIdentities(userId).stream()
                .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                .toList());
        detail.setRecentDevices(iamUserService.listRecentDevices(userId, 10).stream()
                .map(device -> toUserDeviceVO(device, canViewSensitive))
                .toList());
        detail.setSecuritySetting(iamUserService.findSecuritySetting(userId)
                .map(this::toUserSecuritySettingVO)
                .orElse(null));
    }

    private List<String> listUserTenantNames(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select coalesce(
                            (
                                select nullif(c.config_value, '')
                                from sys_config c
                                where c.tenant_id = ut.tenant_id
                                  and c.config_key = 'platform.name'
                                  and c.deleted = 0
                                order by c.id asc
                                limit 1
                            ),
                            concat('租户 ', ut.tenant_id)
                        ) as tenant_name
                        from sys_user_tenant ut
                        where ut.user_id = ?
                          and ut.deleted = 0
                        order by ut.is_default desc, ut.tenant_id asc
                        """,
                String.class,
                userId
        );
    }

    private List<Long> listUserTenantIds(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        select tenant_id
                        from sys_user_tenant
                        where user_id = ?
                          and deleted = 0
                        order by is_default desc, tenant_id asc
                        """,
                Long.class,
                userId
        );
    }

    private List<Long> listUserRoleIds(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId,
                tenantId
        );
    }

    private List<Long> listUserDeptIds(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select ud.dept_id
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.tenant_id = ud.tenant_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.tenant_id = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, ud.dept_id asc
                        """,
                Long.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserRoleNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id = ? and ur.tenant_id = ? and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private List<String> listUserDeptNames(Long userId, Long tenantId) {
        return jdbcTemplate.queryForList(
                """
                        select d.dept_name
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.tenant_id = ud.tenant_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.tenant_id = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, d.sort_no asc, d.id asc
                        """,
                String.class,
                userId,
                tenantId
        );
    }

    private Map<Long, List<String>> listUserRoleNames(List<Long> userIds, Long tenantId) {
        String placeholders = placeholders(userIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(userIds);
        params.add(tenantId);
        return jdbcTemplate.query(
                """
                        select ur.user_id as userId, r.role_name as roleName
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        where ur.user_id in (%s) and ur.tenant_id = ? and ur.deleted = 0
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

    private Map<Long, List<String>> listUserDeptNames(List<Long> userIds, Long tenantId) {
        String placeholders = placeholders(userIds.size());
        List<Object> params = new ArrayList<>();
        params.addAll(userIds);
        params.add(tenantId);
        return jdbcTemplate.query(
                """
                        select ud.user_id as userId, d.dept_name as deptName
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.tenant_id = ud.tenant_id
                         and d.deleted = 0
                        where ud.user_id in (%s)
                          and ud.tenant_id = ?
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
        return DEFAULT_ADMIN_USER_ID.equals(userId)
                || (StringUtils.hasText(username) && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username));
    }

    private String normalizeUserStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户状态不能为空");
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
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "时间参数格式不正确，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
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

    private Set<Long> queryDepartmentAndDescendantIds(Long tenantId, Long deptId) {
        boolean rootExists = existsByQuery(
                "select 1 from sys_department where tenant_id = ? and id = ? and deleted = 0 limit 1",
                tenantId,
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
            params.add(tenantId);
            params.addAll(frontier);
            List<Long> children = jdbcTemplate.queryForList(
                    "select id from sys_department where tenant_id = ? and deleted = 0 and parent_id in (" + placeholders(frontier.size()) + ")",
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

    private Object[] buildTenantAndIdsParams(Long tenantId, List<Long> ids) {
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(ids);
        return params.toArray();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        return currentUser.getCurrentTenantId();
    }

    private void requireAccessibleUserRecord(CurrentUser currentUser, Long userId) {
        if (!canAccessUserRecord(currentUser, userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private boolean canAccessUserRecord(CurrentUser currentUser, Long userId) {
        if (userId == null) {
            return false;
        }
        Long tenantId = currentTenantId(currentUser);
        DataPermissionSql dataPermissionSql = userDataPermissionClause(currentUser, "u");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(userId);
        params.addAll(dataPermissionSql.params());
        return existsByQuery(
                """
                        select 1
                        from sys_user u
                        join sys_user_tenant ut
                          on ut.user_id = u.id
                         and ut.tenant_id = ?
                         and ut.deleted = 0
                         and ut.status = 'ENABLED'
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
        Set<String> permissions = !snapshot.getPermissions().isEmpty()
                ? snapshot.getPermissions()
                : currentUser.getPermissions();
        Set<Long> deptIdsFromSnapshot = snapshot.getDeptIds();
        Set<Long> descendantDeptIdsFromSnapshot = snapshot.getDescendantDeptIds();
        List<DataPermissionRule> dataScopes = !snapshot.getDataScopes().isEmpty()
                ? snapshot.getDataScopes()
                : currentUser.getDataScopes();
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                RESOURCE_SYSTEM_USER,
                currentUser.getUserId(),
                !deptIdsFromSnapshot.isEmpty() ? deptIdsFromSnapshot : currentUser.getDeptIds(),
                !descendantDeptIdsFromSnapshot.isEmpty() ? descendantDeptIdsFromSnapshot : currentUser.getDescendantDeptIds(),
                dataScopes,
                permissions
        );
        if (decision.scopeType() == DataScopeType.ALL || decision.scopeType() == DataScopeType.TENANT) {
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
                        where sud.tenant_id = ?
                          and sud.user_id = %s.id
                          and sud.dept_id in (%s)
                          and sud.deleted = 0
                    )
                    """.formatted(userAlias, placeholders(deptIds.size())));
            params.add(currentTenantId(currentUser));
            params.addAll(deptIds);
        }
        if (!userIds.isEmpty()) {
            conditions.add("%s.id in (%s)".formatted(userAlias, placeholders(userIds.size())));
            params.addAll(userIds);
        }
        return new DataPermissionSql(" and (" + String.join(" or ", conditions) + ")", params);
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getSimulatedRoleId() != null) {
            if (currentUser == null || currentUser.getSimulatedRoleId() == null || currentUser.getCurrentTenantId() == null || currentUser.getUserId() == null) {
                return PermissionSnapshotService.PermissionSnapshot.empty();
            }
            return permissionSnapshotService.loadRoleSnapshot(currentUser.getCurrentTenantId(), currentUser.getSimulatedRoleId());
        }
        if (!org.springframework.util.StringUtils.hasText(currentUser.getPermissionsVersion())) {
            return permissionSnapshotService.loadSnapshot(currentTenantId(currentUser), currentUser.getUserId());
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
        target.setTenantNames(source.getTenantNames());
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
