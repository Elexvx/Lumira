package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface AiToolRegistry {

    List<AiVO.SkillVO> listRegisteredSkills(Long employeeId);
}

@Service
@Primary
class DefaultAiToolRegistry implements AiToolRegistry {
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    DefaultAiToolRegistry(
            MyBatisQueryOperations jdbcTemplate,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, authorizationService, securityContextFacade, permissionSnapshotService, null, null);
    }

    @Autowired
    DefaultAiToolRegistry(
            MyBatisQueryOperations jdbcTemplate,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                authorizationService,
                securityContextFacade,
                permissionSnapshotService,
                null,
                sessionAuthenticationService
        );
    }

    DefaultAiToolRegistry(
            MyBatisQueryOperations jdbcTemplate,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.securityContextFacade = securityContextFacade;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    DefaultAiToolRegistry(
            MyBatisQueryOperations jdbcTemplate,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade
    ) {
        this(jdbcTemplate, authorizationService, securityContextFacade, null);
    }

    @Override
    public List<AiVO.SkillVO> listRegisteredSkills(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return Collections.emptyList();
        }
        CurrentUser currentUser = refreshTrustedCurrentUser(securityContextFacade.getCurrentUser());
        return jdbcTemplate.query(
                """
                        select k.id, k.skill_code as skillCode, k.skill_name as skillName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                               k.enabled, k.create_time as createTime, k.update_time as updateTime,
                               r.permission_mode as permissionMode
                        from ai_skill k
                        join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                          and k.enabled = 1
                          and lower(r.permission_mode) in ('view', 'visit', 'invoke', 'execute', 'allow')
                        order by k.category asc, k.skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.SkillVO.class),
                employeeId
        ).stream()
                .filter(skill -> isAuthorized(currentUser, employeeId, skill))
                .toList();
    }

    private boolean isAuthorized(CurrentUser currentUser, Long employeeId, AiVO.SkillVO skill) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)
                || skill == null) {
            return false;
        }
        String permissionKey = StringUtils.hasText(skill.getSkillCode())
                ? "ai:tool:" + skill.getSkillCode()
                : "ai:tool:invoke";
        AuthorizationDecision decision = authorizationService.evaluate(AuthorizationRequest.aiToolAccess(
                currentUser,
                employeeId,
                skill.getSkillCode(),
                permissionKey,
                skill.getRiskLevel(),
                Boolean.TRUE.equals(skill.getReadOnly()) ? "view" : "execute",
                Map.of(
                        "agentGrant", skill.getPermissionMode(),
                        "permissionMode", skill.getPermissionMode(),
                        "readOnly", Boolean.TRUE.equals(skill.getReadOnly())
                )
        ));
        return decision.allowed() || decision.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM;
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (sessionAuthenticationService != null) {
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
            return currentUser;
        }
        if (permissionSnapshotService == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            return currentUser;
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            return currentUser;
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        CurrentUser refreshed = new CurrentUser(
                userId,
                currentUser.getUsername(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                true,
                snapshot.getPermissions() == null ? java.util.Set.of() : java.util.Set.copyOf(snapshot.getPermissions()),
                snapshot.getRoleIds() == null ? java.util.Set.of() : java.util.Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        refreshed.setUserUuid(normalizedUserUuid);
        refreshed.setPermissionsVersion(snapshot.getVersion());
        refreshed.setDefaultHomePath(snapshot.getDefaultHomePath());
        refreshed.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        refreshed.setSimulatedRoleId(currentUser.getSimulatedRoleId());
        refreshed.setLoginType(currentUser.getLoginType());
        return refreshed;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
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
        target.setPermissions(source.getPermissions() == null ? java.util.Set.of() : java.util.Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? java.util.Set.of() : java.util.Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }
}
