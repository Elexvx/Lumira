package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PlatformContext;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CompetitionManagementAppService {
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final Set<String> STATUSES = Set.of("draft", "published", "archived");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter COMPETITION_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MyBatisQueryOperations jdbcTemplate;

    public CompetitionManagementAppService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<CompetitionVO.Competition> listCompetitions(
            CurrentUser currentUser,
            String keyword,
            String category,
            String status,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        Long tenantId = requireTenantId(currentUser);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_competition where tenant_id = ? and deleted = 0");
        params.add(tenantId);
        if (StringUtils.hasText(keyword)) {
            where.append(" and (title like ? or code like ? or organizer like ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(category)) {
            where.append(" and category = ?");
            params.add(category.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            params.add(normalizeEnum(status, null, STATUSES, "Invalid competition status"));
        }
        if (StringUtils.hasText(locale)) {
            where.append(" and locale = ?");
            params.add(normalizeEnum(locale, null, LOCALES, "Invalid competition locale"));
        }
        if (featured != null) {
            where.append(" and featured = ?");
            params.add(Boolean.TRUE.equals(featured) ? 1 : 0);
        }

        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((normalizedPageNo - 1) * normalizedPageSize);
        selectParams.add(normalizedPageSize);
        List<CompetitionVO.Competition> records = jdbcTemplate.query(
                competitionSelect() + where + " order by sort asc, featured desc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                selectParams.toArray()
        );

        PageResponse<CompetitionVO.Competition> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    public CompetitionVO.Competition getCompetition(CurrentUser currentUser, Long id) {
        CompetitionVO.Competition competition = findCompetition(requireTenantId(currentUser), id);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition createCompetition(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long userId = requireUserId(currentUser);
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, generateCompetitionCode());
        jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            tenant_id, code, locale, title, category, level, organizer,
                            registration_start, registration_end, competition_start, competition_end,
                            location, description, image_url, tags, status, featured, sort,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getCategory(),
                normalized.getLevel(),
                normalized.getOrganizer(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd(),
                normalized.getCompetitionStart(),
                normalized.getCompetitionEnd(),
                normalized.getLocation(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getTags(),
                normalized.getStatus(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                normalized.getSort(),
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getCompetition(currentUser, id);
    }

    @Transactional
    public CompetitionVO.Competition updateCompetition(CurrentUser currentUser, Long id, CompetitionDTO.CompetitionUpsertRequest request) {
        Long tenantId = requireTenantId(currentUser);
        CompetitionVO.Competition existing = findCompetition(tenantId, id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, existing.getCode());
        int updated = jdbcTemplate.update(
                """
                        update aiadc_competition
                        set code = ?, locale = ?, title = ?, category = ?, level = ?, organizer = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, description = ?, image_url = ?, tags = ?, status = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_at = ?
                        where tenant_id = ? and id = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getCategory(),
                normalized.getLevel(),
                normalized.getOrganizer(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd(),
                normalized.getCompetitionStart(),
                normalized.getCompetitionEnd(),
                normalized.getLocation(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getTags(),
                normalized.getStatus(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                normalized.getSort(),
                requireUserId(currentUser),
                LocalDateTime.now(),
                tenantId,
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return getCompetition(currentUser, id);
    }

    @Transactional
    public boolean deleteCompetition(CurrentUser currentUser, Long id) {
        int updated = jdbcTemplate.update(
                "update aiadc_competition set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                requireTenantId(currentUser),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return true;
    }

    private CompetitionVO.Competition findCompetition(Long tenantId, Long id) {
        List<CompetitionVO.Competition> records = jdbcTemplate.query(
                competitionSelect() + " from aiadc_competition where tenant_id = ? and id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                tenantId,
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CompetitionDTO.CompetitionUpsertRequest normalizeRequest(CompetitionDTO.CompetitionUpsertRequest request, String fallbackCode) {
        CompetitionDTO.CompetitionUpsertRequest normalized = new CompetitionDTO.CompetitionUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? request.getCode().trim()
                : trimRequired(fallbackCode, "Competition code is required"));
        normalized.setLocale(normalizeEnum(request.getLocale(), "zh", LOCALES, "Invalid competition locale"));
        normalized.setTitle(trimRequired(request.getTitle(), "Competition title is required"));
        normalized.setCategory(trimRequired(request.getCategory(), "Competition category is required"));
        normalized.setLevel(trimToNull(request.getLevel()));
        normalized.setOrganizer(trimToNull(request.getOrganizer()));
        normalized.setRegistrationStart(trimToNull(request.getRegistrationStart()));
        normalized.setRegistrationEnd(trimToNull(request.getRegistrationEnd()));
        normalized.setCompetitionStart(trimRequired(request.getCompetitionStart(), "Competition start time is required"));
        normalized.setCompetitionEnd(trimToNull(request.getCompetitionEnd()));
        normalized.setLocation(trimRequired(request.getLocation(), "Competition location is required"));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setImageUrl(trimToNull(request.getImageUrl()));
        normalized.setTags(trimToNull(request.getTags()));
        normalized.setStatus(normalizeEnum(request.getStatus(), "draft", STATUSES, "Invalid competition status"));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String generateCompetitionCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36);
        return "cmp-" + LocalDateTime.now().format(COMPETITION_CODE_TIME_FORMATTER) + "-" + random;
    }

    private Long requireTenantId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return PlatformContext.compatibilityTenantId();
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

    private String competitionSelect() {
        return """
                select id, tenant_id as tenantId, code, locale, title, category, level, organizer,
                       registration_start as registrationStart, registration_end as registrationEnd,
                       competition_start as competitionStart, competition_end as competitionEnd,
                       location, description, image_url as imageUrl, tags, status, featured, sort,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
