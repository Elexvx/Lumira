package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
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
public class CompetitionManagementAppService {
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final List<String> LOCALE_ORDER = List.of("zh", "en");
    private static final Set<String> STATUSES = Set.of("draft", "published", "archived");
    private static final Set<String> FEE_MODES = Set.of("TEAM", "MEMBER");
    private static final String COMPETITION_CATEGORY_DICT = "aiadc_competition_category";
    private static final String COMPETITION_LEVEL_DICT = "aiadc_competition_level";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter COMPETITION_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MyBatisQueryOperations jdbcTemplate;
    private final DictRuntimeService dictRuntimeService;

    public CompetitionManagementAppService(MyBatisQueryOperations jdbcTemplate, DictRuntimeService dictRuntimeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dictRuntimeService = dictRuntimeService;
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
        requireAuthenticated(currentUser);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_competition where deleted = 0");
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
            where.append(" and find_in_set(?, replace(locale, ' ', '')) > 0");
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
        requireAuthenticated(currentUser);
        CompetitionVO.Competition competition = findCompetition(id);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition createCompetition(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, generateCompetitionCode());
        jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, homepage_content, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getShortName(),
                normalized.getCategory(),
                normalized.getLevel(),
                normalized.getCompetitionLevel(),
                normalized.getOrganizer(),
                normalized.getOrganizersJson(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd(),
                normalized.getCompetitionStart(),
                normalized.getCompetitionEnd(),
                normalized.getLocation(),
                normalized.getParticipationScope(),
                normalized.getParticipationRequirement(),
                normalized.getScheduleJson(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getContactName(),
                normalized.getContactQrCodeUrl(),
                normalized.getHomepageContent(),
                normalized.getTags(),
                normalized.getStatus(),
                normalized.getFeeMode(),
                normalized.getEntryFeeMinor(),
                normalized.getCurrency(),
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
        CompetitionVO.Competition existing = findCompetition(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, existing.getCode());
        int updated = jdbcTemplate.update(
                """
                        update aiadc_competition
                        set code = ?, locale = ?, title = ?, short_name = ?, category = ?, level = ?, competition_level = ?, organizer = ?, organizers_json = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, participation_scope = ?, participation_requirement = ?, schedule_json = ?,
                            description = ?, image_url = ?, contact_name = ?, contact_qr_code_url = ?, homepage_content = ?, tags = ?, status = ?,
                            fee_mode = ?, entry_fee_minor = ?, currency = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getShortName(),
                normalized.getCategory(),
                normalized.getLevel(),
                normalized.getCompetitionLevel(),
                normalized.getOrganizer(),
                normalized.getOrganizersJson(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd(),
                normalized.getCompetitionStart(),
                normalized.getCompetitionEnd(),
                normalized.getLocation(),
                normalized.getParticipationScope(),
                normalized.getParticipationRequirement(),
                normalized.getScheduleJson(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getContactName(),
                normalized.getContactQrCodeUrl(),
                normalized.getHomepageContent(),
                normalized.getTags(),
                normalized.getStatus(),
                normalized.getFeeMode(),
                normalized.getEntryFeeMinor(),
                normalized.getCurrency(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                normalized.getSort(),
                requireUserId(currentUser),
                LocalDateTime.now(),
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
                "update aiadc_competition set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return true;
    }

    private CompetitionVO.Competition findCompetition(Long id) {
        List<CompetitionVO.Competition> records = jdbcTemplate.query(
                competitionSelect() + " from aiadc_competition where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CompetitionDTO.CompetitionUpsertRequest normalizeRequest(CompetitionDTO.CompetitionUpsertRequest request, String fallbackCode) {
        CompetitionDTO.CompetitionUpsertRequest normalized = new CompetitionDTO.CompetitionUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? request.getCode().trim()
                : trimRequired(fallbackCode, "Competition code is required"));
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid competition locale"));
        normalized.setTitle(trimRequired(request.getTitle(), "Competition title is required"));
        normalized.setShortName(trimToNull(request.getShortName()));
        normalized.setCategory(dictRuntimeService.normalizeValue(
                COMPETITION_CATEGORY_DICT,
                trimRequired(request.getCategory(), "Competition category is required"),
                null,
                true,
                "Invalid competition category"
        ));
        String competitionLevel = dictRuntimeService.normalizeValue(
                COMPETITION_LEVEL_DICT,
                request.getCompetitionLevel(),
                request.getLevel(),
                true,
                "Invalid competition level"
        );
        normalized.setCompetitionLevel(trimToNull(competitionLevel));
        normalized.setLevel(trimToNull(competitionLevel == null ? request.getLevel() : competitionLevel));
        normalized.setOrganizer(trimToNull(request.getOrganizer()));
        normalized.setOrganizersJson(trimToNull(request.getOrganizersJson()));
        normalized.setRegistrationStart(trimToNull(request.getRegistrationStart()));
        normalized.setRegistrationEnd(trimToNull(request.getRegistrationEnd()));
        normalized.setCompetitionStart(trimRequired(request.getCompetitionStart(), "Competition start time is required"));
        normalized.setCompetitionEnd(trimToNull(request.getCompetitionEnd()));
        normalized.setLocation(trimRequired(request.getLocation(), "Competition location is required"));
        normalized.setParticipationScope(trimToNull(request.getParticipationScope()));
        normalized.setParticipationRequirement(trimToNull(request.getParticipationRequirement()));
        normalized.setScheduleJson(trimToNull(request.getScheduleJson()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setImageUrl(trimToNull(request.getImageUrl()));
        normalized.setContactName(trimToNull(request.getContactName()));
        normalized.setContactQrCodeUrl(trimToNull(request.getContactQrCodeUrl()));
        normalized.setHomepageContent(trimToNull(request.getHomepageContent()));
        normalized.setTags(trimToNull(request.getTags()));
        normalized.setStatus(normalizeEnum(request.getStatus(), "draft", STATUSES, "Invalid competition status"));
        normalized.setFeeMode(normalizeFeeMode(request.getFeeMode()));
        normalized.setEntryFeeMinor(normalizeEntryFeeMinor(request.getEntryFeeMinor()));
        normalized.setCurrency(normalizeCurrency(request.getCurrency()));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String generateCompetitionCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36);
        return "cmp-" + LocalDateTime.now().format(COMPETITION_CODE_TIME_FORMATTER) + "-" + random;
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

    private String normalizeFeeMode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "TEAM";
        if (!FEE_MODES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid competition fee mode");
        }
        return normalized;
    }

    private Long normalizeEntryFeeMinor(Long value) {
        long normalized = value == null ? 0L : value;
        if (normalized < 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Entry fee cannot be negative");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "CNY";
        if (normalized.length() > 16) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid currency");
        }
        return normalized;
    }

    private String competitionSelect() {
        return """
                select id, code, locale, title, short_name as shortName,
                       category, level, competition_level as competitionLevel, organizer, organizers_json as organizersJson,
                       registration_start as registrationStart, registration_end as registrationEnd,
                       competition_start as competitionStart, competition_end as competitionEnd,
                       location, participation_scope as participationScope, participation_requirement as participationRequirement,
                       schedule_json as scheduleJson, description, image_url as imageUrl,
                       contact_name as contactName, contact_qr_code_url as contactQrCodeUrl, homepage_content as homepageContent,
                       tags, status, fee_mode as feeMode, entry_fee_minor as entryFeeMinor, currency, featured, sort,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
