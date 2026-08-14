package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.repository.CompetitionAuditRepository;
import com.lumira.saas.modules.competition.vo.CompetitionAuditVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UUID-scoped audit read API for a competition workspace. */
@RestController
@RequestMapping("/api/v2/aiadc/competitions/{competitionUuid}/audit")
public class CompetitionWorkspaceAuditController {
    private static final long MAX_PAGE_SIZE = 100L;

    private final CompetitionWorkspaceAccessPolicy accessPolicy;
    private final CompetitionAuditRepository auditRepository;
    private final SecurityContextFacade securityContextFacade;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;

    @Autowired
    public CompetitionWorkspaceAuditController(
            CompetitionWorkspaceAccessPolicy accessPolicy,
            CompetitionAuditRepository auditRepository,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this.accessPolicy = accessPolicy;
        this.auditRepository = auditRepository;
        this.securityContextFacade = securityContextFacade;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
    }

    @GetMapping
    public ApiResponse<PageResponse<CompetitionAuditVO.Record>> records(
            @PathVariable String competitionUuid,
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        CurrentUser currentUser = requireTrustedUser();
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(
                currentUser, competitionUuid, CompetitionCapability.AUDIT_READ);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        String normalizedModule = normalizeModule(module);
        String normalizedAction = normalizeAction(action);
        CompetitionAuditRepository.AuditPage page = auditRepository.findRecords(
                decision.competition().uuid(), normalizedModule, normalizedAction,
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        PageResponse<CompetitionAuditVO.Record> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < page.total());
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    private String normalizeModule(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Audit module is too large");
        }
        return normalized;
    }

    private String normalizeAction(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Audit action is too large");
        }
        return normalized;
    }

    private CurrentUser requireTrustedUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(currentUser, trustedCurrentUserResolver, true);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
