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
import com.lumira.saas.modules.ai.repository.AiToolRegistryRepository;
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

    private final AiToolRegistryRepository toolRegistryRepository;
    private final AuthorizationService authorizationService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    DefaultAiToolRegistry(
            AiToolRegistryRepository toolRegistryRepository,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(toolRegistryRepository, authorizationService, securityContextFacade, permissionSnapshotService, null, null, false);
    }

    @Autowired
    DefaultAiToolRegistry(
            AiToolRegistryRepository toolRegistryRepository,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                toolRegistryRepository,
                authorizationService,
                securityContextFacade,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                true
        );
    }

    DefaultAiToolRegistry(
            AiToolRegistryRepository toolRegistryRepository,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                toolRegistryRepository,
                authorizationService,
                securityContextFacade,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private DefaultAiToolRegistry(
            AiToolRegistryRepository toolRegistryRepository,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.toolRegistryRepository = toolRegistryRepository;
        this.authorizationService = authorizationService;
        this.securityContextFacade = securityContextFacade;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    DefaultAiToolRegistry(
            AiToolRegistryRepository toolRegistryRepository,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade
    ) {
        this(toolRegistryRepository, authorizationService, securityContextFacade, null);
    }

    @Override
    public List<AiVO.SkillVO> listRegisteredSkills(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return Collections.emptyList();
        }
        CurrentUser currentUser = refreshTrustedCurrentUser(securityContextFacade.getCurrentUser());
        return toolRegistryRepository.findGrantedSkills(employeeId).stream()
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
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
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
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            return currentUser;
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
            return currentUser;
        }
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
        refreshed.setSimulatedRoleId(simulatedRoleId);
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

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
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
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
