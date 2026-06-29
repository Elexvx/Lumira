package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CompetitionManagementAppService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final List<String> LOCALE_ORDER = List.of("zh", "en");
    private static final Set<String> STATUSES = Set.of("draft", "published", "archived");
    private static final Set<String> FEE_MODES = Set.of("TEAM", "MEMBER");
    private static final Set<String> CONFIG_ITEM_TYPES = Set.of(
            "AGREEMENT",
            "CONSENT",
            "REGISTRATION_FIELD",
            "TEAM_FIELD",
            "MEMBER_FIELD",
            "PROJECT_FIELD",
            "REQUIRED_FILE",
            "STAGE_MATERIAL",
            "TIMELINE"
    );
    private static final Map<String, Set<String>> SETTINGS_MODULE_TYPES = Map.of(
            "documents", Set.of("AGREEMENT", "CONSENT"),
            "fields", Set.of("REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD"),
            "files", Set.of("REQUIRED_FILE"),
            "stage-materials", Set.of("STAGE_MATERIAL"),
            "timeline", Set.of("TIMELINE")
    );
    private static final String COMPETITION_CATEGORY_DICT = "aiadc_competition_category";
    private static final String COMPETITION_LEVEL_DICT = "aiadc_competition_level";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter COMPETITION_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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

    public CompetitionVO.Settings getCompetitionSettings(CurrentUser currentUser, String competitionUuid) {
        requireAuthenticated(currentUser);
        CompetitionVO.Competition competition = requireCompetitionByUuid(competitionUuid);
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, requireUserId(currentUser));
        CompetitionVO.Settings settings = new CompetitionVO.Settings();
        settings.setCompetition(competition);
        settings.setActiveConfigSet(configSet);
        settings.setDocuments(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("documents")));
        settings.setFields(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("fields")));
        settings.setFiles(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("files")));
        settings.setStageMaterials(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("stage-materials")));
        settings.setTimeline(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("timeline")));
        return settings;
    }

    @Transactional
    public CompetitionVO.Competition createCompetition(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        String uuid = UUID.randomUUID().toString();
        String competitionNo = generateCompetitionNo();
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, competitionNo);
        jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            uuid, competition_no, code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, homepage_content, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                uuid,
                competitionNo,
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
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, userId);
        if ("published".equals(competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, configSet);
        }
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_COMPETITION", "BASIC", "Created competition " + competition.getCompetitionNo());
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition createCompetitionDraft(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        String uuid = UUID.randomUUID().toString();
        String competitionNo = generateCompetitionNo();
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeDraftRequest(request, competitionNo);
        jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            uuid, competition_no, code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, homepage_content, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                uuid,
                competitionNo,
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
                "draft",
                normalized.getFeeMode(),
                normalized.getEntryFeeMinor(),
                normalized.getCurrency(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                normalized.getSort(),
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        ensureCurrentConfigSet(competition, userId);
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_DRAFT", "BASIC", "Created competition draft " + competition.getCompetitionNo());
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition updateCompetition(CurrentUser currentUser, Long id, CompetitionDTO.CompetitionUpsertRequest request) {
        CompetitionVO.Competition existing = findCompetition(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, existing.getCompetitionNo());
        int updated = jdbcTemplate.update(
                """
                        update aiadc_competition
                        set locale = ?, title = ?, short_name = ?, category = ?, level = ?, competition_level = ?, organizer = ?, organizers_json = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, participation_scope = ?, participation_requirement = ?, schedule_json = ?,
                            description = ?, image_url = ?, contact_name = ?, contact_qr_code_url = ?, homepage_content = ?, tags = ?, status = ?,
                            fee_mode = ?, entry_fee_minor = ?, currency = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
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
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        if ("published".equals(competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, ensureCurrentConfigSet(competition, requireUserId(currentUser)));
        }
        recordConfigAudit(currentUser, competition.getUuid(), "UPDATE_COMPETITION", "BASIC", "Updated competition basic information");
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition updateCompetitionDraft(CurrentUser currentUser, Long id, CompetitionDTO.CompetitionUpsertRequest request) {
        CompetitionVO.Competition existing = findCompetition(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        if (!"draft".equals(existing.getStatus())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Only draft competition can be updated as draft");
        }
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeDraftRequest(request, existing.getCompetitionNo());
        int updated = jdbcTemplate.update(
                """
                        update aiadc_competition
                        set locale = ?, title = ?, short_name = ?, category = ?, level = ?, competition_level = ?, organizer = ?, organizers_json = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, participation_scope = ?, participation_requirement = ?, schedule_json = ?,
                            description = ?, image_url = ?, contact_name = ?, contact_qr_code_url = ?, homepage_content = ?, tags = ?, status = 'draft',
                            fee_mode = ?, entry_fee_minor = ?, currency = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
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
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        recordConfigAudit(currentUser, competition.getUuid(), "UPDATE_DRAFT", "BASIC", "Updated competition draft");
        return competition;
    }

    @Transactional
    public CompetitionVO.Settings saveSettingsModule(
            CurrentUser currentUser,
            String competitionUuid,
            String module,
            CompetitionDTO.SettingsModuleRequest request
    ) {
        Long userId = requireUserId(currentUser);
        CompetitionVO.Competition competition = requireCompetitionByUuid(competitionUuid);
        Set<String> allowedTypes = SETTINGS_MODULE_TYPES.get(module);
        if (allowedTypes == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid competition settings module");
        }
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, userId);
        jdbcTemplate.update(
                "update competition_config_item set deleted = 1, updated_by = ?, updated_at = ? where competition_uuid = ? and config_set_id = ? and item_type in (" + placeholders(allowedTypes.size()) + ") and deleted = 0",
                concat(new Object[]{userId, LocalDateTime.now(), competition.getUuid(), configSet.getId()}, allowedTypes.toArray())
        );
        int index = 0;
        for (CompetitionDTO.ConfigItemRequest item : request.getItems()) {
            CompetitionDTO.ConfigItemRequest normalized = normalizeConfigItem(item);
            if (!allowedTypes.contains(normalized.getItemType())) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid item type for settings module");
            }
            jdbcTemplate.update(
                    """
                            insert into competition_config_item (
                                competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                                sort_order, required_flag, enabled, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    competition.getUuid(),
                    configSet.getId(),
                    normalized.getItemType(),
                    normalized.getItemKey(),
                    normalized.getTitle(),
                    trimToNull(normalized.getContentJson()),
                    trimToNull(normalized.getContentText()),
                    normalized.getSortOrder() == null ? (index + 1) * 10 : normalized.getSortOrder(),
                    Boolean.TRUE.equals(normalized.getRequiredFlag()) ? 1 : 0,
                    normalized.getEnabled() == null || Boolean.TRUE.equals(normalized.getEnabled()) ? 1 : 0,
                    userId,
                    userId
            );
            index += 1;
        }
        recordConfigAudit(currentUser, competition.getUuid(), "SAVE_SETTINGS", module.toUpperCase(Locale.ROOT), "Saved " + request.getItems().size() + " config items");
        return getCompetitionSettings(currentUser, competition.getUuid());
    }

    @Transactional
    public CompetitionVO.ConfigSet publishSettings(CurrentUser currentUser, String competitionUuid) {
        Long userId = requireUserId(currentUser);
        CompetitionVO.Competition competition = requireCompetitionByUuid(competitionUuid);
        CompetitionVO.ConfigSet current = ensureCurrentConfigSet(competition, userId);
        validateCompetitionReadyForPublish(competition, current);
        jdbcTemplate.update(
                "update competition_config_set set status = 'PUBLISHED', published_at = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                LocalDateTime.now(),
                userId,
                LocalDateTime.now(),
                current.getId()
        );
        jdbcTemplate.update(
                "update competition_config_set set status = 'ARCHIVED', updated_by = ?, updated_at = ? where competition_uuid = ? and id <> ? and deleted = 0",
                userId,
                LocalDateTime.now(),
                competition.getUuid(),
                current.getId()
        );
        recordConfigAudit(currentUser, competition.getUuid(), "PUBLISH_SETTINGS", "PUBLISH", "Published current config");
        return requireConfigSet(current.getId());
    }

    @Transactional
    public boolean deleteCompetition(CurrentUser currentUser, Long id) {
        Long registrationCount = jdbcTemplate.queryForObject(
                "select count(1) from competition_registration where competition_id = ? and deleted = 0",
                Long.class,
                id
        );
        if (registrationCount != null && registrationCount > 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "赛事已有报名记录，不能删除");
        }
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

    private CompetitionVO.Competition requireCompetitionByUuid(String competitionUuid) {
        String normalized = trimRequired(competitionUuid, "Competition uuid is required");
        List<CompetitionVO.Competition> records = jdbcTemplate.query(
                competitionSelect() + " from aiadc_competition where uuid = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                normalized
        );
        if (records.isEmpty()) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return records.get(0);
    }

    private void validateCompetitionReadyForPublish(CompetitionVO.Competition competition, CompetitionVO.ConfigSet configSet) {
        List<String> missing = new ArrayList<>();
        requirePublishText(missing, competition.getTitle(), "基础信息：竞赛名称未填写");
        requirePublishText(missing, competition.getCategory(), "基础信息：竞赛类别未选择");
        requirePublishText(missing, firstText(competition.getCompetitionLevel(), competition.getLevel()), "基础信息：竞赛级别未选择");
        requirePublishText(missing, competition.getParticipationScope(), "基础信息：参赛范围未填写");
        requirePublishText(missing, competition.getFeeMode(), "基础信息：收费方式未选择");
        requirePublishText(missing, competition.getCurrency(), "基础信息：货币未选择");
        requirePublishText(missing, competition.getRegistrationStart(), "赛事时间：报名开始时间未选择");
        requirePublishText(missing, competition.getRegistrationEnd(), "赛事时间：报名结束时间未选择");
        requirePublishText(missing, competition.getCompetitionStart(), "赛事时间：竞赛安排未配置");

        List<CompetitionVO.ConfigItem> items = listConfigItems(competition.getUuid(), configSet.getId(), CONFIG_ITEM_TYPES);
        validateEnabledConfigItems(missing, items);
        if (!missing.isEmpty()) {
            String message = missing.stream().limit(8).collect(Collectors.joining("；"));
            if (missing.size() > 8) {
                message += "；等 " + missing.size() + " 项";
            }
            throw biz(ErrorCode.VALIDATION_ERROR, "发布前请完善配置：" + message);
        }
    }

    private void validateEnabledConfigItems(List<String> missing, List<CompetitionVO.ConfigItem> items) {
        for (CompetitionVO.ConfigItem item : items) {
            if (Boolean.FALSE.equals(item.getEnabled())) {
                continue;
            }
            String moduleLabel = configItemModuleLabel(item.getItemType());
            String itemLabel = itemLabel(item);
            requirePublishText(missing, item.getTitle(), moduleLabel + "：" + itemLabel + "名称未填写");
            requirePublishText(missing, item.getItemKey(), moduleLabel + "：" + itemLabel + "标识未生成");
            if ("AGREEMENT".equals(item.getItemType()) || "CONSENT".equals(item.getItemType())) {
                requirePublishText(missing, item.getContentText(), moduleLabel + "：" + itemLabel + "内容未填写");
            } else if (Set.of("REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD").contains(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "fieldType"), moduleLabel + "：" + itemLabel + "字段类型未选择");
            } else if ("REQUIRED_FILE".equals(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "fileFormat"), moduleLabel + "：" + itemLabel + "文件格式未配置");
            } else if ("STAGE_MATERIAL".equals(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "stageName"), moduleLabel + "：" + itemLabel + "赛事阶段未填写");
                requirePublishText(missing, metadataValue(item, "materialType"), moduleLabel + "：" + itemLabel + "材料类型未选择");
            } else if ("TIMELINE".equals(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "timelineKind"), moduleLabel + "：" + itemLabel + "时间类型未选择");
            }
        }
    }

    private void requirePublishText(List<String> missing, String value, String message) {
        if (!StringUtils.hasText(value)) {
            missing.add(message);
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String itemLabel(CompetitionVO.ConfigItem item) {
        return StringUtils.hasText(item.getTitle()) ? item.getTitle().trim() : configItemModuleLabel(item.getItemType()) + "配置项";
    }

    private String configItemModuleLabel(String itemType) {
        return switch (itemType == null ? "" : itemType) {
            case "AGREEMENT", "CONSENT" -> "文书配置";
            case "REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD" -> "收集字段";
            case "REQUIRED_FILE" -> "上传文件";
            case "STAGE_MATERIAL" -> "阶段材料";
            case "TIMELINE" -> "赛事时间";
            default -> "赛事配置";
        };
    }

    private String metadataValue(CompetitionVO.ConfigItem item, String key) {
        if (!StringUtils.hasText(item.getContentJson())) {
            return null;
        }
        try {
            JsonNode value = OBJECT_MAPPER.readTree(item.getContentJson()).path(key);
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            String text = value.asText(null);
            return StringUtils.hasText(text) ? text : null;
        } catch (Exception ignored) {
            return null;
        }
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

    private CompetitionDTO.CompetitionUpsertRequest normalizeDraftRequest(CompetitionDTO.CompetitionUpsertRequest request, String fallbackCode) {
        CompetitionDTO.CompetitionUpsertRequest normalized = new CompetitionDTO.CompetitionUpsertRequest();
        String competitionLevel = dictRuntimeService.normalizeValue(
                COMPETITION_LEVEL_DICT,
                request.getCompetitionLevel(),
                request.getLevel(),
                true,
                "Invalid competition level"
        );
        String category = dictRuntimeService.normalizeValue(
                COMPETITION_CATEGORY_DICT,
                request.getCategory(),
                null,
                true,
                "Invalid competition category"
        );
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? request.getCode().trim()
                : trimRequired(fallbackCode, "Competition code is required"));
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid competition locale"));
        normalized.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : "未命名赛事草稿");
        normalized.setShortName(trimToNull(request.getShortName()));
        normalized.setCategory(trimToNull(category) == null ? "OTHER" : trimToNull(category));
        normalized.setCompetitionLevel(trimToNull(competitionLevel));
        normalized.setLevel(trimToNull(competitionLevel == null ? request.getLevel() : competitionLevel));
        normalized.setOrganizer(trimToNull(request.getOrganizer()));
        normalized.setOrganizersJson(trimToNull(request.getOrganizersJson()));
        normalized.setRegistrationStart(trimToNull(request.getRegistrationStart()));
        normalized.setRegistrationEnd(trimToNull(request.getRegistrationEnd()));
        normalized.setCompetitionStart(StringUtils.hasText(request.getCompetitionStart()) ? request.getCompetitionStart().trim() : "TBD");
        normalized.setCompetitionEnd(trimToNull(request.getCompetitionEnd()));
        normalized.setLocation(StringUtils.hasText(request.getLocation()) ? request.getLocation().trim() : "TBD");
        normalized.setParticipationScope(trimToNull(request.getParticipationScope()));
        normalized.setParticipationRequirement(trimToNull(request.getParticipationRequirement()));
        normalized.setScheduleJson(trimToNull(request.getScheduleJson()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setImageUrl(trimToNull(request.getImageUrl()));
        normalized.setContactName(trimToNull(request.getContactName()));
        normalized.setContactQrCodeUrl(trimToNull(request.getContactQrCodeUrl()));
        normalized.setHomepageContent(trimToNull(request.getHomepageContent()));
        normalized.setTags(trimToNull(request.getTags()));
        normalized.setStatus("draft");
        normalized.setFeeMode(normalizeFeeMode(request.getFeeMode()));
        normalized.setEntryFeeMinor(normalizeEntryFeeMinor(request.getEntryFeeMinor()));
        normalized.setCurrency(normalizeCurrency(request.getCurrency()));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String generateCompetitionNo() {
        for (int i = 0; i < 8; i += 1) {
            String candidate = LocalDateTime.now().format(COMPETITION_NO_TIME_FORMATTER)
                    + String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(0, 10000));
            Long count = jdbcTemplate.queryForObject(
                    "select count(1) from aiadc_competition where competition_no = ? and deleted = 0",
                    Long.class,
                    candidate
            );
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw biz(ErrorCode.BIZ_ERROR, "Failed to generate competition number");
    }

    private CompetitionVO.ConfigSet ensureCurrentConfigSet(CompetitionVO.Competition competition, Long userId) {
        List<CompetitionVO.ConfigSet> configSets = jdbcTemplate.query(
                configSetSelect() + " from competition_config_set where competition_uuid = ? and status in ('DRAFT', 'PUBLISHED') and deleted = 0 order by id desc limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigSet.class),
                competition.getUuid()
        );
        if (!configSets.isEmpty()) {
            return configSets.get(0);
        }
        jdbcTemplate.update(
                """
                        insert into competition_config_set (
                            competition_uuid, version, status, created_by_uuid, created_by, updated_by, deleted
                        ) values (?, 1, 'DRAFT', ?, ?, ?, 0)
                        """,
                competition.getUuid(),
                userUuidOrNull(userId),
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        seedDefaultConfigItems(competition.getUuid(), id, userId);
        return requireConfigSet(id);
    }

    private void seedDefaultConfigItems(String competitionUuid, Long configSetId, Long userId) {
        Long existing = jdbcTemplate.queryForObject(
                "select count(1) from competition_config_item where competition_uuid = ? and deleted = 0",
                Long.class,
                competitionUuid
        );
        if (existing != null && existing > 0) {
            return;
        }
        insertConfigItem(competitionUuid, configSetId, "AGREEMENT", "commitment", "承诺书", "", "请在这里配置赛事承诺书。", 10, true, true, userId);
        insertConfigItem(competitionUuid, configSetId, "CONSENT", "informed-consent", "知情同意", "", "请在这里配置知情同意内容。", 20, true, true, userId);
        insertConfigItem(competitionUuid, configSetId, "REGISTRATION_FIELD", "contact-name", "联系人姓名", "{\"type\":\"input\",\"target\":\"registration\"}", null, 10, true, true, userId);
        insertConfigItem(competitionUuid, configSetId, "REQUIRED_FILE", "work-file", "作品文件", "{\"accept\":\"*\",\"maxSizeMb\":100,\"maxCount\":1}", null, 10, true, true, userId);
    }

    private void insertConfigItem(String competitionUuid, Long configSetId, String itemType, String itemKey, String title, String contentJson, String contentText, int sortOrder, boolean required, boolean enabled, Long userId) {
        jdbcTemplate.update(
                """
                        insert into competition_config_item (
                            competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                            sort_order, required_flag, enabled, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                competitionUuid,
                configSetId,
                itemType,
                itemKey,
                title,
                contentJson,
                contentText,
                sortOrder,
                required ? 1 : 0,
                enabled ? 1 : 0,
                userId,
                userId
        );
    }

    private CompetitionVO.ConfigSet requireConfigSet(Long id) {
        List<CompetitionVO.ConfigSet> records = jdbcTemplate.query(
                configSetSelect() + " from competition_config_set where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigSet.class),
                id
        );
        if (records.isEmpty()) {
            throw biz(ErrorCode.NOT_FOUND, "Competition config set not found");
        }
        return records.get(0);
    }

    private List<CompetitionVO.ConfigItem> listConfigItems(String competitionUuid, Long configSetId, Set<String> itemTypes) {
        return jdbcTemplate.query(
                configItemSelect() + " from competition_config_item where competition_uuid = ? and config_set_id = ? and item_type in (" + placeholders(itemTypes.size()) + ") and deleted = 0 order by sort_order asc, id asc",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigItem.class),
                concat(new Object[]{competitionUuid, configSetId}, itemTypes.toArray())
        );
    }

    private CompetitionDTO.ConfigItemRequest normalizeConfigItem(CompetitionDTO.ConfigItemRequest request) {
        CompetitionDTO.ConfigItemRequest normalized = new CompetitionDTO.ConfigItemRequest();
        String itemType = trimRequired(request.getItemType(), "Config item type is required").toUpperCase(Locale.ROOT);
        if (!CONFIG_ITEM_TYPES.contains(itemType)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid config item type");
        }
        normalized.setItemType(itemType);
        normalized.setItemKey(trimRequired(request.getItemKey(), "Config item key is required"));
        normalized.setTitle(trimRequired(request.getTitle(), "Config item title is required"));
        normalized.setContentJson(trimToNull(request.getContentJson()));
        normalized.setContentText(trimToNull(request.getContentText()));
        normalized.setSortOrder(request.getSortOrder());
        normalized.setRequiredFlag(Boolean.TRUE.equals(request.getRequiredFlag()));
        normalized.setEnabled(request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()));
        return normalized;
    }

    private void recordConfigAudit(CurrentUser currentUser, String competitionUuid, String action, String module, String detail) {
        Long userId = currentUser == null ? 0L : currentUser.getUserId();
        jdbcTemplate.update(
                """
                        insert into competition_config_audit (
                            competition_uuid, operator_user_id, operator_user_uuid, action, module, detail_message, created_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                competitionUuid,
                userId == null ? 0L : userId,
                userUuidOrNull(userId),
                action,
                module,
                detail,
                userId == null ? 0L : userId
        );
    }

    private String userUuidOrNull(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        List<String> uuids = jdbcTemplate.query(
                "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                (rs, rowNum) -> rs.getString("uuid"),
                userId
        );
        return uuids.isEmpty() ? null : uuids.get(0);
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
                select id, uuid, competition_no as competitionNo, code, locale, title, short_name as shortName,
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

    private String configSetSelect() {
        return """
                select id, competition_uuid as competitionUuid, version, status, published_at as publishedAt,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private String configItemSelect() {
        return """
                select id, competition_uuid as competitionUuid, config_set_id as configSetId, item_type as itemType,
                       item_key as itemKey, title, content_json as contentJson, content_text as contentText,
                       sort_order as sortOrder, required_flag as requiredFlag, enabled,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    private Object[] concat(Object[] first, Object[] second) {
        Object[] result = new Object[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
