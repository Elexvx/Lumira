package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String COMPETITION_VIEW = "aiadc:competition:view";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String COMPETITION_CREATE = "aiadc:competition:create";
    private static final String COMPETITION_UPDATE = "aiadc:competition:update";
    private static final String COMPETITION_DELETE = "aiadc:competition:delete";
    private static final String STATUS_ENABLED = "ENABLED";
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
            "TEAM_SETTINGS",
            "PAYMENT_SETTINGS",
            "REQUIRED_FILE",
            "STAGE_MATERIAL",
            "TIMELINE"
    );
    private static final Map<String, Set<String>> SETTINGS_MODULE_TYPES = Map.of(
            "documents", Set.of("AGREEMENT", "CONSENT"),
            "fields", Set.of("TEAM_SETTINGS", "REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD"),
            "payments", Set.of("PAYMENT_SETTINGS"),
            "files", Set.of("REQUIRED_FILE"),
            "stage-materials", Set.of("STAGE_MATERIAL"),
            "materials", Set.of("REQUIRED_FILE", "STAGE_MATERIAL"),
            "timeline", Set.of("TIMELINE")
    );
    private static final String COMPETITION_CATEGORY_DICT = "aiadc_competition_category";
    private static final String COMPETITION_LEVEL_DICT = "aiadc_competition_level";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter COMPETITION_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_MID_TEXT_LENGTH = 255;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final int MAX_HOMEPAGE_CONTENT_LENGTH = 20000;
    private static final int MAX_JSON_LENGTH = 20000;
    private static final int MAX_URL_LENGTH = 512;

    private final MyBatisQueryOperations jdbcTemplate;
    private final DictRuntimeService dictRuntimeService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public CompetitionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, dictRuntimeService, permissionSnapshotService, null, sessionAuthenticationService, true);
    }

    public CompetitionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, dictRuntimeService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private CompetitionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dictRuntimeService = dictRuntimeService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public CompetitionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            DictRuntimeService dictRuntimeService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, dictRuntimeService, permissionSnapshotService, null, null, false);
    }

    public CompetitionManagementAppService(MyBatisQueryOperations jdbcTemplate, DictRuntimeService dictRuntimeService) {
        this(jdbcTemplate, dictRuntimeService, null, null, null, false);
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
        requireCompetitionListPermission(currentUser, status);
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
        requirePositiveId(id, "Competition id is required");
        requireUserId(currentUser);
        boolean canViewCompetition = hasPermission(currentUser, COMPETITION_VIEW);
        boolean canAccessPublishedCompetition = hasPermission(currentUser, REGISTRATION_VIEW) || hasPermission(currentUser, REGISTRATION_CREATE);
        if (!canViewCompetition && !canAccessPublishedCompetition) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + COMPETITION_VIEW);
        }
        CompetitionVO.Competition competition = findCompetition(id);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        if (!canViewCompetition && !"published".equals(competition.getStatus())) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return competition;
    }

    public CompetitionVO.Settings getCompetitionSettings(CurrentUser currentUser, String competitionUuid) {
        CompetitionVO.Competition competition = requireCompetitionSettingsAccess(currentUser, competitionUuid);
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, currentUser);
        CompetitionVO.Settings settings = new CompetitionVO.Settings();
        settings.setCompetition(competition);
        settings.setActiveConfigSet(configSet);
        settings.setDocuments(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("documents")));
        settings.setFields(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("fields")));
        settings.setPayments(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("payments")));
        settings.setFiles(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("files")));
        settings.setStageMaterials(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("stage-materials")));
        settings.setTimeline(listConfigItems(competition.getUuid(), configSet.getId(), SETTINGS_MODULE_TYPES.get("timeline")));
        return settings;
    }

    @Transactional
    public CompetitionVO.Competition createCompetition(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long userId = requirePermission(currentUser, COMPETITION_CREATE);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Competition request is required");
        normalizeRequest(request, "validation-probe");
        String uuid = UUID.randomUUID().toString();
        String competitionNo = generateCompetitionNo();
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, competitionNo);
        int inserted = jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            uuid, competition_no, code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, homepage_content, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                userUuid,
                userId,
                userUuid
        );
        requireCompetitionWrite(inserted, "Competition changed, please retry");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, currentUser);
        if ("published".equals(competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, configSet);
        }
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_COMPETITION", "BASIC", "Created competition " + competition.getCompetitionNo());
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition createCompetitionDraft(CurrentUser currentUser, CompetitionDTO.CompetitionUpsertRequest request) {
        Long userId = requirePermission(currentUser, COMPETITION_CREATE);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Competition request is required");
        normalizeDraftRequest(request, "validation-probe");
        String uuid = UUID.randomUUID().toString();
        String competitionNo = generateCompetitionNo();
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeDraftRequest(request, competitionNo);
        int inserted = jdbcTemplate.update(
                """
                        insert into aiadc_competition (
                            uuid, competition_no, code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, homepage_content, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                userUuid,
                userId,
                userUuid
        );
        requireCompetitionWrite(inserted, "Competition changed, please retry");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        ensureCurrentConfigSet(competition, currentUser);
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_DRAFT", "BASIC", "Created competition draft " + competition.getCompetitionNo());
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition updateCompetition(CurrentUser currentUser, Long id, CompetitionDTO.CompetitionUpsertRequest request) {
        requirePermission(currentUser, COMPETITION_UPDATE);
        requirePositiveId(id, "Competition id is required");
        requireRequest(request, "Competition request is required");
        CompetitionVO.Competition existing = findCompetition(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, existing);
        int updated = jdbcTemplate.update(
                """
                        update aiadc_competition
                        set locale = ?, title = ?, short_name = ?, category = ?, level = ?, competition_level = ?, organizer = ?, organizers_json = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, participation_scope = ?, participation_requirement = ?, schedule_json = ?,
                            description = ?, image_url = ?, contact_name = ?, contact_qr_code_url = ?, homepage_content = ?, tags = ?, status = ?,
                            fee_mode = ?, entry_fee_minor = ?, currency = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and competition_no = ? and status = ? and deleted = 0
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
                requireUserUuid(currentUser),
                LocalDateTime.now(),
                id,
                existing.getUuid(),
                existing.getCompetitionNo(),
                existing.getStatus()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        if (shouldValidatePublishTransition(existing.getStatus(), competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, ensureCurrentConfigSet(competition, currentUser));
        }
        recordConfigAudit(currentUser, competition.getUuid(), "UPDATE_COMPETITION", "BASIC", "Updated competition basic information");
        return competition;
    }

    @Transactional
    public CompetitionVO.Competition updateCompetitionDraft(CurrentUser currentUser, Long id, CompetitionDTO.CompetitionUpsertRequest request) {
        requirePermission(currentUser, COMPETITION_CREATE);
        requirePositiveId(id, "Competition id is required");
        requireRequest(request, "Competition request is required");
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
                            featured = ?, sort = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and competition_no = ? and status = 'draft' and deleted = 0
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
                requireUserUuid(currentUser),
                LocalDateTime.now(),
                id,
                existing.getUuid(),
                existing.getCompetitionNo()
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
        Long userId = requirePermission(currentUser, COMPETITION_UPDATE);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Competition settings request is required");
        Set<String> allowedTypes = SETTINGS_MODULE_TYPES.get(module);
        if (allowedTypes == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid competition settings module");
        }
        List<CompetitionDTO.ConfigItemRequest> normalizedItems = request.getItems().stream()
                .map(this::normalizeConfigItem)
                .toList();
        for (CompetitionDTO.ConfigItemRequest item : normalizedItems) {
            if (!allowedTypes.contains(item.getItemType())) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid item type for settings module");
            }
        }
        CompetitionVO.Competition competition = requireCompetitionByUuid(competitionUuid);
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, currentUser);
        Map<String, CompetitionVO.ConfigItem> existingByKey = listConfigItems(competition.getUuid(), configSet.getId(), allowedTypes)
                .stream()
                .collect(Collectors.toMap(this::configItemIdentity, item -> item, (left, right) -> left));
        Set<Long> retainedIds = new LinkedHashSet<>();
        Set<String> requestKeys = new LinkedHashSet<>();
        int index = 0;
        for (CompetitionDTO.ConfigItemRequest normalized : normalizedItems) {
            String identity = configItemIdentity(normalized);
            if (!requestKeys.add(identity)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Duplicate competition settings item key");
            }
            CompetitionVO.ConfigItem existing = existingByKey.get(identity);
            if (existing == null) {
                insertConfigItem(
                        competition.getUuid(),
                        configSet.getId(),
                        normalized.getItemType(),
                        normalized.getItemKey(),
                        normalized.getTitle(),
                        trimToNull(normalized.getContentJson()),
                        trimToNull(normalized.getContentText()),
                        normalized.getSortOrder() == null ? (index + 1) * 10 : normalized.getSortOrder(),
                        Boolean.TRUE.equals(normalized.getRequiredFlag()),
                        normalized.getEnabled() == null || Boolean.TRUE.equals(normalized.getEnabled()),
                        userId,
                        userUuid
                );
            } else {
                retainedIds.add(existing.getId());
                int itemUpdated = jdbcTemplate.update(
                        """
                                update competition_config_item
                                set title = ?, content_json = ?, content_text = ?, sort_order = ?,
                                    required_flag = ?, enabled = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                                where id = ? and competition_uuid = ? and config_set_id = ? and item_type = ? and item_key = ? and deleted = 0
                                """,
                        normalized.getTitle(),
                        trimToNull(normalized.getContentJson()),
                        trimToNull(normalized.getContentText()),
                        normalized.getSortOrder() == null ? (index + 1) * 10 : normalized.getSortOrder(),
                        Boolean.TRUE.equals(normalized.getRequiredFlag()) ? 1 : 0,
                        normalized.getEnabled() == null || Boolean.TRUE.equals(normalized.getEnabled()) ? 1 : 0,
                        userId,
                        userUuid,
                        LocalDateTime.now(),
                        existing.getId(),
                        competition.getUuid(),
                        configSet.getId(),
                        existing.getItemType(),
                        existing.getItemKey()
                );
                requireCompetitionWrite(itemUpdated, "Competition config item changed, please retry");
            }
            index += 1;
        }
        List<Long> deletedIds = existingByKey.values().stream()
                .map(CompetitionVO.ConfigItem::getId)
                .filter(id -> id != null && !retainedIds.contains(id))
                .toList();
        if (!deletedIds.isEmpty()) {
            int deleted = jdbcTemplate.update(
                    """
                            update competition_config_item
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where competition_uuid = ?
                              and config_set_id = ?
                              and deleted = 0
                              and id in (
                            """ + placeholders(deletedIds.size()) + ")",
                    concatParams(List.of(userId, userUuid, LocalDateTime.now(), competition.getUuid(), configSet.getId()), deletedIds).toArray()
            );
            requireCompetitionWrite(deleted, "Competition config item changed, please retry");
        }
        if ("files".equals(module) || "stage-materials".equals(module) || "materials".equals(module)) {
            synchronizeStageForms(competition, configSet, userId, userUuid);
        }
        recordConfigAudit(currentUser, competition.getUuid(), "SAVE_SETTINGS", module.toUpperCase(Locale.ROOT), "Saved " + request.getItems().size() + " config items");
        return getCompetitionSettings(currentUser, competition.getUuid());
    }

    @Transactional
    public CompetitionVO.ConfigSet publishSettings(CurrentUser currentUser, String competitionUuid) {
        Long userId = requirePermission(currentUser, COMPETITION_UPDATE);
        String userUuid = requireUserUuid(currentUser);
        CompetitionVO.Competition competition = requireCompetitionByUuid(competitionUuid);
        CompetitionVO.ConfigSet current = ensureCurrentConfigSet(competition, currentUser);
        validateCompetitionReadyForPublish(competition, current);
        int published = jdbcTemplate.update(
                "update competition_config_set set status = 'PUBLISHED', published_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and competition_uuid = ? and status = ? and deleted = 0",
                LocalDateTime.now(),
                userId,
                userUuid,
                LocalDateTime.now(),
                current.getId(),
                competition.getUuid(),
                current.getStatus()
        );
        requireCompetitionWrite(published, "Competition config set changed, please retry");
        jdbcTemplate.update(
                "update competition_config_set set status = 'ARCHIVED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where competition_uuid = ? and id <> ? and deleted = 0",
                userId,
                userUuid,
                LocalDateTime.now(),
                competition.getUuid(),
                current.getId()
        );
        synchronizeStageForms(competition, current, userId, userUuid);
        recordConfigAudit(currentUser, competition.getUuid(), "PUBLISH_SETTINGS", "PUBLISH", "Published current config");
        return requireConfigSet(current.getId());
    }

    private void synchronizeStageForms(
            CompetitionVO.Competition competition,
            CompetitionVO.ConfigSet configSet,
            Long userId,
            String userUuid
    ) {
        List<CompetitionVO.ConfigItem> configuredMaterials = listConfigItems(
                competition.getUuid(),
                configSet.getId(),
                Set.of("REQUIRED_FILE", "STAGE_MATERIAL")
        ).stream().filter(item -> !Boolean.FALSE.equals(item.getEnabled())).toList();
        List<CompetitionVO.ConfigItem> preliminary = configuredMaterials.stream()
                .filter(item -> {
                    String stageCode = metadataValue(item, "stageCode");
                    return !StringUtils.hasText(stageCode)
                            || "GENERAL".equalsIgnoreCase(stageCode)
                            || "PRELIMINARY".equalsIgnoreCase(stageCode);
                })
                .toList();
        List<CompetitionVO.ConfigItem> finals = configuredMaterials.stream()
                .filter(item -> "FINAL".equalsIgnoreCase(metadataValue(item, "stageCode")))
                .toList();
        synchronizeStageForm(competition.getId(), "PRELIMINARY", "初赛", preliminary, 10, userId, userUuid);
        synchronizeStageForm(competition.getId(), "FINAL", "决赛", finals, 20, userId, userUuid);
    }

    private void synchronizeStageForm(
            Long competitionId,
            String stageCode,
            String stageName,
            List<CompetitionVO.ConfigItem> items,
            int sort,
            Long userId,
            String userUuid
    ) {
        Long stageId = jdbcTemplate.queryForObject(
                "select id from competition_stage where competition_id = ? and stage_code = ? and deleted = 0 order by id asc limit 1",
                Long.class,
                competitionId,
                stageCode
        );
        if (items.isEmpty()) {
            if (stageId != null) {
                jdbcTemplate.update(
                        "update competition_stage_form set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where stage_id = ? and deleted = 0",
                        userId,
                        userUuid,
                        LocalDateTime.now(),
                        stageId
                );
                jdbcTemplate.update(
                        "update competition_stage set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and competition_id = ? and deleted = 0",
                        userId,
                        userUuid,
                        LocalDateTime.now(),
                        stageId,
                        competitionId
                );
            }
            return;
        }
        if (stageId == null) {
            int inserted = jdbcTemplate.update(
                    "insert into competition_stage (competition_id, stage_code, stage_name, status, sort, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted) values (?, ?, ?, 'ENABLED', ?, ?, ?, ?, ?, 0)",
                    competitionId,
                    stageCode,
                    stageName,
                    sort,
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireCompetitionWrite(inserted, "Competition stage changed, please retry");
            stageId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            jdbcTemplate.update(
                    "update competition_stage set stage_name = ?, status = 'ENABLED', sort = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and competition_id = ? and deleted = 0",
                    stageName,
                    sort,
                    userId,
                    userUuid,
                    LocalDateTime.now(),
                    stageId,
                    competitionId
            );
        }

        String formSchemaJson = buildStageFormSchema(items);
        Long formId = jdbcTemplate.queryForObject(
                "select id from competition_stage_form where stage_id = ? and deleted = 0 order by version desc, id desc limit 1",
                Long.class,
                stageId
        );
        if (formId == null) {
            int inserted = jdbcTemplate.update(
                    "insert into competition_stage_form (competition_id, stage_id, form_name, form_schema_json, version, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted) values (?, ?, ?, ?, 1, 'ENABLED', ?, ?, ?, ?, 0)",
                    competitionId,
                    stageId,
                    stageName + "材料",
                    formSchemaJson,
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireCompetitionWrite(inserted, "Competition stage form changed, please retry");
            return;
        }
        int updated = jdbcTemplate.update(
                "update competition_stage_form set form_name = ?, form_schema_json = ?, version = version + 1, status = 'ENABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and stage_id = ? and deleted = 0",
                stageName + "材料",
                formSchemaJson,
                userId,
                userUuid,
                LocalDateTime.now(),
                formId,
                stageId
        );
        requireCompetitionWrite(updated, "Competition stage form changed, please retry");
    }

    private String buildStageFormSchema(List<CompetitionVO.ConfigItem> items) {
        Map<String, CompetitionVO.ConfigItem> uniqueItems = new LinkedHashMap<>();
        items.forEach(item -> uniqueItems.put(item.getItemKey(), item));
        List<Map<String, Object>> fields = uniqueItems.values().stream().map(item -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("key", item.getItemKey());
            field.put("label", item.getTitle());
            field.put("type", "file");
            field.put("required", Boolean.TRUE.equals(item.getRequiredFlag()));
            field.put("fileFormat", firstText(metadataValue(item, "fileFormat"), "ANY"));
            field.put("maxSizeMb", positiveIntegerMetadata(item, "maxSizeMb", 20));
            String storageKey = metadataValue(item, "storageKey");
            if (StringUtils.hasText(storageKey)) {
                field.put("storageKey", storageKey);
            }
            return field;
        }).toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("fields", fields));
        } catch (Exception exception) {
            throw biz(ErrorCode.BIZ_ERROR, "Competition stage form could not be generated");
        }
    }

    private int positiveIntegerMetadata(CompetitionVO.ConfigItem item, String key, int fallback) {
        String value = metadataValue(item, key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Transactional
    public boolean deleteCompetition(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, COMPETITION_DELETE);
        requirePositiveId(id, "Competition id is required");
        Long registrationCount = jdbcTemplate.queryForObject(
                "select count(1) from competition_registration where competition_id = ? and deleted = 0",
                Long.class,
                id
        );
        if (registrationCount != null && registrationCount > 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Competition has registrations and cannot be deleted");
        }
        CompetitionVO.Competition competition = findCompetition(id);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        int updated = jdbcTemplate.update(
                "update aiadc_competition set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and competition_no = ? and status = ? and deleted = 0",
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                LocalDateTime.now(),
                id,
                competition.getUuid(),
                competition.getCompetitionNo(),
                competition.getStatus()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return true;
    }

    private List<Object> concatParams(List<?> prefix, List<?> suffix) {
        List<Object> params = new ArrayList<>(prefix.size() + suffix.size());
        params.addAll(prefix);
        params.addAll(suffix);
        return params;
    }

    private CompetitionVO.Competition findCompetition(Long id) {
        requirePositiveId(id, "Competition id is required");
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

    private CompetitionVO.Competition requirePublishedCompetitionByUuid(String competitionUuid) {
        String normalized = trimRequired(competitionUuid, "Competition uuid is required");
        List<CompetitionVO.Competition> records = jdbcTemplate.query(
                competitionSelect() + " from aiadc_competition where uuid = ? and status = 'published' and deleted = 0 limit 1",
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
        requirePublishText(missing, competition.getTitle(), "赛事名称不能为空");
        requirePublishText(missing, competition.getCategory(), "请选择赛事类别");
        requirePublishText(missing, firstText(competition.getCompetitionLevel(), competition.getLevel()), "请选择赛事级别");
        requirePublishText(missing, competition.getParticipationScope(), "请填写报名范围");
        requirePublishText(missing, competition.getFeeMode(), "请选择收费方式");
        requirePublishText(missing, competition.getCurrency(), "请选择结算币种");
        requirePublishText(missing, competition.getRegistrationStart(), "请设置报名开始时间");
        requirePublishText(missing, competition.getRegistrationEnd(), "请设置报名结束时间");
        requirePublishText(missing, competition.getCompetitionStart(), "请设置赛事开始时间");

        List<CompetitionVO.ConfigItem> items = listConfigItems(competition.getUuid(), configSet.getId(), CONFIG_ITEM_TYPES);
        validateEnabledConfigItems(missing, items);
        if (!missing.isEmpty()) {
            String message = missing.stream().limit(8).collect(Collectors.joining("；"));
            if (missing.size() > 8) {
                message += "；另有 " + (missing.size() - 8) + " 项未完成";
            }
            throw biz(ErrorCode.VALIDATION_ERROR, "赛事暂未满足发布条件：" + message);
        }
    }

    private void validateEnabledConfigItems(List<String> missing, List<CompetitionVO.ConfigItem> items) {
        for (CompetitionVO.ConfigItem item : items) {
            if (Boolean.FALSE.equals(item.getEnabled())) {
                continue;
            }
            String moduleLabel = configItemModuleLabel(item.getItemType());
            String itemLabel = itemLabel(item);
            String itemReference = moduleLabel + "“" + itemLabel + "”";
            requirePublishText(missing, item.getTitle(), moduleLabel + "名称不能为空");
            requirePublishText(missing, item.getItemKey(), itemReference + "标识不能为空");
            if ("AGREEMENT".equals(item.getItemType()) || "CONSENT".equals(item.getItemType())) {
                requirePublishText(missing, item.getContentText(), itemReference + "必须填写内容");
            } else if (Set.of("REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD").contains(item.getItemType())) {
                requirePublishText(missing, fieldTypeForPublish(item), itemReference + "必须设置字段类型");
            } else if ("REQUIRED_FILE".equals(item.getItemType())) {
                requirePublishText(missing, fileFormatForPublish(item), itemReference + "必须设置允许上传的文件格式");
            } else if ("STAGE_MATERIAL".equals(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "stageName"), itemReference + "必须设置所属阶段");
                requirePublishText(missing, metadataValue(item, "materialType"), itemReference + "必须设置材料类型");
            } else if ("TIMELINE".equals(item.getItemType())) {
                requirePublishText(missing, metadataValue(item, "timelineKind"), itemReference + "必须设置时间类型");
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

    private boolean shouldValidatePublishTransition(String previousStatus, String nextStatus) {
        return !isPublishedStatus(previousStatus) && isPublishedStatus(nextStatus);
    }

    private boolean isPublishedStatus(String status) {
        return "published".equals(status);
    }

    private String itemLabel(CompetitionVO.ConfigItem item) {
        String title = StringUtils.hasText(item.getTitle()) ? item.getTitle().trim() : "未命名";
        if ("contact-name".equals(item.getItemKey()) && "Contact name".equalsIgnoreCase(title)) {
            return "联系人姓名";
        }
        if ("work-file".equals(item.getItemKey()) && "Work file".equalsIgnoreCase(title)) {
            return "作品文件";
        }
        if ("commitment".equals(item.getItemKey()) && "Commitment".equalsIgnoreCase(title)) {
            return "赛事承诺书";
        }
        if ("informed-consent".equals(item.getItemKey()) && "Informed consent".equalsIgnoreCase(title)) {
            return "知情同意书";
        }
        return title;
    }

    private String configItemModuleLabel(String itemType) {
        return switch (itemType == null ? "" : itemType) {
            case "AGREEMENT", "CONSENT" -> "报名文书";
            case "REGISTRATION_FIELD", "TEAM_FIELD", "MEMBER_FIELD", "PROJECT_FIELD" -> "报名字段";
            case "TEAM_SETTINGS" -> "团队设置";
            case "PAYMENT_SETTINGS" -> "支付设置";
            case "REQUIRED_FILE" -> "提交材料";
            case "STAGE_MATERIAL" -> "阶段材料";
            case "TIMELINE" -> "时间节点";
            default -> "配置项";
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

    private String fieldTypeForPublish(CompetitionVO.ConfigItem item) {
        String fieldType = metadataValue(item, "fieldType");
        if (StringUtils.hasText(fieldType)) {
            return fieldType;
        }
        String legacyType = metadataValue(item, "type");
        if (!StringUtils.hasText(legacyType)) {
            return null;
        }
        return switch (legacyType.trim().toLowerCase(Locale.ROOT)) {
            case "input", "text" -> "TEXT";
            case "textarea" -> "TEXTAREA";
            case "number" -> "NUMBER";
            case "date" -> "DATE";
            case "select", "radio" -> "SELECT";
            case "multiselect", "multi-select", "checkbox" -> "MULTI_SELECT";
            case "mobile", "phone" -> "MOBILE";
            case "email" -> "EMAIL";
            case "image", "upload" -> "IMAGE";
            default -> null;
        };
    }

    private String fileFormatForPublish(CompetitionVO.ConfigItem item) {
        String fileFormat = metadataValue(item, "fileFormat");
        if (StringUtils.hasText(fileFormat)) {
            return fileFormat;
        }
        return StringUtils.hasText(metadataValue(item, "accept")) ? "ANY" : null;
    }

    private CompetitionDTO.CompetitionUpsertRequest normalizeRequest(CompetitionDTO.CompetitionUpsertRequest request, String fallbackCode) {
        requireRequest(request, "Competition request is required");
        CompetitionVO.Competition fallback = new CompetitionVO.Competition();
        fallback.setCompetitionNo(fallbackCode);
        fallback.setTitle(null);
        fallback.setCategory(null);
        fallback.setCompetitionStart(null);
        fallback.setLocation(null);
        fallback.setStatus("draft");
        CompetitionDTO.CompetitionUpsertRequest normalized = normalizeRequest(request, fallback);
        normalized.setTitle(trimRequired(request.getTitle(), "Competition title is required"));
        normalized.setCategory(dictRuntimeService.normalizeValue(
                COMPETITION_CATEGORY_DICT,
                trimRequired(request.getCategory(), "Competition category is required"),
                null,
                true,
                "Invalid competition category"
        ));
        normalized.setCompetitionStart(trimRequired(request.getCompetitionStart(), "Competition start time is required"));
        normalized.setLocation(trimRequired(request.getLocation(), "Competition location is required"));
        return normalized;
    }

    private CompetitionDTO.CompetitionUpsertRequest normalizeRequest(CompetitionDTO.CompetitionUpsertRequest request, CompetitionVO.Competition existing) {
        requireRequest(request, "Competition request is required");
        CompetitionDTO.CompetitionUpsertRequest normalized = new CompetitionDTO.CompetitionUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? trimRequired(request.getCode(), "Competition code is required", MAX_CODE_LENGTH, "Competition code is too long")
                : trimRequired(existing.getCompetitionNo(), "Competition code is required", MAX_CODE_LENGTH, "Competition code is too long"));
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid competition locale"));
        normalized.setTitle(trimRequired(firstText(request.getTitle(), existing.getTitle(), "未命名赛事"), "Competition title is required", MAX_TITLE_LENGTH, "Competition title is too long"));
        normalized.setShortName(trimOptional(request.getShortName(), MAX_TITLE_LENGTH, "Competition short name is too long"));
        normalized.setCategory(dictRuntimeService.normalizeValue(
                COMPETITION_CATEGORY_DICT,
                firstText(request.getCategory(), existing.getCategory(), "OTHER"),
                existing.getCategory(),
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
        normalized.setCompetitionLevel(trimOptional(competitionLevel, MAX_SHORT_TEXT_LENGTH, "Competition level is too long"));
        normalized.setLevel(trimOptional(competitionLevel == null ? request.getLevel() : competitionLevel, MAX_SHORT_TEXT_LENGTH, "Competition level is too long"));
        normalized.setOrganizer(trimOptional(request.getOrganizer(), MAX_TITLE_LENGTH, "Competition organizer is too long"));
        normalized.setOrganizersJson(normalizeJson(request.getOrganizersJson(), "Competition organizers JSON"));
        normalized.setRegistrationStart(trimOptional(request.getRegistrationStart(), MAX_SHORT_TEXT_LENGTH, "Registration start is too long"));
        normalized.setRegistrationEnd(trimOptional(request.getRegistrationEnd(), MAX_SHORT_TEXT_LENGTH, "Registration end is too long"));
        normalized.setCompetitionStart(trimRequired(firstText(request.getCompetitionStart(), existing.getCompetitionStart(), "TBD"), "Competition start is required", MAX_SHORT_TEXT_LENGTH, "Competition start is too long"));
        normalized.setCompetitionEnd(trimOptional(request.getCompetitionEnd(), MAX_SHORT_TEXT_LENGTH, "Competition end is too long"));
        normalized.setLocation(trimRequired(firstText(request.getLocation(), existing.getLocation(), "TBD"), "Competition location is required", MAX_MID_TEXT_LENGTH, "Competition location is too long"));
        normalized.setParticipationScope(trimOptional(request.getParticipationScope(), MAX_MID_TEXT_LENGTH, "Participation scope is too long"));
        normalized.setParticipationRequirement(trimOptional(request.getParticipationRequirement(), MAX_LONG_TEXT_LENGTH, "Participation requirement is too long"));
        normalized.setScheduleJson(normalizeJson(request.getScheduleJson(), "Competition schedule JSON"));
        normalized.setDescription(trimOptional(request.getDescription(), MAX_LONG_TEXT_LENGTH, "Competition description is too long"));
        normalized.setImageUrl(normalizeUrl(request.getImageUrl(), "Competition image URL"));
        normalized.setContactName(trimOptional(request.getContactName(), MAX_TITLE_LENGTH, "Competition contact name is too long"));
        normalized.setContactQrCodeUrl(normalizeUrl(request.getContactQrCodeUrl(), "Competition contact QR code URL"));
        normalized.setHomepageContent(trimOptional(request.getHomepageContent(), MAX_HOMEPAGE_CONTENT_LENGTH, "Competition homepage content is too long"));
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Competition tags are too long"));
        normalized.setStatus(normalizeEnum(request.getStatus(), existing.getStatus(), STATUSES, "Invalid competition status"));
        normalized.setFeeMode(normalizeFeeMode(request.getFeeMode()));
        normalized.setEntryFeeMinor(normalizeEntryFeeMinor(request.getEntryFeeMinor()));
        normalized.setCurrency(normalizeCurrency(request.getCurrency()));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String firstText(String preferred, String fallback, String defaultValue) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return defaultValue;
    }

    private CompetitionDTO.CompetitionUpsertRequest normalizeDraftRequest(CompetitionDTO.CompetitionUpsertRequest request, String fallbackCode) {
        requireRequest(request, "Competition request is required");
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
                ? trimRequired(request.getCode(), "Competition code is required", MAX_CODE_LENGTH, "Competition code is too long")
                : trimRequired(fallbackCode, "Competition code is required", MAX_CODE_LENGTH, "Competition code is too long"));
        normalized.setLocale(normalizeLocales(request.getLocale(), "zh", LOCALES, "Invalid competition locale"));
        normalized.setTitle(StringUtils.hasText(request.getTitle())
                ? trimRequired(request.getTitle(), "Competition title is required", MAX_TITLE_LENGTH, "Competition title is too long")
                : "未命名赛事");
        normalized.setShortName(trimOptional(request.getShortName(), MAX_TITLE_LENGTH, "Competition short name is too long"));
        normalized.setCategory(trimToNull(category) == null ? "" : trimToNull(category));
        normalized.setCompetitionLevel(trimOptional(competitionLevel, MAX_SHORT_TEXT_LENGTH, "Competition level is too long"));
        normalized.setLevel(trimOptional(competitionLevel == null ? request.getLevel() : competitionLevel, MAX_SHORT_TEXT_LENGTH, "Competition level is too long"));
        normalized.setOrganizer(trimOptional(request.getOrganizer(), MAX_TITLE_LENGTH, "Competition organizer is too long"));
        normalized.setOrganizersJson(normalizeJson(request.getOrganizersJson(), "Competition organizers JSON"));
        normalized.setRegistrationStart(trimOptional(request.getRegistrationStart(), MAX_SHORT_TEXT_LENGTH, "Registration start is too long"));
        normalized.setRegistrationEnd(trimOptional(request.getRegistrationEnd(), MAX_SHORT_TEXT_LENGTH, "Registration end is too long"));
        String competitionStart = trimToNull(request.getCompetitionStart());
        normalized.setCompetitionStart(competitionStart == null || "TBD".equalsIgnoreCase(competitionStart)
                ? ""
                : trimRequired(competitionStart, "Competition start is required", MAX_SHORT_TEXT_LENGTH, "Competition start is too long"));
        normalized.setCompetitionEnd(trimOptional(request.getCompetitionEnd(), MAX_SHORT_TEXT_LENGTH, "Competition end is too long"));
        String location = trimToNull(request.getLocation());
        normalized.setLocation(location == null || "TBD".equalsIgnoreCase(location)
                ? ""
                : trimRequired(location, "Competition location is required", MAX_MID_TEXT_LENGTH, "Competition location is too long"));
        String participationScope = trimToNull(request.getParticipationScope());
        normalized.setParticipationScope(participationScope == null || "TBD".equalsIgnoreCase(participationScope)
                ? null
                : trimOptional(participationScope, MAX_MID_TEXT_LENGTH, "Participation scope is too long"));
        normalized.setParticipationRequirement(trimOptional(request.getParticipationRequirement(), MAX_LONG_TEXT_LENGTH, "Participation requirement is too long"));
        normalized.setScheduleJson(normalizeJson(request.getScheduleJson(), "Competition schedule JSON"));
        normalized.setDescription(trimOptional(request.getDescription(), MAX_LONG_TEXT_LENGTH, "Competition description is too long"));
        normalized.setImageUrl(normalizeUrl(request.getImageUrl(), "Competition image URL"));
        normalized.setContactName(trimOptional(request.getContactName(), MAX_TITLE_LENGTH, "Competition contact name is too long"));
        normalized.setContactQrCodeUrl(normalizeUrl(request.getContactQrCodeUrl(), "Competition contact QR code URL"));
        normalized.setHomepageContent(trimOptional(request.getHomepageContent(), MAX_HOMEPAGE_CONTENT_LENGTH, "Competition homepage content is too long"));
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Competition tags are too long"));
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

    private CompetitionVO.ConfigSet ensureCurrentConfigSet(CompetitionVO.Competition competition, CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        List<CompetitionVO.ConfigSet> configSets = jdbcTemplate.query(
                configSetSelect() + " from competition_config_set where competition_uuid = ? and status in ('DRAFT', 'PUBLISHED') and deleted = 0 order by id desc limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigSet.class),
                competition.getUuid()
        );
        if (!configSets.isEmpty()) {
            return configSets.get(0);
        }
        int inserted = jdbcTemplate.update(
                """
                        insert into competition_config_set (
                            competition_uuid, version, status, created_by_uuid, created_by, updated_by, updated_by_uuid, deleted
                        ) values (?, 1, 'DRAFT', ?, ?, ?, ?, 0)
                """,
                competition.getUuid(),
                userUuid,
                userId,
                userId,
                userUuid
        );
        requireCompetitionWrite(inserted, "Competition config set changed, please retry");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        seedDefaultConfigItems(competition.getUuid(), id, userId, userUuid);
        return requireConfigSet(id);
    }

    private void seedDefaultConfigItems(String competitionUuid, Long configSetId, Long userId, String userUuid) {
        Long existing = jdbcTemplate.queryForObject(
                "select count(1) from competition_config_item where config_set_id = ? and deleted = 0",
                Long.class,
                configSetId
        );
        if (existing != null && existing > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into competition_config_item (
                            competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                            sort_order, required_flag, enabled, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select ?, ?, item_type, item_key, title, content_json, content_text,
                               sort_order, required_flag, enabled, ?, ?, ?, ?, 0
                        from competition_config_item_template
                        where template_code = 'DEFAULT' and enabled = 1 and deleted = 0
                        order by sort_order asc, id asc
                        """,
                competitionUuid,
                configSetId,
                userId,
                userUuid,
                userId,
                userUuid
        );
    }

    private void insertConfigItem(
            String competitionUuid,
            Long configSetId,
            String itemType,
            String itemKey,
            String title,
            String contentJson,
            String contentText,
            int sortOrder,
            boolean required,
            boolean enabled,
            Long userId,
            String userUuid
    ) {
        jdbcTemplate.update(
                """
                        insert into competition_config_item (
                            competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                            sort_order, required_flag, enabled, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                userUuid,
                userId,
                userUuid
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
        requireRequest(request, "Config item request is required");
        CompetitionDTO.ConfigItemRequest normalized = new CompetitionDTO.ConfigItemRequest();
        String itemType = trimRequired(request.getItemType(), "Config item type is required").toUpperCase(Locale.ROOT);
        if (!CONFIG_ITEM_TYPES.contains(itemType)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid config item type");
        }
        normalized.setItemType(itemType);
        normalized.setItemKey(trimRequired(request.getItemKey(), "Config item key is required", MAX_TITLE_LENGTH, "Config item key is too long"));
        normalized.setTitle(trimRequired(request.getTitle(), "Config item title is required", MAX_MID_TEXT_LENGTH, "Config item title is too long"));
        normalized.setContentJson(normalizeJson(request.getContentJson(), "Config item JSON"));
        normalized.setContentText(trimOptional(request.getContentText(), MAX_HOMEPAGE_CONTENT_LENGTH, "Config item text is too long"));
        normalized.setSortOrder(request.getSortOrder());
        normalized.setRequiredFlag(Boolean.TRUE.equals(request.getRequiredFlag()));
        normalized.setEnabled(request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()));
        validateMaterialConfigItem(normalized);
        return normalized;
    }

    private void validateMaterialConfigItem(CompetitionDTO.ConfigItemRequest item) {
        if (!"REQUIRED_FILE".equals(item.getItemType()) && !"STAGE_MATERIAL".equals(item.getItemType())) {
            return;
        }
        JsonNode metadata;
        try {
            metadata = StringUtils.hasText(item.getContentJson())
                    ? OBJECT_MAPPER.readTree(item.getContentJson())
                    : OBJECT_MAPPER.createObjectNode();
        } catch (Exception error) {
            throw biz(ErrorCode.VALIDATION_ERROR, "材料配置格式不正确");
        }
        String label = "材料“" + item.getTitle() + "”";
        if (!StringUtils.hasText(metadata.path("stageCode").asText(null))) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + "必须设置所属阶段");
        }
        if (!StringUtils.hasText(metadata.path("fileFormat").asText(null))) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + "必须设置文件格式");
        }
        if (!metadata.path("maxSizeMb").canConvertToInt() || metadata.path("maxSizeMb").asInt() <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + "必须设置有效的文件大小上限");
        }
        if (!StringUtils.hasText(metadata.path("storageKey").asText(null))) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + "必须选择保存位置");
        }
    }

    private String configItemIdentity(CompetitionVO.ConfigItem item) {
        return item.getItemType() + "\u0000" + item.getItemKey();
    }

    private String configItemIdentity(CompetitionDTO.ConfigItemRequest item) {
        return item.getItemType() + "\u0000" + item.getItemKey();
    }

    private void recordConfigAudit(CurrentUser currentUser, String competitionUuid, String action, String module, String detail) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        jdbcTemplate.update(
                """
                        insert into competition_config_audit (
                            competition_uuid, operator_user_id, operator_user_uuid, action, module, detail_message,
                            created_by, created_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                competitionUuid,
                userId,
                userUuid,
                action,
                module,
                detail,
                userId,
                userUuid
        );
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private Long requireUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String requireUserUuid(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserUuid().trim();
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        Long userId = requireUserId(currentUser);
        if (!hasPermission(currentUser, permissionKey)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return userId;
    }

    private void requireCompetitionListPermission(CurrentUser currentUser, String status) {
        requireUserId(currentUser);
        if (hasPermission(currentUser, COMPETITION_VIEW)) {
            return;
        }
        boolean publishedOnly = "published".equalsIgnoreCase(status);
        if (publishedOnly && (hasPermission(currentUser, REGISTRATION_VIEW) || hasPermission(currentUser, REGISTRATION_CREATE))) {
            return;
        }
        throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + COMPETITION_VIEW);
    }

    private CompetitionVO.Competition requireCompetitionSettingsAccess(CurrentUser currentUser, String competitionUuid) {
        requireUserId(currentUser);
        if (hasPermission(currentUser, COMPETITION_VIEW)) {
            return requireCompetitionByUuid(competitionUuid);
        }
        if (hasPermission(currentUser, REGISTRATION_VIEW) || hasPermission(currentUser, REGISTRATION_CREATE)) {
            return requirePublishedCompetitionByUuid(competitionUuid);
        }
        throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + COMPETITION_VIEW);
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
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
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
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

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
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

    private String normalizeJson(String value, String fieldName) {
        String trimmed = trimOptional(value, MAX_JSON_LENGTH, fieldName + " is too long");
        if (trimmed == null) {
            return null;
        }
        try {
            OBJECT_MAPPER.readTree(trimmed);
            return trimmed;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
        }
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

    private void requireCompetitionWrite(int updated, String message) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
