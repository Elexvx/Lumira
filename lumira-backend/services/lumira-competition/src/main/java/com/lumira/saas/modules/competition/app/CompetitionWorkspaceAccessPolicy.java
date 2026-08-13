package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The single authorization and identity boundary for competition workspaces.
 *
 * <p>Phase one intentionally uses the existing RBAC and data-scope facts. A
 * future competition-member model can be intersected here without changing
 * controllers or frontend capability names.</p>
 */
@Service
public class CompetitionWorkspaceAccessPolicy {
    public static final String COMPETITION_VIEW = "aiadc:competition:view";
    public static final String COMPETITION_UPDATE = "aiadc:competition:update";
    public static final String REGISTRATION_VIEW = "aiadc:registration:view";
    public static final String REGISTRATION_CREATE = "aiadc:registration:create";
    public static final String REGISTRATION_UPDATE = "aiadc:registration:update";
    public static final String MATERIAL_VIEW = "aiadc:material:view";
    public static final String MATERIAL_DOWNLOAD = "registration:material:download";
    public static final String DATASET_VIEW = "registration:dataset:view";
    public static final String DATASET_EXPORT = "registration:dataset:export";
    public static final String STAGE_VIEW = "aiadc:stage:view";
    public static final String STAGE_MANAGE = "aiadc:stage:manage";
    public static final String PAYMENT_VIEW = "payment:order:view";
    public static final String REVIEW_VIEW = "review:workbench:view";
    public static final String REVIEW_MANAGE = "review:plan:manage";
    public static final String REVIEW_BATCH_CREATE = "review:batch:create";
    public static final String REVIEW_ASSIGNMENT_MANAGE = "review:assignment:manage";
    public static final String REVIEW_ROSTER_MANAGE = "review:roster:manage";
    public static final String REVIEW_NOTIFICATION_SEND = "review:notification:send";
    public static final String REVIEW_CHECKIN_SCAN = "review:checkin:scan";
    public static final String REVIEW_RESULT_AGGREGATE = "review:result:aggregate";
    public static final String REVIEW_RESULT_FINALIZE = "review:result:finalize";
    public static final String REVIEW_RESULT_PUBLISH = "review:result:publish";
    public static final String REVIEW_APPEAL_MANAGE = "review:appeal:manage";
    public static final String REVIEW_AUDIT_VIEW = "review:audit:view";
    public static final String CERTIFICATE_VIEW = "aiadc:certificate:view";
    public static final String CERTIFICATE_BATCH_VIEW = "aiadc:certificate-batch:view";
    public static final String CERTIFICATE_BATCH_CREATE = "aiadc:certificate-batch:create";
    public static final String CERTIFICATE_DOWNLOAD = "aiadc:certificate:download";
    public static final String CERTIFICATE_REGENERATE = "aiadc:certificate:regenerate";
    public static final String CERTIFICATE_REVOKE = "aiadc:certificate:revoke";
    public static final String AUDIT_VIEW = "audit:view";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    );

    private final CompetitionManagementRepository competitionRepository;
    private final PermissionGuard permissionGuard;

    public CompetitionWorkspaceAccessPolicy(
            CompetitionManagementRepository competitionRepository,
            PermissionGuard permissionGuard
    ) {
        this.competitionRepository = competitionRepository;
        this.permissionGuard = permissionGuard;
    }

    public CompetitionAccessDecision requireAccessibleCompetition(
            CurrentUser currentUser,
            String competitionUuid,
            CompetitionCapability requiredCapability
    ) {
        CompetitionRef competition = resolveCompetition(currentUser, competitionUuid);
        Set<CompetitionCapability> capabilities = capabilities(currentUser);
        if (!isDiscoverable(currentUser, competition, capabilities)) {
            throw notFound();
        }
        if (requiredCapability != null && !capabilities.contains(requiredCapability)) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前赛事没有该工作空间能力");
        }
        if (requiredCapability != null && requiredCapability.isWrite() && competition.archived()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "赛事已归档，只读");
        }
        return new CompetitionAccessDecision(competition, Collections.unmodifiableSet(capabilities));
    }

    public CompetitionRef resolveCompetition(CurrentUser currentUser, String rawCompetitionUuid) {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        String uuid = normalizeUuid(rawCompetitionUuid);
        CompetitionVO.Competition competition = competitionRepository.findCompetitionByUuid(uuid);
        if (competition == null || !StringUtils.hasText(competition.getUuid())) {
            throw notFound();
        }
        return CompetitionRef.from(competition);
    }

    public Set<CompetitionCapability> capabilities(CurrentUser currentUser) {
        EnumSet<CompetitionCapability> capabilities = EnumSet.noneOf(CompetitionCapability.class);
        if (has(currentUser, "*")) {
            capabilities.addAll(EnumSet.allOf(CompetitionCapability.class));
            return capabilities;
        }

        boolean hasAnyWorkspacePermission = false;
        if (has(currentUser, COMPETITION_VIEW)) {
            capabilities.add(CompetitionCapability.WORKSPACE_VIEW);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, REGISTRATION_VIEW, MATERIAL_VIEW, MATERIAL_DOWNLOAD, DATASET_VIEW, DATASET_EXPORT)) {
            capabilities.add(CompetitionCapability.REGISTRATION_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, REGISTRATION_CREATE, REGISTRATION_UPDATE)) {
            capabilities.add(CompetitionCapability.REGISTRATION_MANAGE);
            capabilities.add(CompetitionCapability.REGISTRATION_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, STAGE_VIEW, REVIEW_VIEW)) {
            capabilities.add(CompetitionCapability.REVIEW_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, STAGE_MANAGE, REVIEW_MANAGE, REVIEW_BATCH_CREATE, REVIEW_ASSIGNMENT_MANAGE,
                REVIEW_ROSTER_MANAGE, REVIEW_NOTIFICATION_SEND, REVIEW_CHECKIN_SCAN, REVIEW_RESULT_AGGREGATE,
                REVIEW_RESULT_FINALIZE, REVIEW_RESULT_PUBLISH, REVIEW_APPEAL_MANAGE)) {
            capabilities.add(CompetitionCapability.REVIEW_MANAGE);
            capabilities.add(CompetitionCapability.REVIEW_READ);
            hasAnyWorkspacePermission = true;
        }
        if (has(currentUser, PAYMENT_VIEW)) {
            capabilities.add(CompetitionCapability.PAYMENT_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, CERTIFICATE_VIEW, CERTIFICATE_BATCH_VIEW, CERTIFICATE_DOWNLOAD)) {
            capabilities.add(CompetitionCapability.CERTIFICATE_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, CERTIFICATE_BATCH_CREATE, CERTIFICATE_REGENERATE, CERTIFICATE_REVOKE)) {
            capabilities.add(CompetitionCapability.CERTIFICATE_MANAGE);
            capabilities.add(CompetitionCapability.CERTIFICATE_READ);
            hasAnyWorkspacePermission = true;
        }
        if (has(currentUser, COMPETITION_UPDATE)) {
            capabilities.add(CompetitionCapability.SETTINGS_MANAGE);
            capabilities.add(CompetitionCapability.WORKSPACE_VIEW);
            hasAnyWorkspacePermission = true;
        }
        if (hasAny(currentUser, AUDIT_VIEW, REVIEW_AUDIT_VIEW)) {
            capabilities.add(CompetitionCapability.AUDIT_READ);
            hasAnyWorkspacePermission = true;
        }
        if (hasAnyWorkspacePermission && !capabilities.contains(CompetitionCapability.WORKSPACE_VIEW)) {
            capabilities.add(CompetitionCapability.WORKSPACE_VIEW);
        }
        return capabilities;
    }

    public boolean canDiscoverAnyCompetition(CurrentUser currentUser) {
        return !capabilities(currentUser).isEmpty();
    }

    public List<String> wireCapabilities(Set<CompetitionCapability> capabilities) {
        return capabilities.stream()
                .map(CompetitionCapability::wireName)
                .sorted()
                .toList();
    }

    public List<String> allowedModules(Set<CompetitionCapability> capabilities) {
        LinkedHashSet<String> modules = new LinkedHashSet<>();
        if (capabilities.contains(CompetitionCapability.WORKSPACE_VIEW)) {
            modules.add("overview");
        }
        if (capabilities.contains(CompetitionCapability.REGISTRATION_READ)
                || capabilities.contains(CompetitionCapability.REGISTRATION_MANAGE)) {
            modules.add("registrations");
        }
        if (capabilities.contains(CompetitionCapability.REVIEW_READ)
                || capabilities.contains(CompetitionCapability.REVIEW_MANAGE)) {
            modules.add("reviews");
        }
        if (capabilities.contains(CompetitionCapability.PAYMENT_READ)) {
            modules.add("payments");
        }
        if (capabilities.contains(CompetitionCapability.CERTIFICATE_READ)
                || capabilities.contains(CompetitionCapability.CERTIFICATE_MANAGE)) {
            modules.add("certificates");
        }
        if (capabilities.contains(CompetitionCapability.SETTINGS_MANAGE)) {
            modules.add("settings");
        }
        if (capabilities.contains(CompetitionCapability.AUDIT_READ)) {
            modules.add("audit");
        }
        return List.copyOf(modules);
    }

    public static String normalizeUuid(String rawCompetitionUuid) {
        if (!StringUtils.hasText(rawCompetitionUuid)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "赛事 UUID 不能为空");
        }
        String value = rawCompetitionUuid.trim();
        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "赛事 UUID 格式无效");
        }
        return UUID.fromString(value).toString();
    }

    private boolean isDiscoverable(
            CurrentUser currentUser,
            CompetitionRef competition,
            Set<CompetitionCapability> capabilities
    ) {
        if (!canDiscoverAnyCompetition(currentUser)) {
            return false;
        }
        if (has(currentUser, COMPETITION_VIEW)
                || has(currentUser, "*")
                || capabilities.contains(CompetitionCapability.SETTINGS_MANAGE)) {
            return true;
        }
        return "published".equalsIgnoreCase(competition.status());
    }

    private boolean hasAny(CurrentUser currentUser, String... permissions) {
        for (String permission : permissions) {
            if (has(currentUser, permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean has(CurrentUser currentUser, String permission) {
        return currentUser != null && permissionGuard.hasPermission(currentUser, permission);
    }

    private BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
    }
}
