package com.lumira.saas.modules.project.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.repository.ProjectRepository;
import com.lumira.saas.modules.project.vo.ProjectVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProjectManagementAppService {
    private static final String LOCALE_DICT_CODE = "aiadc_project_locale";
    private static final String STATUS_DICT_CODE = "aiadc_project_status";
    private static final String RATING_DICT_CODE = "aiadc_project_rating";
    private static final String FILTER_ALL_DICT_CODE = "aiadc_project_filter_all";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String VIEW = "aiadc:project:view";
    private static final String CREATE = "aiadc:project:create";
    private static final String UPDATE = "aiadc:project:update";
    private static final String DELETE = "aiadc:project:delete";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_OWNER_LENGTH = 128;
    private static final int MAX_LABEL_LENGTH = 64;

    private final ProjectRepository projectRepository;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public ProjectManagementAppService(
            ProjectRepository projectRepository,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(projectRepository, permissionSnapshotService, null, sessionAuthenticationService, true);
    }

    public ProjectManagementAppService(
            ProjectRepository projectRepository,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(projectRepository, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private ProjectManagementAppService(
            ProjectRepository projectRepository,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.projectRepository = projectRepository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public ProjectManagementAppService(
            ProjectRepository projectRepository,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(projectRepository, permissionSnapshotService, null, null, false);
    }

    public ProjectManagementAppService(ProjectRepository projectRepository) {
        this(projectRepository, null, null, null, false);
    }

    public PageResponse<ProjectVO.Project> listProjects(
            CurrentUser currentUser,
            String keyword,
            String category,
            String ownerName,
            String rating,
            String status,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        requireAnyPermission(currentUser, VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String filterAll = requiredDictValues(FILTER_ALL_DICT_CODE).getFirst();
        String normalizedCategory = normalizeOptionalFilter(category, filterAll);
        String normalizedRating = normalizeOptionalFilter(rating, filterAll);
        if (normalizedRating != null) {
            normalizedRating = normalizeEnum(normalizedRating, null, Set.copyOf(requiredDictValues(RATING_DICT_CODE)), "Invalid project rating");
        }
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(status, null, Set.copyOf(requiredDictValues(STATUS_DICT_CODE)), "Invalid project status") : null;
        String normalizedLocale = StringUtils.hasText(locale)
                ? normalizeEnum(locale, null, Set.copyOf(requiredDictValues(LOCALE_DICT_CODE)), "Invalid project locale") : null;
        ProjectRepository.PageData page = projectRepository.search(keyword, normalizedCategory, ownerName,
                normalizedRating, normalizedStatus, normalizedLocale, featured,
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);

        PageResponse<ProjectVO.Project> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    public ProjectVO.Project getProject(CurrentUser currentUser, Long id) {
        requireAnyPermission(currentUser, VIEW, REGISTRATION_VIEW, REGISTRATION_CREATE);
        requirePositiveId(id, "Project id is required");
        ProjectVO.Project project = findProject(id);
        if (project == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return project;
    }

    @Transactional
    public ProjectVO.Project createProject(CurrentUser currentUser, ProjectDTO.ProjectUpsertRequest request) {
        Long userId = requireAnyPermission(currentUser, CREATE, REGISTRATION_CREATE);
        String userUuid = currentUser.getUserUuid().trim();
        requireRequest(request);
        ProjectDTO.ProjectUpsertRequest normalized = normalizeRequest(request);
        Long id = projectRepository.create(normalized, userId, userUuid);
        requireProjectWrite(id == null ? 0 : 1);
        ProjectVO.Project createdProject = findProject(id);
        if (createdProject == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return createdProject;
    }

    @Transactional
    public ProjectVO.Project updateProject(CurrentUser currentUser, Long id, ProjectDTO.ProjectUpsertRequest request) {
        Long userId = requireAnyPermission(currentUser, UPDATE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Project id is required");
        requireRequest(request);
        ProjectVO.Project existing = findProject(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        ProjectDTO.ProjectUpsertRequest normalized = normalizeRequest(request);
        int updated = projectRepository.update(id, existing, normalized, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return getProject(currentUser, id);
    }

    @Transactional
    public boolean deleteProject(CurrentUser currentUser, Long id) {
        Long userId = requireAnyPermission(currentUser, DELETE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Project id is required");
        ProjectVO.Project existing = findProject(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        int updated = projectRepository.delete(id, existing, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return true;
    }

    private ProjectVO.Project findProject(Long id) {
        requirePositiveId(id, "Project id is required");
        return projectRepository.findById(id).orElse(null);
    }

    private void requireProjectWrite(int updated) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Project changed, please retry");
        }
    }

    private ProjectDTO.ProjectUpsertRequest normalizeRequest(ProjectDTO.ProjectUpsertRequest request) {
        ProjectDTO.ProjectUpsertRequest normalized = new ProjectDTO.ProjectUpsertRequest();
        normalized.setCode(trimRequired(request.getCode(), "Project code is required", MAX_CODE_LENGTH, "Project code is too long"));
        normalized.setTitle(trimRequired(request.getTitle(), "Project title is required", MAX_TITLE_LENGTH, "Project title is too long"));
        normalized.setCategory(trimRequired(request.getCategory(), "Project category is required", MAX_CATEGORY_LENGTH, "Project category is too long"));
        normalized.setDescription(trimOptional(request.getDescription(), MAX_LONG_TEXT_LENGTH, "Project description is too long"));
        normalized.setImageUrl(normalizeUrl(request.getImageUrl(), "Project image URL"));
        normalized.setOwnerName(trimOptional(request.getOwnerName(), MAX_OWNER_LENGTH, "Project owner name is too long"));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Project tags are too long"));
        normalized.setCtaLabel(trimOptional(request.getCtaLabel(), MAX_LABEL_LENGTH, "Project CTA label is too long"));
        normalized.setCtaHref(normalizeUrl(request.getCtaHref(), "Project CTA URL"));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        List<String> locales = requiredDictValues(LOCALE_DICT_CODE);
        List<String> ratings = requiredDictValues(RATING_DICT_CODE);
        List<String> statuses = requiredDictValues(STATUS_DICT_CODE);
        normalized.setLocale(normalizeEnum(request.getLocale(), locales.getFirst(), Set.copyOf(locales), "Invalid project locale"));
        normalized.setRating(normalizeEnum(request.getRating(), ratings.getFirst(), Set.copyOf(ratings), "Invalid project rating"));
        normalized.setStatus(normalizeEnum(request.getStatus(), statuses.getFirst(), Set.copyOf(statuses), "Invalid project status"));
        return normalized;
    }

    private Long requireAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Long actorUserId = currentUser.getUserId();
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (permissions.isEmpty()) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(",", permissionKeys));
        }
        if (permissions.contains("*")) {
            return actorUserId;
        }
        for (String permissionKey : permissionKeys) {
            if (permissions.contains(permissionKey)) {
                return actorUserId;
            }
        }
        throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(",", permissionKeys));
    }

    private String requireUserUuid(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserUuid().trim();
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshed = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    ),
                    "Login required"
            );
            copyTrustedCurrentUser(currentUser, refreshed);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
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
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
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

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess,
            String message
    ) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return authenticatedAccess.currentUser();
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
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private void requireRequest(ProjectDTO.ProjectUpsertRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Project request is required");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String normalizeOptionalFilter(String value, String filterAll) {
        if (!StringUtils.hasText(value) || filterAll.equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private List<String> requiredDictValues(String dictCode) {
        List<String> values = projectRepository.findEnabledDictValues(dictCode);
        if (values == null || values.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Project dictionary is not configured: " + dictCode);
        }
        return values;
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimRequired(String value, String requiredMessage, int maxLength, String tooLongMessage) {
        String trimmed = trimRequired(value, requiredMessage);
        if (trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, tooLongMessage);
        }
        return trimmed;
    }

    private String trimOptional(String value, int maxLength, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, tooLongMessage);
        }
        return trimmed;
    }

    private String normalizeUrl(String value, String fieldName) {
        String trimmed = trimOptional(value, MAX_URL_LENGTH, fieldName + " is too long");
        if (trimmed == null) {
            return null;
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("\\")) {
            return trimmed;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (URISyntaxException exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
        }
        throw biz(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
