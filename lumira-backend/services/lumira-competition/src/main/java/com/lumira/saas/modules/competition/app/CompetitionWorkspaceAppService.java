package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionWorkspaceVO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Application service for the workspace shell and its stable metadata contract. */
@Service
public class CompetitionWorkspaceAppService {
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Set<String> MODULES = Set.of(
            "overview", "registrations", "reviews", "payments", "certificates", "settings", "audit"
    );

    private final CompetitionManagementRepository competitionRepository;
    private final CompetitionWorkspaceAccessPolicy accessPolicy;

    public CompetitionWorkspaceAppService(
            CompetitionManagementRepository competitionRepository,
            CompetitionWorkspaceAccessPolicy accessPolicy
    ) {
        this.competitionRepository = competitionRepository;
        this.accessPolicy = accessPolicy;
    }

    public PageResponse<CompetitionWorkspaceVO.Workspace> listWorkspaces(
            CurrentUser currentUser,
            String keyword,
            String status,
            String module,
            long pageNo,
            long pageSize
    ) {
        Set<CompetitionCapability> capabilities = accessPolicy.capabilities(currentUser);
        if (capabilities.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号没有赛事工作空间权限");
        }
        String normalizedModule = normalizeModule(module);
        if (StringUtils.hasText(normalizedModule) && !accessPolicy.allowedModules(capabilities).contains(normalizedModule)) {
            return emptyPage(Math.max(1L, pageNo), normalizePageSize(pageSize));
        }

        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        String effectiveStatus = normalizeStatus(status);
        if (!capabilities.contains(CompetitionCapability.SETTINGS_MANAGE)
                && !hasCompetitionView(currentUser)) {
            effectiveStatus = "published";
        }
        CompetitionManagementRepository.CompetitionPage page = competitionRepository.findCompetitions(
                new CompetitionManagementRepository.CompetitionSearch(
                        trimToNull(keyword),
                        null,
                        effectiveStatus,
                        null,
                        null,
                        (normalizedPageNo - 1) * normalizedPageSize,
                        normalizedPageSize
                )
        );
        List<CompetitionWorkspaceVO.Workspace> records = page.records().stream()
                .filter(competition -> isVisible(competition, currentUser, capabilities))
                .map(competition -> toWorkspace(competition, capabilities))
                .toList();
        PageResponse<CompetitionWorkspaceVO.Workspace> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < page.total());
        return response;
    }

    public CompetitionWorkspaceVO.Workspace getWorkspace(CurrentUser currentUser, String competitionUuid) {
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(
                currentUser,
                competitionUuid,
                CompetitionCapability.WORKSPACE_VIEW
        );
        return toWorkspace(decision.competition(), decision.capabilities());
    }

    public CompetitionAccessDecision require(
            CurrentUser currentUser,
            String competitionUuid,
            CompetitionCapability requiredCapability
    ) {
        return accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, requiredCapability);
    }

    private CompetitionWorkspaceVO.Workspace toWorkspace(
            CompetitionVO.Competition competition,
            Set<CompetitionCapability> capabilities
    ) {
        return toWorkspace(CompetitionRef.from(competition), capabilities);
    }

    private CompetitionWorkspaceVO.Workspace toWorkspace(
            CompetitionRef competition,
            Set<CompetitionCapability> capabilities
    ) {
        CompetitionWorkspaceVO.Workspace workspace = new CompetitionWorkspaceVO.Workspace();
        workspace.setCompetitionUuid(competition.uuid());
        workspace.setCompetitionNo(competition.competitionNo());
        workspace.setCode(competition.code());
        workspace.setTitle(competition.title());
        workspace.setStatus(competition.status());
        workspace.setActiveRegistrationCount(
                competition.id() == null ? 0L : competitionRepository.countActiveRegistrations(competition.id())
        );
        workspace.setCapabilities(accessPolicy.wireCapabilities(capabilities));
        workspace.setAllowedModules(accessPolicy.allowedModules(capabilities));
        return workspace;
    }

    private boolean isVisible(
            CompetitionVO.Competition competition,
            CurrentUser currentUser,
            Set<CompetitionCapability> capabilities
    ) {
        return hasCompetitionView(currentUser)
                || "published".equalsIgnoreCase(competition.getStatus())
                || capabilities.contains(CompetitionCapability.SETTINGS_MANAGE);
    }

    private boolean hasCompetitionView(CurrentUser currentUser) {
        return currentUser != null && currentUser.getPermissions().stream().anyMatch(
                permission -> "*".equals(permission) || CompetitionWorkspaceAccessPolicy.COMPETITION_VIEW.equals(permission)
        );
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("draft", "published", "archived").contains(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "赛事状态无效");
        }
        return value;
    }

    private String normalizeModule(String module) {
        if (!StringUtils.hasText(module)) {
            return null;
        }
        String value = module.trim().toLowerCase(java.util.Locale.ROOT);
        if (!MODULES.contains(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "赛事工作空间模块无效");
        }
        return value;
    }

    private long normalizePageSize(long pageSize) {
        return Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private PageResponse<CompetitionWorkspaceVO.Workspace> emptyPage(long pageNo, long pageSize) {
        PageResponse<CompetitionWorkspaceVO.Workspace> response = new PageResponse<>();
        response.setRecords(List.of());
        response.setTotal(0L);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setHasMore(false);
        return response;
    }
}
