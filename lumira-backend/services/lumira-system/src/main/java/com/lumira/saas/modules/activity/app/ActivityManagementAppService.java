package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final MyBatisQueryOperations jdbcTemplate;

    public ActivityManagementAppService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        requireAuthenticated(currentUser);
        return listActivitiesInternal(keyword, status, locale, featured, pageNo, pageSize);
    }

    public PageResponse<ActivityVO.Activity> listPublishedActivities(
            String keyword,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        return listActivitiesInternal(keyword, "published", locale, featured, pageNo, pageSize);
    }

    public ActivityVO.Activity getActivity(CurrentUser currentUser, Long id) {
        requireAuthenticated(currentUser);
        ActivityVO.Activity activity = findActivity(id);
        if (activity == null) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return activity;
    }

    @Transactional
    public ActivityVO.Activity createActivity(CurrentUser currentUser, ActivityDTO.ActivityUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        ActivityDTO.ActivityUpsertRequest normalized = normalizeRequest(request, generateActivityCode());
        jdbcTemplate.update(
                """
                        insert into aiadc_activity (
                            code, locale, title, subtitle, description, image_url,
                            sort, status, tags, cta_label, cta_href,
                            activity_date, activity_time, location, featured, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getActivity(currentUser, id);
    }

    @Transactional
    public ActivityVO.Activity updateActivity(CurrentUser currentUser, Long id, ActivityDTO.ActivityUpsertRequest request) {
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
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
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
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return getActivity(currentUser, id);
    }

    @Transactional
    public boolean deleteActivity(CurrentUser currentUser, Long id) {
        int updated = jdbcTemplate.update(
                "update aiadc_activity set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Activity not found");
        }
        return true;
    }

    private ActivityVO.Activity findActivity(Long id) {
        List<ActivityVO.Activity> records = jdbcTemplate.query(
                activitySelect() + " from aiadc_activity where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ActivityVO.Activity.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
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
                ? request.getCode().trim()
                : trimRequired(fallbackCode, "Activity code is required"));
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid activity locale"));
        normalized.setTitle(trimRequired(request.getTitle(), "Activity title is required"));
        normalized.setSubtitle(trimToNull(request.getSubtitle()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setImageUrl(trimToNull(request.getImageUrl()));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        normalized.setStatus(normalizeEnum(request.getStatus(), "draft", STATUSES, "Invalid activity status"));
        normalized.setTags(trimToNull(request.getTags()));
        normalized.setCtaLabel(trimToNull(request.getCtaLabel()));
        normalized.setCtaHref(trimToNull(request.getCtaHref()));
        normalized.setActivityDate(trimRequired(request.getActivityDate(), "Activity date is required"));
        normalized.setActivityTime(trimRequired(request.getActivityTime(), "Activity time is required"));
        normalized.setLocation(trimRequired(request.getLocation(), "Activity location is required"));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        return normalized;
    }

    private String generateActivityCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36);
        return "act-" + LocalDateTime.now().format(ACTIVITY_CODE_TIME_FORMATTER) + "-" + random;
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
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

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
