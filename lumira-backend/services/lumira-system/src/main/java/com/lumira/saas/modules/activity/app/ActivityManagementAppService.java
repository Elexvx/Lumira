package com.lumira.saas.modules.activity.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.activity.repository.ActivityRepository;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ActivityManagementAppService {
    private static final String LOCALE_DICT_CODE = "aiadc_activity_locale";
    private static final String STATUS_DICT_CODE = "aiadc_activity_status";
    private static final String PUBLIC_STATUS_DICT_CODE = "aiadc_activity_public_status";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter ACTIVITY_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String VIEW = "aiadc:activity:view";
    private static final String CREATE = "aiadc:activity:create";
    private static final String UPDATE = "aiadc:activity:update";
    private static final String DELETE = "aiadc:activity:delete";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_LOCATION_LENGTH = 255;

    private final ActivityRepository activityRepository;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(activityRepository, permissionSnapshotService, null, sessionAuthenticationService, true);
    }

    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(activityRepository, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private ActivityManagementAppService(
            ActivityRepository activityRepository,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.activityRepository = activityRepository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(activityRepository, permissionSnapshotService, null, null, false);
    }

    public ActivityManagementAppService(ActivityRepository activityRepository) {
        this(activityRepository, null, null, null, false);
    }

    public PageResponse<ActivityVO.Activity> listActivities(
            CurrentUser currentUser,
            String keyword,
            String status,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        requirePermission(currentUser, VIEW);
        return listActivitiesInternal(keyword, status, locale, featured, pageNo, pageSize);
    }

    public PageResponse<ActivityVO.PublicActivity> listPublishedActivities(
            String keyword,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        String publicStatus = requiredDictValues(PUBLIC_STATUS_DICT_CODE).getFirst();
        PageResponse<ActivityVO.Activity> page = listActivitiesInternal(keyword, publicStatus, locale, featured, pageNo, pageSize);
        PageResponse<ActivityVO.PublicActivity> response = new PageResponse<>();
        response.setRecords(page.getRecords().stream().map(this::toPublicActivity).toList());
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setHasMore(page.getHasMore());
        return response;
    }

    public ActivityVO.Activity getActivity(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, VIEW);
        requirePositiveId(id, "Activity id is required");
        ActivityVO.Activity activity = findActivity(id);
        if (activity == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return activity;
    }

    @Transactional
    public ActivityVO.Activity createActivity(CurrentUser currentUser, ActivityDTO.ActivityUpsertRequest request) {
        Long userId = requirePermission(currentUser, CREATE);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request);
        ActivityDTO.ActivityUpsertRequest normalized = normalizeRequest(request, generateActivityCode());
        Long id = activityRepository.create(normalized, userId, userUuid);
        requireActivityWrite(id == null ? 0 : 1);
        return getActivity(currentUser, id);
    }

    @Transactional
    public ActivityVO.Activity updateActivity(CurrentUser currentUser, Long id, ActivityDTO.ActivityUpsertRequest request) {
        Long userId = requirePermission(currentUser, UPDATE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Activity id is required");
        requireRequest(request);
        ActivityVO.Activity existing = findActivity(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        ActivityDTO.ActivityUpsertRequest normalized = normalizeRequest(request, existing.getCode());
        int updated = activityRepository.update(id, existing, normalized, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return getActivity(currentUser, id);
    }

    @Transactional
    public boolean deleteActivity(CurrentUser currentUser, Long id) {
        Long userId = requirePermission(currentUser, DELETE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Activity id is required");
        ActivityVO.Activity existing = findActivity(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        int updated = activityRepository.delete(id, existing, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return true;
    }

    private ActivityVO.Activity findActivity(Long id) {
        requirePositiveId(id, "Activity id is required");
        return activityRepository.findById(id).orElse(null);
    }

    private void requireActivityWrite(int updated) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Activity changed, please retry");
        }
    }

    private PageResponse<ActivityVO.Activity> listActivitiesInternal(
            String keyword,
            String status,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(status, null, Set.copyOf(requiredDictValues(STATUS_DICT_CODE)), "Invalid activity status") : null;
        String normalizedLocale = StringUtils.hasText(locale)
                ? normalizeEnum(locale, null, Set.copyOf(requiredDictValues(LOCALE_DICT_CODE)), "Invalid activity locale") : null;
        ActivityRepository.PageData page = activityRepository.search(keyword, normalizedStatus, normalizedLocale, featured,
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);

        PageResponse<ActivityVO.Activity> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    private ActivityDTO.ActivityUpsertRequest normalizeRequest(ActivityDTO.ActivityUpsertRequest request, String fallbackCode) {
        ActivityDTO.ActivityUpsertRequest normalized = new ActivityDTO.ActivityUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? trimRequired(request.getCode(), "Activity code is required", MAX_CODE_LENGTH, "Activity code is too long")
                : trimRequired(fallbackCode, "Activity code is required"));
        normalized.setTitle(trimRequired(request.getTitle(), "Activity title is required", MAX_TITLE_LENGTH, "Activity title is too long"));
        normalized.setSubtitle(trimOptional(request.getSubtitle(), MAX_SHORT_TEXT_LENGTH, "Activity subtitle is too long"));
        normalized.setDescription(trimOptional(request.getDescription(), MAX_LONG_TEXT_LENGTH, "Activity description is too long"));
        normalized.setImageUrl(normalizeUrl(request.getImageUrl(), "Activity image URL"));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Activity tags are too long"));
        normalized.setCtaLabel(trimOptional(request.getCtaLabel(), MAX_SHORT_TEXT_LENGTH, "Activity CTA label is too long"));
        normalized.setCtaHref(normalizeUrl(request.getCtaHref(), "Activity CTA URL"));
        normalized.setActivityDate(trimRequired(request.getActivityDate(), "Activity date is required", MAX_SHORT_TEXT_LENGTH, "Activity date is too long"));
        normalized.setActivityTime(trimRequired(request.getActivityTime(), "Activity time is required", MAX_SHORT_TEXT_LENGTH, "Activity time is too long"));
        normalized.setLocation(trimRequired(request.getLocation(), "Activity location is required", MAX_LOCATION_LENGTH, "Activity location is too long"));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        List<String> locales = requiredDictValues(LOCALE_DICT_CODE);
        List<String> statuses = requiredDictValues(STATUS_DICT_CODE);
        normalized.setLocale(normalizeLocales(request.getLocale(), locales, "Invalid activity locale"));
        normalized.setStatus(normalizeEnum(request.getStatus(), statuses.getFirst(), Set.copyOf(statuses), "Invalid activity status"));
        return normalized;
    }

    private String generateActivityCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36);
        return "act-" + LocalDateTime.now().format(ACTIVITY_CODE_TIME_FORMATTER) + "-" + random;
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        requireAuthenticated(currentUser);
        Long actorUserId = currentUser.getUserId();
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (permissions == null || permissions.isEmpty() || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return actorUserId;
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private String requireUserUuid(CurrentUser currentUser) {
        requireAuthenticated(currentUser);
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
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
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

    private void requireRequest(ActivityDTO.ActivityUpsertRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Activity request is required");
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

    private String normalizeLocales(String value, List<String> orderedValues, String message) {
        String defaultValue = orderedValues.getFirst();
        Set<String> allowed = Set.copyOf(orderedValues);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            selected.add(normalizeEnum(part, null, allowed, message));
        }
        if (selected.isEmpty()) {
            return defaultValue;
        }
        List<String> ordered = orderedValues.stream().filter(selected::contains).toList();
        return String.join(",", ordered);
    }

    private List<String> requiredDictValues(String dictCode) {
        List<String> values = activityRepository.findEnabledDictValues(dictCode);
        if (values == null || values.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Activity dictionary is not configured: " + dictCode);
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

    private ActivityVO.PublicActivity toPublicActivity(ActivityVO.Activity activity) {
        ActivityVO.PublicActivity view = new ActivityVO.PublicActivity();
        view.setId(activity.getId());
        view.setLocale(activity.getLocale());
        view.setTitle(activity.getTitle());
        view.setSubtitle(activity.getSubtitle());
        view.setDescription(activity.getDescription());
        view.setImageUrl(activity.getImageUrl());
        view.setTags(activity.getTags());
        view.setCtaLabel(activity.getCtaLabel());
        view.setCtaHref(activity.getCtaHref());
        view.setActivityDate(activity.getActivityDate());
        view.setActivityTime(activity.getActivityTime());
        view.setLocation(activity.getLocation());
        view.setFeatured(activity.getFeatured());
        return view;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
