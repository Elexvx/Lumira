package com.lumira.saas.modules.activity.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ActivityManagementAppService {
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final List<String> LOCALE_ORDER = List.of("zh", "en");
    private static final Set<String> STATUSES = Set.of("draft", "published");
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Autowired
    public ActivityManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, permissionSnapshotService, null, sessionAuthenticationService);
    }

    public ActivityManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public ActivityManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, permissionSnapshotService, null);
    }

    public ActivityManagementAppService(MyBatisQueryOperations jdbcTemplate) {
        this(jdbcTemplate, null);
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
        PageResponse<ActivityVO.Activity> page = listActivitiesInternal(keyword, "published", locale, featured, pageNo, pageSize);
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
        int inserted = jdbcTemplate.update(
                """
                        insert into aiadc_activity (
                            code, locale, title, subtitle, description, image_url,
                            sort, status, tags, cta_label, cta_href,
                            activity_date, activity_time, location, featured,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getSubtitle(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getSort(),
                normalized.getStatus(),
                normalized.getTags(),
                normalized.getCtaLabel(),
                normalized.getCtaHref(),
                normalized.getActivityDate(),
                normalized.getActivityTime(),
                normalized.getLocation(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                userId,
                userUuid,
                userId,
                userUuid
        );
        requireActivityWrite(inserted);
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
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
        int updated = jdbcTemplate.update(
                """
                        update aiadc_activity
                        set code = ?, locale = ?, title = ?, subtitle = ?, description = ?, image_url = ?,
                            sort = ?, status = ?, tags = ?, cta_label = ?, cta_href = ?,
                            activity_date = ?, activity_time = ?, location = ?, featured = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and code = ? and locale = ? and status = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getSubtitle(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getSort(),
                normalized.getStatus(),
                normalized.getTags(),
                normalized.getCtaLabel(),
                normalized.getCtaHref(),
                normalized.getActivityDate(),
                normalized.getActivityTime(),
                normalized.getLocation(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                userId,
                userUuid,
                LocalDateTime.now(),
                id,
                existing.getCode(),
                existing.getLocale(),
                existing.getStatus()
        );
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
        int updated = jdbcTemplate.update(
                """
                        update aiadc_activity
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and code = ? and locale = ? and status = ? and deleted = 0
                        """,
                userId,
                userUuid,
                LocalDateTime.now(),
                id,
                existing.getCode(),
                existing.getLocale(),
                existing.getStatus()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return true;
    }

    private ActivityVO.Activity findActivity(Long id) {
        requirePositiveId(id, "Activity id is required");
        List<ActivityVO.Activity> records = jdbcTemplate.query(
                activitySelect() + " from aiadc_activity where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ActivityVO.Activity.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
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
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_activity where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (title like ? or code like ? or subtitle like ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            params.add(normalizeEnum(status, null, STATUSES, "Invalid activity status"));
        }
        if (StringUtils.hasText(locale)) {
            where.append(" and find_in_set(?, replace(locale, ' ', '')) > 0");
            params.add(normalizeEnum(locale, null, LOCALES, "Invalid activity locale"));
        }
        if (featured != null) {
            where.append(" and featured = ?");
            params.add(Boolean.TRUE.equals(featured) ? 1 : 0);
        }

        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((normalizedPageNo - 1) * normalizedPageSize);
        selectParams.add(normalizedPageSize);
        List<ActivityVO.Activity> records = jdbcTemplate.query(
                activitySelect() + where + " order by sort asc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ActivityVO.Activity.class),
                selectParams.toArray()
        );

        PageResponse<ActivityVO.Activity> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
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
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid activity locale"));
        normalized.setTitle(trimRequired(request.getTitle(), "Activity title is required", MAX_TITLE_LENGTH, "Activity title is too long"));
        normalized.setSubtitle(trimOptional(request.getSubtitle(), MAX_SHORT_TEXT_LENGTH, "Activity subtitle is too long"));
        normalized.setDescription(trimOptional(request.getDescription(), MAX_LONG_TEXT_LENGTH, "Activity description is too long"));
        normalized.setImageUrl(normalizeUrl(request.getImageUrl(), "Activity image URL"));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        normalized.setStatus(normalizeEnum(request.getStatus(), "draft", STATUSES, "Invalid activity status"));
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Activity tags are too long"));
        normalized.setCtaLabel(trimOptional(request.getCtaLabel(), MAX_SHORT_TEXT_LENGTH, "Activity CTA label is too long"));
        normalized.setCtaHref(normalizeUrl(request.getCtaHref(), "Activity CTA URL"));
        normalized.setActivityDate(trimRequired(request.getActivityDate(), "Activity date is required", MAX_SHORT_TEXT_LENGTH, "Activity date is too long"));
        normalized.setActivityTime(trimRequired(request.getActivityTime(), "Activity time is required", MAX_SHORT_TEXT_LENGTH, "Activity time is too long"));
        normalized.setLocation(trimRequired(request.getLocation(), "Activity location is required", MAX_LOCATION_LENGTH, "Activity location is too long"));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
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
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
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
        target.setSimulatedRoleId(source.getSimulatedRoleId());
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

    private String normalizeLocales(String value, String defaultValue, Set<String> allowed, String message) {
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
        List<String> ordered = LOCALE_ORDER.stream().filter(selected::contains).collect(Collectors.toList());
        return String.join(",", ordered);
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

    private String activitySelect() {
        return """
                select id, code, locale, title, subtitle, description,
                       image_url as imageUrl, sort, status, tags,
                       cta_label as ctaLabel, cta_href as ctaHref, activity_date as activityDate,
                       activity_time as activityTime, location, featured, created_at as createdAt,
                       updated_at as updatedAt
                """;
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
