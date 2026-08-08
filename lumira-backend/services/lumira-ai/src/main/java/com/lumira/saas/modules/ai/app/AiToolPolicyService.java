package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.integration.AiPermissionSnapshotResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiToolPolicyRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface AiToolPolicyService {

    PageResponse<AiVO.ToolPolicyVO> listPolicies(CurrentUser currentUser, long pageNo, long pageSize);

    AiVO.ToolPolicyVO createPolicy(CurrentUser currentUser, AiDTO.ToolPolicyUpsertRequest request);

    AiVO.ToolPolicyVO updatePolicy(CurrentUser currentUser, Long id, AiDTO.ToolPolicyUpsertRequest request);

    boolean updatePolicyEnabled(CurrentUser currentUser, Long id, boolean enabled);

    boolean deletePolicy(CurrentUser currentUser, Long id);

    PolicyDecision evaluate(String toolCode, String actionType, String riskLevel, String message, Map<String, Object> arguments);

    record PolicyDecision(String verdict, String message, List<String> matches) {
        boolean denied() {
            return "DENY".equalsIgnoreCase(verdict);
        }
    }
}

@Service
@Primary
class DefaultAiToolPolicyService implements AiToolPolicyService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String STATUS_ENABLED = "ENABLED";

    private final AiToolPolicyRepository policyRepository;
    private final AiPermissionSnapshotResolver permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final AiTrustedSessionResolver sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    DefaultAiToolPolicyService(
            AiToolPolicyRepository policyRepository,
            AiPermissionSnapshotResolver permissionSnapshotService
    ) {
        this(policyRepository, permissionSnapshotService, null, null, false);
    }

    @Autowired
    DefaultAiToolPolicyService(
            AiToolPolicyRepository policyRepository,
            AiPermissionSnapshotResolver permissionSnapshotService,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(policyRepository, permissionSnapshotService, null, sessionAuthenticationService, true);
    }

    DefaultAiToolPolicyService(
            AiToolPolicyRepository policyRepository,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(policyRepository, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private DefaultAiToolPolicyService(
            AiToolPolicyRepository policyRepository,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.policyRepository = policyRepository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    DefaultAiToolPolicyService(AiToolPolicyRepository policyRepository) {
        this(policyRepository, null);
    }

    @Override
    public PageResponse<AiVO.ToolPolicyVO> listPolicies(CurrentUser currentUser, long pageNo, long pageSize) {
        requirePermission(currentUser, "ai:tool-policy:view");
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        long offset = (safePageNo - 1) * safePageSize;
        List<AiVO.ToolPolicyVO> records = policyRepository.findPage(safePageSize, offset);
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : policyRepository.countActive();
        PageResponse<AiVO.ToolPolicyVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    @Override
    @Transactional
    public AiVO.ToolPolicyVO createPolicy(CurrentUser currentUser, AiDTO.ToolPolicyUpsertRequest request) {
        requirePermission(currentUser, "ai:tool-policy:manage");
        Long id = policyRepository.create(policyMutation(request), LocalDateTime.now());
        requirePolicyWrite(id == null ? 0 : 1);
        return requirePolicy(id);
    }

    @Override
    @Transactional
    public AiVO.ToolPolicyVO updatePolicy(CurrentUser currentUser, Long id, AiDTO.ToolPolicyUpsertRequest request) {
        requirePermission(currentUser, "ai:tool-policy:manage");
        AiVO.ToolPolicyVO existing = requirePolicy(id);
        int updated = policyRepository.update(id, existing, policyMutation(request), LocalDateTime.now());
        requirePolicyWrite(updated);
        return requirePolicy(id);
    }

    @Override
    @Transactional
    public boolean updatePolicyEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        requirePermission(currentUser, "ai:tool-policy:manage");
        AiVO.ToolPolicyVO existing = requirePolicy(id);
        int updated = policyRepository.updateEnabled(id, existing, enabled, LocalDateTime.now());
        requirePolicyWrite(updated);
        return true;
    }

    @Override
    @Transactional
    public boolean deletePolicy(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "ai:tool-policy:manage");
        AiVO.ToolPolicyVO existing = requirePolicy(id);
        int updated = policyRepository.softDelete(id, existing, LocalDateTime.now());
        requirePolicyWrite(updated);
        return true;
    }

    @Override
    public PolicyDecision evaluate(String toolCode, String actionType, String riskLevel, String message, Map<String, Object> arguments) {
        List<AiVO.ToolPolicyVO> policies = policyRepository.findEnabled();
        String haystack = (String.valueOf(message) + "\n" + String.valueOf(arguments)).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        String decisionMessage = null;
        for (AiVO.ToolPolicyVO policy : policies) {
            if (!matchesTool(policy.getToolCode(), toolCode)
                    || !matchesNullable(policy.getActionType(), actionType)
                    || !matchesNullable(policy.getRiskLevel(), riskLevel)
                    || !matchesValue(policy.getMatchType(), policy.getMatchValue(), haystack)) {
                continue;
            }
            matches.add(policy.getPolicyName());
            if ("DENY".equalsIgnoreCase(policy.getVerdict())) {
                return new PolicyDecision("DENY", firstText(policy.getMessage(), "AI tool policy blocked: " + policy.getPolicyName()), matches);
            }
            if (!StringUtils.hasText(decisionMessage)) {
                decisionMessage = policy.getMessage();
            }
        }
        return new PolicyDecision("ALLOW", firstText(decisionMessage, "Platform policy check passed"), matches);
    }

    private AiVO.ToolPolicyVO requirePolicy(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Policy id is required");
        }
        return policyRepository.findActiveById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "AI tool policy does not exist"));
    }

    private AiToolPolicyRepository.PolicyMutation policyMutation(AiDTO.ToolPolicyUpsertRequest request) {
        return new AiToolPolicyRepository.PolicyMutation(
                requiredText(request.getPolicyName(), "Policy name is required"),
                defaultText(request.getToolCode(), "*"),
                normalizeText(request.getActionType()),
                normalizeRisk(request.getRiskLevel()),
                defaultText(request.getMatchType(), "KEYWORD"),
                normalizeText(request.getMatchValue()),
                defaultText(request.getVerdict(), "DENY").toUpperCase(Locale.ROOT),
                normalizeText(request.getMessage()),
                !Boolean.FALSE.equals(request.getEnabled())
        );
    }

    private boolean matchesTool(String pattern, String toolCode) {
        String resolvedPattern = defaultText(pattern, "*");
        if ("*".equals(resolvedPattern)) {
            return true;
        }
        if (resolvedPattern.endsWith("*")) {
            return String.valueOf(toolCode).startsWith(resolvedPattern.substring(0, resolvedPattern.length() - 1));
        }
        return resolvedPattern.equals(toolCode);
    }

    private boolean matchesNullable(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(String.valueOf(actual));
    }

    private boolean matchesValue(String matchType, String matchValue, String haystack) {
        if (!StringUtils.hasText(matchValue)) {
            return true;
        }
        if ("REGEX".equalsIgnoreCase(matchType)) {
            return java.util.regex.Pattern.compile(matchValue, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(haystack).find();
        }
        for (String keyword : matchValue.split(",")) {
            if (StringUtils.hasText(keyword) && haystack.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeRisk(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void requirePolicyWrite(int updated) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI tool policy changed, please retry");
        }
    }

    private void requireLogin(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        requireLogin(runtimeUser);
        Set<String> permissions = runtimeUser.getPermissions();
        if (permissions == null || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
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
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        AiPermissionSnapshotResolver.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user permission snapshot is unavailable");
            }
            return currentUser;
        }
        CurrentUser refreshed = new CurrentUser(
                userId,
                currentUser.getUsername(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                true,
                snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()),
                snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        refreshed.setUserUuid(normalizedUserUuid);
        refreshed.setPermissionsVersion(snapshot.getVersion());
        refreshed.setDefaultHomePath(snapshot.getDefaultHomePath());
        refreshed.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        refreshed.setSimulatedRoleId(simulatedRoleId);
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(AiTrustedSessionResolver.AuthenticatedAccess authenticatedAccess) {
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
}
