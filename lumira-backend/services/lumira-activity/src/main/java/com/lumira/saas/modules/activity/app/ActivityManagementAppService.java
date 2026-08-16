package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.repository.ActivityRepository;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityPageResponse;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_LOCATION_LENGTH = 255;
    private static final int MAX_REGISTRATION_FIELDS = 50;
    private static final int MAX_REGISTRATION_OPTIONS = 100;
    private static final Set<String> REGISTRATION_FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "SELECT", "MULTI_SELECT", "MOBILE", "EMAIL"
    );

    private final ActivityRepository activityRepository;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final TransactionalEventOutboxPort transactionalEventOutboxPort;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            TransactionalEventOutboxPort transactionalEventOutboxPort
    ) {
        this(activityRepository, trustedCurrentUserResolver, true, transactionalEventOutboxPort);
    }

    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this(activityRepository, trustedCurrentUserResolver, enforceTrustedUserResolution, null);
    }

    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(activityRepository, trustedCurrentUserResolver, true, null);
    }

    public ActivityManagementAppService(
            ActivityRepository activityRepository,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution,
            TransactionalEventOutboxPort transactionalEventOutboxPort
    ) {
        this.activityRepository = activityRepository;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.transactionalEventOutboxPort = transactionalEventOutboxPort;
    }

    public ActivityManagementAppService(ActivityRepository activityRepository) {
        this(activityRepository, null, false, null);
    }

    public ActivityPageResponse<ActivityVO.Activity> listActivities(
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

    public ActivityPageResponse<ActivityVO.PublicActivity> listPublishedActivities(
            String keyword,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        String publicStatus = requiredDictValues(PUBLIC_STATUS_DICT_CODE).getFirst();
        ActivityPageResponse<ActivityVO.Activity> page = listActivitiesInternal(keyword, publicStatus, locale, featured, pageNo, pageSize);
        ActivityPageResponse<ActivityVO.PublicActivity> response = new ActivityPageResponse<>();
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
        CurrentUser trustedCurrentUser = requirePermission(currentUser, CREATE);
        Long userId = trustedCurrentUser.getUserId();
        String userUuid = requireUserUuid(trustedCurrentUser);
        requireRequest(request);
        ActivityDTO.ActivityUpsertRequest normalized = normalizeRequest(request, generateActivityCode(), List.of());
        Long id = activityRepository.create(normalized, userId, userUuid);
        requireActivityWrite(id == null ? 0 : 1);
        ActivityVO.Activity activity = getActivity(trustedCurrentUser, id);
        recordCatalogChange(activity, null, userId, userUuid, activity.getUpdatedAt());
        return activity;
    }

    @Transactional
    public ActivityVO.Activity updateActivity(CurrentUser currentUser, Long id, ActivityDTO.ActivityUpsertRequest request) {
        CurrentUser trustedCurrentUser = requirePermission(currentUser, UPDATE);
        Long userId = trustedCurrentUser.getUserId();
        String userUuid = requireUserUuid(trustedCurrentUser);
        requirePositiveId(id, "Activity id is required");
        requireRequest(request);
        ActivityVO.Activity existing = findActivity(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        ActivityDTO.ActivityUpsertRequest normalized = normalizeRequest(
                request,
                existing.getCode(),
                existing.getRegistrationFields() == null ? List.of() : existing.getRegistrationFields()
        );
        int updated = activityRepository.update(id, existing, normalized, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        ActivityVO.Activity activity = getActivity(trustedCurrentUser, id);
        recordCatalogChange(activity, existing.getStatus(), userId, userUuid, activity.getUpdatedAt());
        return activity;
    }

    @Transactional
    public boolean deleteActivity(CurrentUser currentUser, Long id) {
        CurrentUser trustedCurrentUser = requirePermission(currentUser, DELETE);
        Long userId = trustedCurrentUser.getUserId();
        String userUuid = requireUserUuid(trustedCurrentUser);
        requirePositiveId(id, "Activity id is required");
        ActivityVO.Activity existing = findActivity(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        int updated = activityRepository.delete(id, existing, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        recordCatalogChange(existing, existing.getStatus(), userId, userUuid, LocalDateTime.now(), true);
        return true;
    }

    private void recordCatalogChange(
            ActivityVO.Activity activity,
            String previousStatus,
            Long userId,
            String userUuid,
            LocalDateTime sourceUpdatedAt
    ) {
        recordCatalogChange(activity, previousStatus, userId, userUuid, sourceUpdatedAt, false);
    }

    private void recordCatalogChange(
            ActivityVO.Activity activity,
            String previousStatus,
            Long userId,
            String userUuid,
            LocalDateTime sourceUpdatedAt,
            boolean deleted
    ) {
        if (transactionalEventOutboxPort == null || activity == null || activity.getId() == null) {
            return;
        }
        String eventType;
        if (deleted || "published".equals(previousStatus) && !"published".equals(activity.getStatus())) {
            eventType = "archived".equals(activity.getStatus())
                    ? EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED
                    : EventCatalogEventTypes.CATALOG_ITEM_WITHDRAWN;
        } else if ("archived".equals(activity.getStatus())) {
            eventType = EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED;
        } else if ("published".equals(activity.getStatus())) {
            eventType = EventCatalogEventTypes.CATALOG_ITEM_UPSERTED;
        } else {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", userUuid);
        attributes.put("sourceType", "ACTIVITY");
        attributes.put("sourceId", activity.getId());
        attributes.put("sourceUuid", activity.getCode());
        attributes.put("locale", activity.getLocale());
        attributes.put("title", activity.getTitle());
        attributes.put("subtitle", activity.getSubtitle());
        attributes.put("summary", activity.getDescription());
        attributes.put("status", activity.getStatus());
        attributes.put("eventStart", activity.getActivityDate());
        attributes.put("eventTime", activity.getActivityTime());
        attributes.put("location", activity.getLocation());
        attributes.put("imageUrl", activity.getImageUrl());
        attributes.put("tags", activity.getTags());
        attributes.put("ctaLabel", activity.getCtaLabel());
        attributes.put("ctaHref", activity.getCtaHref());
        attributes.put("featured", Boolean.TRUE.equals(activity.getFeatured()));
        attributes.put("sort", activity.getSort() == null ? 100 : activity.getSort());
        if (sourceUpdatedAt != null) {
            attributes.put("sourceUpdatedAt", sourceUpdatedAt.toString());
        }
        transactionalEventOutboxPort.record(
                eventType,
                userId,
                "event-catalog.item",
                activity.getId(),
                attributes
        );
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

    private ActivityPageResponse<ActivityVO.Activity> listActivitiesInternal(
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

        ActivityPageResponse<ActivityVO.Activity> response = new ActivityPageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    private ActivityDTO.ActivityUpsertRequest normalizeRequest(
            ActivityDTO.ActivityUpsertRequest request,
            String fallbackCode,
            List<ActivityRegistrationField> fallbackRegistrationFields
    ) {
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
        normalized.setRegistrationFields(normalizeRegistrationFields(
                request.getRegistrationFields() == null ? fallbackRegistrationFields : request.getRegistrationFields()
        ));
        return normalized;
    }

    private List<ActivityRegistrationField> normalizeRegistrationFields(List<ActivityRegistrationField> fields) {
        List<ActivityRegistrationField> source = fields == null ? List.of() : fields;
        if (source.size() > MAX_REGISTRATION_FIELDS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many activity registration fields");
        }
        Set<String> fieldKeys = new HashSet<>();
        List<ActivityRegistrationField> normalized = new ArrayList<>(source.size());
        for (ActivityRegistrationField field : source) {
            if (field == null) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Activity registration field is required");
            }
            String fieldKey = trimRequired(field.getFieldKey(), "Registration field key is required", 64, "Registration field key is too long");
            if (!fieldKey.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field key is invalid");
            }
            if (!fieldKeys.add(fieldKey.toLowerCase(Locale.ROOT))) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field keys must be unique");
            }
            String fieldType = trimRequired(field.getFieldType(), "Registration field type is required")
                    .toUpperCase(Locale.ROOT);
            if (!REGISTRATION_FIELD_TYPES.contains(fieldType)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field type is invalid");
            }
            List<String> options = normalizeRegistrationFieldOptions(field.getOptions());
            if (("SELECT".equals(fieldType) || "MULTI_SELECT".equals(fieldType)) && options.isEmpty()) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Choice registration fields require options");
            }

            ActivityRegistrationField next = new ActivityRegistrationField();
            next.setFieldKey(fieldKey);
            next.setLabel(trimRequired(field.getLabel(), "Registration field label is required", 128, "Registration field label is too long"));
            next.setFieldType(fieldType);
            next.setPlaceholder(trimOptional(field.getPlaceholder(), 255, "Registration field placeholder is too long"));
            next.setDescription(trimOptional(field.getDescription(), 500, "Registration field description is too long"));
            next.setRequired(Boolean.TRUE.equals(field.getRequired()));
            next.setOptions(("SELECT".equals(fieldType) || "MULTI_SELECT".equals(fieldType)) ? options : List.of());
            normalized.add(next);
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeRegistrationFieldOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        if (options.size() > MAX_REGISTRATION_OPTIONS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration field options");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String option : options) {
            String value = trimRequired(option, "Registration field option is required", 128, "Registration field option is too long");
            if (!normalized.add(value)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field options must be unique");
            }
        }
        return List.copyOf(normalized);
    }

    private String generateActivityCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36);
        return "act-" + LocalDateTime.now().format(ACTIVITY_CODE_TIME_FORMATTER) + "-" + random;
    }

    private CurrentUser requirePermission(CurrentUser currentUser, String permissionKey) {
        CurrentUser trustedCurrentUser = requireAuthenticated(currentUser);
        Set<String> permissions = trustedCurrentUser.getPermissions() == null ? Set.of() : trustedCurrentUser.getPermissions();
        if (permissions == null || permissions.isEmpty() || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return trustedCurrentUser;
    }

    private CurrentUser requireAuthenticated(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        CurrentUser resolvedCurrentUser = resolveTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(resolvedCurrentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return resolvedCurrentUser;
    }

    private String requireUserUuid(CurrentUser currentUser) {
        return requireAuthenticated(currentUser).getUserUuid().trim();
    }

    private CurrentUser resolveTrustedCurrentUser(CurrentUser currentUser) {
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        return trustedCurrentUserResolver.resolve(currentUser);
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
        view.setRegistrationFields(activity.getRegistrationFields() == null ? List.of() : activity.getRegistrationFields());
        return view;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
