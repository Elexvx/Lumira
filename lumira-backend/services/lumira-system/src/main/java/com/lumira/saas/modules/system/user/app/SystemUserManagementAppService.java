package com.lumira.saas.modules.system.user.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lumira.saas.modules.system.app.SystemProfileSettingsAppService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.profile.repository.SystemCurrentUserProfileRepository;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.user.infrastructure.SystemUserManagementPersistenceAdapters;
import com.lumira.saas.modules.system.user.repository.SystemUserManagementRepository;
import com.lumira.saas.modules.system.user.support.UserUidGenerator;
import com.lumira.saas.modules.system.user.support.UserAvatarDefaults;
import com.lumira.saas.modules.system.user.vo.UserDetailVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemUserManagementAppService {

    private static final Long DEFAULT_ADMIN_USER_ID = 1001L;
    private static final String ASYNC_EXPORT_SESSION_PREFIX = "internal-export-task-";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String EXTRA_PROFILE_VALUES_KEY = "customProfileValues";
    private static final int CUSTOM_PROFILE_VALUE_MAX_LENGTH = 1000;
    private static final String RESOURCE_SYSTEM_USER = "system:user";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemUserManagementRepository userRepository;
    private final UserDomainService userDomainService;
    private final IamUserService iamUserService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final OnlineSessionManagementAppService onlineSessionManagementAppService;
    private final OperationAuditService operationAuditService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final SystemCurrentUserProfileRepository currentUserProfileRepository;
    private final SystemProfileSettingsAppService systemProfileSettingsAppService;
    private final boolean enforceTrustedUserResolution;

    public SystemUserManagementAppService(
            Object persistence,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService
    ) {
        this(
                SystemUserManagementPersistenceAdapters.from(persistence),
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                null,
                null,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService,
                null,
                null,
                false
        );
    }

    @Autowired
    public SystemUserManagementAppService(
            SystemUserManagementRepository userRepository,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            SystemCurrentUserProfileRepository currentUserProfileRepository,
            SystemProfileSettingsAppService systemProfileSettingsAppService
    ) {
        this(
                userRepository,
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService,
                currentUserProfileRepository,
                systemProfileSettingsAppService,
                true
        );
    }

    /** Compatibility constructor for tests still providing the legacy persistence facade. */
    public SystemUserManagementAppService(
            Object persistence,
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
                SystemUserManagementPersistenceAdapters.from(persistence),
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                onlineSessionManagementAppService,
                operationAuditService,
                passwordEncoder,
                passwordPolicyService,
                null,
                null,
                true
        );
    }

    private SystemUserManagementAppService(
            SystemUserManagementRepository userRepository,
            UserDomainService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            SystemCurrentUserProfileRepository currentUserProfileRepository,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            boolean enforceTrustedUserResolution
    ) {
        this.userRepository = userRepository;
        this.userDomainService = userDomainService;
        this.iamUserService = iamUserService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.onlineSessionManagementAppService = onlineSessionManagementAppService;
        this.operationAuditService = operationAuditService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.currentUserProfileRepository = currentUserProfileRepository;
        this.systemProfileSettingsAppService = systemProfileSettingsAppService;
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
        LocalDateTime registeredStartAt = parseDateTimeParam(registeredStart, false);
        LocalDateTime registeredEndAt = parseDateTimeParam(registeredEnd, true);
        LocalDateTime lastLoginStartAt = parseDateTimeParam(lastLoginStart, false);
        LocalDateTime lastLoginEndAt = parseDateTimeParam(lastLoginEnd, true);
        LocalDateTime cursorCreatedAtValue = parseDateTimeParam(cursorCreatedAt, false);
        Set<Long> departmentFilterIds = deptId == null ? null : userRepository.findActiveDepartmentTree(deptId);
        PageResponse<SystemVO.UserVO> page = userRepository.findUsers(new SystemUserManagementRepository.UserSearch(
                userId,
                uid,
                StringUtils.hasText(username) ? iamUserService.normalizeIdentifier(IamUserService.IDENTITY_USERNAME, username) : null,
                StringUtils.hasText(mobile) ? iamUserService.normalizeIdentifier(IamUserService.IDENTITY_MOBILE, mobile) : null,
                StringUtils.hasText(email) ? iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, email) : null,
                departmentFilterIds,
                StringUtils.hasText(status) ? normalizeUserStatus(status) : null,
                StringUtils.hasText(source) ? source.trim().toUpperCase(Locale.ROOT) : null,
                registeredStartAt,
                registeredEndAt,
                lastLoginStartAt,
                lastLoginEndAt,
                cursorId,
                cursorCreatedAtValue,
                pageNo,
                pageSize,
                userDataVisibility(currentUser)
        ));
        decorateUsers(page.getRecords());
        maskSensitiveUsers(page.getRecords(), canViewSensitiveUserInfo(currentUser));
        return page;
    }

    public SystemVO.UserDetailVO getUser(CurrentUser currentUser, Long userId) {
        assertAuthenticated(currentUser);
        requirePermission(currentUser, "system:user:view");
        requireAccessibleUserRecord(currentUser, userId);
        return buildUserDetail(currentUser, userId);
    }

    /**
     * Builds the response after the public entry point has already authenticated
     * and authorized the actor. Permission mutations advance the global snapshot
     * version, so authenticating again here would compare the request's old token
     * version with the new snapshot and incorrectly turn a successful write into
     * SESSION_EXPIRED.
     */
    private SystemVO.UserDetailVO buildUserDetail(CurrentUser currentUser, Long userId) {
        SystemVO.UserVO user = queryUser(userId);
        boolean canViewSensitive = canViewSensitiveUserInfo(currentUser);
        if (!canViewSensitive) {
            maskSensitiveUser(user);
        }
        SystemVO.UserDetailVO detail = new SystemVO.UserDetailVO();
        copyUser(detail, user);
        String userUuid = user.getUserUuid();
        detail.setExtraProfileValues(loadExtraProfileValues(userId, userUuid));
        detail.setRoleIds(userRepository.findActiveUserRoleIds(userId, userUuid));
        List<Long> deptIds = userRepository.findActiveUserDepartmentIds(userId, userUuid);
        detail.setDeptIds(deptIds);
        detail.setPrimaryDeptId(deptIds.isEmpty() ? null : deptIds.get(0));
        detail.setRoleNames(userRepository.findActiveUserRoleNames(userId, userUuid));
        detail.setDeptNames(userRepository.findActiveUserDepartmentNames(userId, userUuid));
        detail.setIdentities(iamUserService.listIdentities(userId, userUuid).stream()
                .map(identity -> toUserIdentityVO(identity, canViewSensitive))
                .toList());
        detail.setRecentDevices(iamUserService.listRecentDevices(userId, userUuid, 10).stream()
                .map(device -> toUserDeviceVO(device, canViewSensitive))
                .toList());
        detail.setSecuritySetting(iamUserService.findSecuritySetting(userId, userUuid)
                .map(this::toUserSecuritySettingVO)
                .orElse(null));
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
        updateExtraProfileValues(currentUser, userId, userUuid, request.getExtraProfileValues());
        // Creating an identity does not change any existing user's roles,
        // departments, or data scope. Advancing the global permission version
        // here would invalidate every active administrator session for a
        // mutation that cannot affect the current user's authorization.
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "create", "CREATE", "SUCCESS", "创建用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId);
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
        updateExtraProfileValues(currentUser, userId, userUuid, request.getExtraProfileValues());
        permissionSnapshotService.invalidatePermissions();
        operationAuditService.log(currentUser.getUserId(), currentUser.getUserUuid(), currentUser.getUsername(), "user", "update", "UPDATE", "SUCCESS", "更新用户: " + request.getUsername());
        return buildUserDetail(currentUser, userId);
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
        int updated = userRepository.updateStatus(
                userId,
                userUuid,
                normalizedStatus,
                actor(currentUser.getUserId(), currentUser.getUserUuid()),
                LocalDateTime.now()
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

        SystemUserManagementRepository.Actor actor = actor(currentUser.getUserId(), currentUser.getUserUuid());
        int deleted = userRepository.softDeleteUser(userId, userUuid, actor, now);
        if (deleted <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "User changed, please retry");
        }
        userRepository.retireUserRelations(userId, userUuid, actor, now);
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
        return userRepository.findActiveUserRoles(userId, userUuid);
    }

    private SystemVO.UserVO queryUser(Long userId) {
        SystemVO.UserVO user = userRepository.findActiveUser(userId);
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
                SystemUserManagementRepository.UserSaveResult result = userRepository.saveUser(userSave(
                        null,
                        null,
                        UserUidGenerator.nextNumericUid(),
                        username,
                        passwordEncoder.encode(password),
                        request,
                        normalizedStatus,
                        operatorId,
                        operatorUuid,
                        null
                ));
                requireRelationshipWrite(result.writeCount(), "User changed, please retry");
                if (result.userId() == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "User changed, please retry");
                }
                Long createdUserId = result.userId();
                userDomainService.findById(createdUserId).ifPresent(user -> {
                    iamUserService.createUserWithIdentity(user, username, "ADMIN_CREATE");
                    iamUserService.recordUserRegistered(user.getId(), user.getUuid(), "ADMIN_CREATE", null, null);
                });
                return createdUserId;
            } catch (DuplicateKeyException exception) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
            }
        }
        try {
            SystemUserManagementRepository.UserSaveResult result = userRepository.saveUser(userSave(
                    userId,
                    userUuid,
                    null,
                    username,
                    null,
                    request,
                    normalizedStatus,
                    operatorId,
                    operatorUuid,
                    LocalDateTime.now()
            ));
            requireRelationshipWrite(result.writeCount(), "User changed, please retry");
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
        if (StringUtils.hasText(request.getPassword())) {
            passwordPolicyService.validatePassword(request.getPassword());
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            int passwordUpdated = userRepository.updatePasswordHash(
                    userId,
                    userUuid,
                    encodedPassword,
                    actor(operatorId, operatorUuid),
                    LocalDateTime.now()
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
        Long existingUserId = userRepository.findActiveUserIdByUsername(username);
        if (existingUserId != null && !existingUserId.equals(currentUserId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
        Long existingIdentityUserId = userRepository.findActiveIdentityUserId(
                iamUserService.normalizeIdentifier(IamUserService.IDENTITY_USERNAME, username)
        );
        if (existingIdentityUserId != null && !existingIdentityUserId.equals(currentUserId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }
    }

    private void replaceUserRoles(Long userId, String userUuid, List<Long> roleIds, Long operatorId, String operatorUuid) {
        if (CollectionUtils.isEmpty(roleIds)) {
            userRepository.retireUserRoles(userId, userUuid, actor(operatorId, operatorUuid), LocalDateTime.now());
            return;
        }
        if (roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User role id must be a positive integer");
        }
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        int existingRoleCount = userRepository.countActiveRoles(distinctRoleIds);
        if (existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role does not exist or is disabled");
        }
        SystemUserManagementRepository.Actor actor = actor(operatorId, operatorUuid);
        userRepository.retireUserRoles(userId, userUuid, actor, LocalDateTime.now());
        for (Long roleId : distinctRoleIds) {
            int inserted = userRepository.upsertUserRole(userId, userUuid, roleId, actor);
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
        SystemUserManagementRepository.Actor actor = actor(operatorId, operatorUuid);
        userRepository.retireUserDepartments(userId, userUuid, actor, LocalDateTime.now());
        if (CollectionUtils.isEmpty(deptIds)) {
            return;
        }
        if (deptIds.stream().anyMatch(deptId -> deptId == null || deptId <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "User department id must be a positive integer");
        }
        List<Long> distinctDeptIds = new ArrayList<>(new LinkedHashSet<>(deptIds));
        int existingDeptCount = userRepository.countEnabledDepartments(distinctDeptIds);
        if (existingDeptCount != distinctDeptIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Department does not exist or is disabled");
        }
        Long effectivePrimaryDeptId = primaryDeptId != null && distinctDeptIds.contains(primaryDeptId)
                ? primaryDeptId
                : distinctDeptIds.get(0);
        for (Long deptId : distinctDeptIds) {
            int inserted = userRepository.upsertUserDepartment(userId, userUuid, deptId, deptId.equals(effectivePrimaryDeptId), actor);
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

        var roleNames = userRepository.findActiveUserRoleNames(userIds);
        var deptNames = userRepository.findActiveUserDepartmentNames(userIds);
        var userUuids = userRepository.findActiveUserUuids(userIds);
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

    private String requireUserUuid(Long userId) {
        String userUuid = userRepository.findActiveUserUuid(userId);
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

    private void updateExtraProfileValues(
            CurrentUser currentUser,
            Long userId,
            String userUuid,
            Map<String, String> requestedValues
    ) {
        if (requestedValues == null || requestedValues.isEmpty()
                || currentUserProfileRepository == null || systemProfileSettingsAppService == null) {
            return;
        }
        Set<String> allowedKeys = systemProfileSettingsAppService.getProfileFieldSettings(currentUser).stream()
                .filter(item -> Boolean.TRUE.equals(item.getCustom()))
                .map(ProfileFieldSettingVO::getFieldKey)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (allowedKeys.isEmpty()) {
            return;
        }

        Map<String, String> sanitizedValues = new LinkedHashMap<>();
        requestedValues.forEach((fieldKey, value) -> {
            if (!allowedKeys.contains(fieldKey)) {
                return;
            }
            String normalized = normalizeNullableText(value);
            sanitizedValues.put(fieldKey, normalized == null
                    ? null
                    : normalized.substring(0, Math.min(normalized.length(), CUSTOM_PROFILE_VALUE_MAX_LENGTH)));
        });
        if (sanitizedValues.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(EXTRA_PROFILE_VALUES_KEY, sanitizedValues);
        try {
            currentUserProfileRepository.mergeExtraProfileJson(userId, userUuid, OBJECT_MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to serialize extra profile values");
        }
    }

    private Map<String, String> loadExtraProfileValues(Long userId, String userUuid) {
        if (currentUserProfileRepository == null) {
            return Map.of();
        }
        String extraJson = currentUserProfileRepository.findExtraProfileJson(userId, userUuid);
        if (!StringUtils.hasText(extraJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(extraJson, new TypeReference<>() {});
            Object rawValues = payload.get(EXTRA_PROFILE_VALUES_KEY);
            if (!(rawValues instanceof Map<?, ?> rawMap)) {
                return Map.of();
            }
            Map<String, String> values = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key instanceof String fieldKey && value instanceof String fieldValue && StringUtils.hasText(fieldValue)) {
                    values.put(fieldKey, fieldValue);
                }
            });
            return values;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
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
        int existingRoleCount = userRepository.countActiveRoles(distinctRoleIds);
        if (existingRoleCount != distinctRoleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role not found");
        }
        if (currentUser.getPermissions() != null && currentUser.getPermissions().contains("*")) {
            return;
        }
        if (userRepository.countPrivilegedRoles(distinctRoleIds) > 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "Privileged role assignment denied");
        }
    }

    private void requireAccessibleUserRecord(CurrentUser currentUser, Long userId) {
        if (!canAccessUserRecord(currentUser, userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "User does not exist");
        }
    }

    private boolean canAccessUserRecord(CurrentUser currentUser, Long userId) {
        return userRepository.canAccessActiveUser(userId, userDataVisibility(currentUser));
    }

    private SystemUserManagementRepository.DataVisibility userDataVisibility(CurrentUser currentUser) {
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
            return new SystemUserManagementRepository.DataVisibility(true, Set.of(), Set.of());
        }
        Set<Long> deptIds = new LinkedHashSet<>(decision.deptIds());
        Set<Long> userIds = new LinkedHashSet<>(decision.userIds());
        if (decision.hasDeptRestriction() && currentUser.getUserId() != null) {
            userIds.add(currentUser.getUserId());
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) {
            userIds.add(currentUser.getUserId());
        }

        return new SystemUserManagementRepository.DataVisibility(false, Set.copyOf(deptIds), Set.copyOf(userIds));
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

    private SystemUserManagementRepository.Actor actor(Long userId, String userUuid) {
        return new SystemUserManagementRepository.Actor(userId, userUuid);
    }

    private SystemUserManagementRepository.UserSave userSave(
            Long userId,
            String userUuid,
            String generatedUuid,
            String username,
            String passwordHash,
            SystemDTO.UserUpsertRequest request,
            String status,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        String avatarUrl = normalizeNullableText(request.getAvatarUrl());
        if (avatarUrl == null) {
            avatarUrl = UserAvatarDefaults.generatedAvatarUrl(userUuid != null ? userUuid : generatedUuid);
        }
        return new SystemUserManagementRepository.UserSave(
                userId,
                userUuid,
                generatedUuid,
                username,
                passwordHash,
                normalizeNullableText(request.getMobile()),
                normalizeNullableText(request.getNickname()),
                normalizeNullableText(request.getRealName()),
                avatarUrl,
                normalizeNullableText(request.getEmail()),
                normalizeNullableText(request.getBirthMonth()),
                normalizeNullableText(request.getGender()),
                normalizeNullableText(request.getRegion()),
                normalizeNullableText(request.getAvailableTime()),
                normalizeNullableText(request.getIdCardNumber()),
                status,
                actor(operatorId, operatorUuid),
                updatedAt
        );
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

}
