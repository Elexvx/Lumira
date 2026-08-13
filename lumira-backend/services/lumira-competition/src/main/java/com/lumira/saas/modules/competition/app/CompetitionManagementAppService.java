package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.api.file.CompetitionStorageSpace;
import com.lumira.api.file.CompetitionStorageSpaceRequest;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.infrastructure.CompetitionManagementPersistenceAssemblyConfiguration;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.repository.CompetitionStageRepository;
import com.lumira.saas.modules.competition.repository.RegistrationDatasetRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Import(CompetitionManagementPersistenceAssemblyConfiguration.class)
public class CompetitionManagementAppService {
    private static final String COMPETITION_VIEW = "aiadc:competition:view";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE = "aiadc:registration:create";
    private static final String COMPETITION_CREATE = "aiadc:competition:create";
    private static final String COMPETITION_UPDATE = "aiadc:competition:update";
    private static final String COMPETITION_DELETE = "aiadc:competition:delete";
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
    private static final List<DateTimeFormatter> TIMELINE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_MID_TEXT_LENGTH = 255;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final int MAX_CONFIG_CONTENT_LENGTH = 20000;
    private static final int MAX_JSON_LENGTH = 20000;
    private static final int MAX_URL_LENGTH = 512;

    private final DictionaryValueNormalizer dictionaryValueNormalizer;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final RegistrationDatasetRepository registrationDatasetRepository;
    private final CompetitionManagementRepository competitionManagementRepository;
    private final CompetitionSettingsRepository competitionSettingsRepository;
    private final CompetitionStageRepository competitionStageRepository;
    private final TransactionalEventOutboxPort transactionalEventOutboxPort;
    private final FileInternalApi fileInternalApi;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public CompetitionManagementAppService(
            DictionaryValueNormalizer dictionaryValueNormalizer,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            RegistrationDatasetRepository registrationDatasetRepository,
            CompetitionManagementRepository competitionManagementRepository,
            CompetitionSettingsRepository competitionSettingsRepository,
            CompetitionStageRepository competitionStageRepository,
            TransactionalEventOutboxPort transactionalEventOutboxPort,
            FileInternalApi fileInternalApi
    ) {
        this(
                dictionaryValueNormalizer,
                trustedCurrentUserResolver,
                registrationDatasetRepository,
                competitionManagementRepository,
                competitionSettingsRepository,
                competitionStageRepository,
                true,
                transactionalEventOutboxPort,
                fileInternalApi
        );
    }

    CompetitionManagementAppService(
            DictionaryValueNormalizer dictionaryValueNormalizer,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            RegistrationDatasetRepository registrationDatasetRepository,
            CompetitionManagementRepository competitionManagementRepository,
            CompetitionSettingsRepository competitionSettingsRepository,
            CompetitionStageRepository competitionStageRepository,
            boolean enforceTrustedUserResolution,
            FileInternalApi fileInternalApi
    ) {
        this(
                dictionaryValueNormalizer,
                trustedCurrentUserResolver,
                registrationDatasetRepository,
                competitionManagementRepository,
                competitionSettingsRepository,
                competitionStageRepository,
                enforceTrustedUserResolution,
                null,
                fileInternalApi
        );
    }

    CompetitionManagementAppService(
            DictionaryValueNormalizer dictionaryValueNormalizer,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            RegistrationDatasetRepository registrationDatasetRepository,
            CompetitionManagementRepository competitionManagementRepository,
            CompetitionSettingsRepository competitionSettingsRepository,
            CompetitionStageRepository competitionStageRepository,
            boolean enforceTrustedUserResolution,
            TransactionalEventOutboxPort transactionalEventOutboxPort,
            FileInternalApi fileInternalApi
    ) {
        this.dictionaryValueNormalizer = dictionaryValueNormalizer;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.registrationDatasetRepository = registrationDatasetRepository;
        this.competitionManagementRepository = competitionManagementRepository;
        this.competitionSettingsRepository = competitionSettingsRepository;
        this.competitionStageRepository = competitionStageRepository;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.transactionalEventOutboxPort = transactionalEventOutboxPort;
        this.fileInternalApi = Objects.requireNonNull(fileInternalApi, "fileInternalApi");
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
        CompetitionManagementRepository.CompetitionPage page = competitionManagementRepository.findCompetitions(
                new CompetitionManagementRepository.CompetitionSearch(
                        trimToNull(keyword),
                        trimToNull(category),
                        StringUtils.hasText(status) ? normalizeEnum(status, null, STATUSES, "Invalid competition status") : null,
                        StringUtils.hasText(locale) ? normalizeEnum(locale, null, LOCALES, "Invalid competition locale") : null,
                        featured,
                        (normalizedPageNo - 1) * normalizedPageSize,
                        normalizedPageSize
                )
        );

        PageResponse<CompetitionVO.Competition> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
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
        ensureCompetitionStorageSpace(
                competition.getId(),
                competition.getUuid(),
                competition.getTitle(),
                requireUserId(currentUser),
                requireUserUuid(currentUser)
        );
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
        parseStageScheduleWindows(
                normalized.getScheduleJson(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd()
        );
        CompetitionManagementRepository.CompetitionCreateResult created = competitionManagementRepository.createCompetition(
                new CompetitionManagementRepository.CompetitionCreate(
                        uuid,
                        competitionNo,
                        normalized,
                        new CompetitionManagementRepository.Actor(userId, userUuid)
                )
        );
        requireCompetitionWrite(created.writeCount(), "Competition changed, please retry");
        Long id = created.competitionId();
        requireCompetitionWrite(id == null ? 0 : 1, "Competition changed, please retry");
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        requireCompetitionWrite(
                registrationDatasetRepository.createDataset(id, normalized.getTitle(), userId, userUuid),
                "Registration dataset could not be created"
        );
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, currentUser);
        if ("published".equals(competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, configSet);
        }
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_COMPETITION", "BASIC", "Created competition " + competition.getCompetitionNo());
        recordCatalogChange(competition, null, userId, userUuid, competition.getUpdatedAt(), false);
        ensureCompetitionStorageSpace(id, uuid, normalized.getTitle(), userId, userUuid);
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
        parseDraftStageScheduleWindows(
                normalized.getScheduleJson(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd()
        );
        CompetitionManagementRepository.CompetitionCreateResult created = competitionManagementRepository.createCompetition(
                new CompetitionManagementRepository.CompetitionCreate(
                        uuid,
                        competitionNo,
                        normalized,
                        new CompetitionManagementRepository.Actor(userId, userUuid)
                )
        );
        requireCompetitionWrite(created.writeCount(), "Competition changed, please retry");
        Long id = created.competitionId();
        requireCompetitionWrite(id == null ? 0 : 1, "Competition changed, please retry");
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        requireCompetitionWrite(
                registrationDatasetRepository.createDataset(id, normalized.getTitle(), userId, userUuid),
                "Registration dataset could not be created"
        );
        ensureCurrentConfigSet(competition, currentUser);
        recordConfigAudit(currentUser, competition.getUuid(), "CREATE_DRAFT", "BASIC", "Created competition draft " + competition.getCompetitionNo());
        recordCatalogChange(competition, null, userId, userUuid, competition.getUpdatedAt(), false);
        ensureCompetitionStorageSpace(id, uuid, normalized.getTitle(), userId, userUuid);
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
        if (Objects.equals(request.getScheduleJson(), existing.getScheduleJson())) {
            normalized.setScheduleJson(existing.getScheduleJson());
        }
        boolean scheduleChanged = !Objects.equals(existing.getScheduleJson(), normalized.getScheduleJson());
        boolean registrationWindowChanged = !Objects.equals(existing.getRegistrationStart(), normalized.getRegistrationStart())
                || !Objects.equals(existing.getRegistrationEnd(), normalized.getRegistrationEnd());
        boolean publishing = shouldValidatePublishTransition(existing.getStatus(), normalized.getStatus());
        if (scheduleChanged || registrationWindowChanged || publishing) {
            parseStageScheduleWindows(
                    normalized.getScheduleJson(),
                    normalized.getRegistrationStart(),
                    normalized.getRegistrationEnd()
            );
        }
        int updated = competitionManagementRepository.updateCompetition(
                new CompetitionManagementRepository.CompetitionUpdate(
                        id,
                        existing.getUuid(),
                        existing.getCompetitionNo(),
                        existing.getStatus(),
                        normalized.getStatus(),
                        normalized,
                        new CompetitionManagementRepository.Actor(requireUserId(currentUser), requireUserUuid(currentUser)),
                        LocalDateTime.now()
                )
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        if (scheduleChanged) {
            synchronizeStageWindowsFromSchedule(
                    id,
                    normalized.getScheduleJson(),
                    requireUserId(currentUser),
                    requireUserUuid(currentUser)
            );
        }
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        if (shouldValidatePublishTransition(existing.getStatus(), competition.getStatus())) {
            validateCompetitionReadyForPublish(competition, ensureCurrentConfigSet(competition, currentUser));
        }
        recordConfigAudit(currentUser, competition.getUuid(), "UPDATE_COMPETITION", "BASIC", "Updated competition basic information");
        recordCatalogChange(
                competition,
                existing.getStatus(),
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                competition.getUpdatedAt(),
                false
        );
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
        parseDraftStageScheduleWindows(
                normalized.getScheduleJson(),
                normalized.getRegistrationStart(),
                normalized.getRegistrationEnd()
        );
        int updated = competitionManagementRepository.updateCompetition(
                new CompetitionManagementRepository.CompetitionUpdate(
                        id,
                        existing.getUuid(),
                        existing.getCompetitionNo(),
                        "draft",
                        "draft",
                        normalized,
                        new CompetitionManagementRepository.Actor(requireUserId(currentUser), requireUserUuid(currentUser)),
                        LocalDateTime.now()
                )
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        synchronizeStageWindowsFromSchedule(
                id,
                normalized.getScheduleJson(),
                requireUserId(currentUser),
                requireUserUuid(currentUser)
        );
        CompetitionVO.Competition competition = getCompetition(currentUser, id);
        recordConfigAudit(currentUser, competition.getUuid(), "UPDATE_DRAFT", "BASIC", "Updated competition draft");
        recordCatalogChange(
                competition,
                existing.getStatus(),
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                competition.getUpdatedAt(),
                false
        );
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
        enforceCompetitionStorageKey(normalizedItems, competition.getStorageKey());
        CompetitionVO.ConfigSet configSet = ensureCurrentConfigSet(competition, currentUser);
        Map<String, CompetitionVO.ConfigItem> existingByKey = listConfigItems(competition.getUuid(), configSet.getId(), allowedTypes)
                .stream()
                .collect(Collectors.toMap(this::configItemIdentity, item -> item, (left, right) -> left));
        Set<String> requestKeys = new LinkedHashSet<>();
        for (CompetitionDTO.ConfigItemRequest normalized : normalizedItems) {
            String identity = configItemIdentity(normalized);
            if (!requestKeys.add(identity)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "同一配置类型内不能使用重复的设置项标识：" + normalized.getItemKey());
            }
        }
        Set<Long> retainedIds = new LinkedHashSet<>();
        int index = 0;
        for (CompetitionDTO.ConfigItemRequest normalized : normalizedItems) {
            String identity = configItemIdentity(normalized);
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
                int itemUpdated = competitionSettingsRepository.updateConfigItem(
                        new CompetitionSettingsRepository.ConfigItemUpdate(
                                existing.getId(),
                                competition.getUuid(),
                                configSet.getId(),
                                existing.getItemType(),
                                existing.getItemKey(),
                                normalized.getTitle(),
                                trimToNull(normalized.getContentJson()),
                                trimToNull(normalized.getContentText()),
                                normalized.getSortOrder() == null ? (index + 1) * 10 : normalized.getSortOrder(),
                                Boolean.TRUE.equals(normalized.getRequiredFlag()),
                                normalized.getEnabled() == null || Boolean.TRUE.equals(normalized.getEnabled()),
                                new CompetitionSettingsRepository.Actor(userId, userUuid),
                                LocalDateTime.now()
                        )
                );
                requireCompetitionWrite(itemUpdated, "Competition config item changed, please retry");
            }
            index += 1;
        }
        List<CompetitionVO.ConfigItem> deletedItems = existingByKey.values().stream()
                .filter(item -> item.getId() != null && !retainedIds.contains(item.getId()))
                .toList();
        if (!deletedItems.isEmpty()) {
            purgePreviousConfigItemTombstones(configSet.getId(), deletedItems);
            List<Long> deletedIds = deletedItems.stream().map(CompetitionVO.ConfigItem::getId).toList();
            int deleted = competitionSettingsRepository.softDeleteConfigItems(
                    new CompetitionSettingsRepository.ConfigItemSoftDelete(
                            competition.getUuid(),
                            configSet.getId(),
                            deletedIds,
                            new CompetitionSettingsRepository.Actor(userId, userUuid),
                            LocalDateTime.now()
                    )
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
        LocalDateTime now = LocalDateTime.now();
        CompetitionSettingsRepository.Actor actor = new CompetitionSettingsRepository.Actor(userId, userUuid);
        int published = competitionSettingsRepository.publishConfigSet(
                new CompetitionSettingsRepository.ConfigSetPublish(
                        current.getId(),
                        competition.getUuid(),
                        current.getStatus(),
                        actor,
                        now,
                        now
                )
        );
        requireCompetitionWrite(published, "Competition config set changed, please retry");
        competitionSettingsRepository.archiveOtherConfigSets(
                new CompetitionSettingsRepository.ConfigSetArchive(
                        competition.getUuid(),
                        current.getId(),
                        actor,
                        now
                )
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
        synchronizeStageWindowsFromSchedule(competition.getId(), competition.getScheduleJson(), userId, userUuid);
    }

    private record StageScheduleWindow(
            String stageName,
            LocalDateTime materialStart,
            LocalDateTime materialEnd,
            LocalDateTime reviewStart,
            LocalDateTime reviewEnd
    ) {
    }

    private record TimelineRange(LocalDateTime start, LocalDateTime end) {
    }

    private enum StageScheduleValidationMode {
        STRICT,
        DRAFT
    }

    private void synchronizeStageWindowsFromSchedule(
            Long competitionId,
            String scheduleJson,
            Long userId,
            String userUuid
    ) {
        Map<String, StageScheduleWindow> windows = parseStageScheduleWindows(scheduleJson);
        windows.forEach((stageCode, window) -> competitionStageRepository.updateStageWindow(
                new CompetitionStageRepository.StageWindowUpdate(
                        competitionId,
                        stageCode,
                        window.stageName(),
                        window.materialStart(),
                        window.materialEnd(),
                        window.reviewStart(),
                        window.reviewEnd(),
                        new CompetitionStageRepository.Actor(userId, userUuid),
                        LocalDateTime.now()
                )
        ));
    }

    private Map<String, StageScheduleWindow> parseStageScheduleWindows(String scheduleJson) {
        return parseStageScheduleWindows(scheduleJson, null, null, StageScheduleValidationMode.DRAFT);
    }

    private Map<String, StageScheduleWindow> parseStageScheduleWindows(
            String scheduleJson,
            String registrationStartValue,
            String registrationEndValue
    ) {
        return parseStageScheduleWindows(
                scheduleJson,
                registrationStartValue,
                registrationEndValue,
                StageScheduleValidationMode.STRICT
        );
    }

    private Map<String, StageScheduleWindow> parseDraftStageScheduleWindows(
            String scheduleJson,
            String registrationStartValue,
            String registrationEndValue
    ) {
        return parseStageScheduleWindows(
                scheduleJson,
                registrationStartValue,
                registrationEndValue,
                StageScheduleValidationMode.DRAFT
        );
    }

    private Map<String, StageScheduleWindow> parseStageScheduleWindows(
            String scheduleJson,
            String registrationStartValue,
            String registrationEndValue,
            StageScheduleValidationMode validationMode
    ) {
        if (!StringUtils.hasText(scheduleJson)) {
            return Map.of();
        }
        try {
            JsonNode schedules = OBJECT_MAPPER.readTree(scheduleJson);
            if (!schedules.isArray()) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Competition schedule JSON must be an array");
            }
            Map<String, StageScheduleWindow> windows = new LinkedHashMap<>();
            TimelineRange registrationWindow = null;
            int confirmedIndex = 0;
            for (JsonNode schedule : schedules) {
                if (!"CONFIRMED".equalsIgnoreCase(schedule.path("timeMode").asText())) {
                    continue;
                }
                String stageCode = trimToNull(schedule.path("stageCode").asText(null));
                if (stageCode == null) {
                    stageCode = confirmedIndex == 0
                            ? "PRELIMINARY"
                            : confirmedIndex == 1 ? "FINAL" : "STAGE_" + (confirmedIndex + 1);
                }
                confirmedIndex += 1;

                String materialStartValue = schedule.path("materialStart").asText(null);
                String materialEndValue = schedule.path("materialEnd").asText(null);
                String reviewStartValue = schedule.path("reviewStart").asText(null);
                String reviewEndValue = schedule.path("reviewEnd").asText(null);
                boolean hasCompleteMaterialWindow = StringUtils.hasText(materialStartValue)
                        && StringUtils.hasText(materialEndValue);
                boolean hasCompleteReviewWindow = StringUtils.hasText(reviewStartValue)
                        && StringUtils.hasText(reviewEndValue);
                if (!hasCompleteMaterialWindow || !hasCompleteReviewWindow) {
                    if (validationMode == StageScheduleValidationMode.DRAFT) {
                        continue;
                    }
                    if (!hasCompleteMaterialWindow) {
                        throw biz(ErrorCode.VALIDATION_ERROR, "提交材料开始和结束时间不能为空");
                    }
                    throw biz(ErrorCode.VALIDATION_ERROR, "评审开始和结束时间不能为空");
                }

                LocalDateTime materialStart = parseTimelineTime(materialStartValue);
                LocalDateTime materialEnd = parseTimelineTime(materialEndValue);
                LocalDateTime reviewStart = parseTimelineTime(reviewStartValue);
                LocalDateTime reviewEnd = parseTimelineTime(reviewEndValue);
                if (validationMode == StageScheduleValidationMode.STRICT && registrationWindow == null) {
                    registrationWindow = parseRegistrationWindow(registrationStartValue, registrationEndValue);
                }
                if (validationMode == StageScheduleValidationMode.STRICT && registrationWindow == null) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "请先设置完整的报名时间");
                }
                requireTimelineRange(materialStart, materialEnd, "Material submission end must be after its start");
                requireTimelineRange(reviewStart, reviewEnd, "Review end must be after its start");
                if (validationMode == StageScheduleValidationMode.STRICT) {
                    if (materialStart.isBefore(registrationWindow.start())
                            || materialEnd.isAfter(registrationWindow.end())) {
                        throw biz(ErrorCode.VALIDATION_ERROR, "提交材料时间必须在报名时间范围内");
                    }
                    if (reviewStart.isBefore(registrationWindow.start())
                            || reviewEnd.isAfter(registrationWindow.end())) {
                        throw biz(ErrorCode.VALIDATION_ERROR, "评审时间必须在报名时间范围内");
                    }
                }
                if (reviewStart.isBefore(materialEnd)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Review cannot start before material submission closes");
                }
                windows.put(stageCode, new StageScheduleWindow(
                        trimToNull(schedule.path("title").asText(null)),
                        materialStart,
                        materialEnd,
                        reviewStart,
                        reviewEnd
                ));
            }
            return windows;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "赛事时间格式无效");
        }
    }

    private TimelineRange parseRegistrationWindow(String startValue, String endValue) {
        boolean hasStart = StringUtils.hasText(startValue);
        boolean hasEnd = StringUtils.hasText(endValue);
        if (!hasStart && !hasEnd) {
            return null;
        }
        LocalDateTime start = parseTimelineTime(startValue);
        LocalDateTime end = parseTimelineTime(endValue);
        requireTimelineRange(start, end, "报名开始和结束时间不能为空");
        return new TimelineRange(start, end);
    }

    private LocalDateTime parseTimelineTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        for (DateTimeFormatter formatter : TIMELINE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported persisted format.
            }
        }
        throw biz(ErrorCode.VALIDATION_ERROR, "赛事时间格式无效");
    }

    private void requireTimelineRange(LocalDateTime start, LocalDateTime end, String message) {
        if (start == null || end == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Timeline start and end time are both required");
        }
        if (!end.isAfter(start)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
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
        boolean enabled = !items.isEmpty();
        CompetitionStageRepository.StageFormSynchronizationResult result = competitionStageRepository.synchronizeStageForm(
                new CompetitionStageRepository.StageFormSynchronization(
                        competitionId,
                        stageCode,
                        stageName,
                        sort,
                        enabled ? buildStageFormSchema(items) : null,
                        enabled,
                        new CompetitionStageRepository.Actor(userId, userUuid),
                        LocalDateTime.now()
                )
        );
        if (result.createdStage()) {
            requireCompetitionWrite(result.stageWriteCount(), "Competition stage changed, please retry");
        }
        if (enabled) {
            requireCompetitionWrite(result.formWriteCount(), "Competition stage form changed, please retry");
        }
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
        long registrationCount = competitionManagementRepository.countActiveRegistrations(id);
        if (registrationCount > 0) {
            throw new BizException(
                    ErrorCode.VALIDATION_ERROR,
                    "Competition has registrations and cannot be deleted",
                    "赛事已有报名，无法删除"
            );
        }
        CompetitionVO.Competition competition = findCompetition(id);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        int updated = competitionManagementRepository.softDeleteCompetition(
                new CompetitionManagementRepository.CompetitionDelete(
                        id,
                        competition.getUuid(),
                        competition.getCompetitionNo(),
                        competition.getStatus(),
                        new CompetitionManagementRepository.Actor(requireUserId(currentUser), requireUserUuid(currentUser)),
                        LocalDateTime.now()
                )
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        recordCatalogChange(
                competition,
                competition.getStatus(),
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                LocalDateTime.now(),
                true
        );
        return true;
    }

    private void recordCatalogChange(
            CompetitionVO.Competition competition,
            String previousStatus,
            Long userId,
            String userUuid,
            LocalDateTime sourceUpdatedAt,
            boolean deleted
    ) {
        if (transactionalEventOutboxPort == null || competition == null || competition.getId() == null) {
            return;
        }
        String eventType;
        if (deleted || ("published".equals(previousStatus) && !"published".equals(competition.getStatus()))) {
            eventType = "archived".equals(competition.getStatus())
                    ? EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED
                    : EventCatalogEventTypes.CATALOG_ITEM_WITHDRAWN;
        } else if ("archived".equals(competition.getStatus())) {
            eventType = EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED;
        } else if ("published".equals(competition.getStatus())) {
            eventType = EventCatalogEventTypes.CATALOG_ITEM_UPSERTED;
        } else {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", userUuid);
        attributes.put("sourceType", "COMPETITION");
        attributes.put("sourceId", competition.getId());
        attributes.put("sourceUuid", competition.getUuid());
        attributes.put("locale", competition.getLocale());
        attributes.put("title", competition.getTitle());
        attributes.put("subtitle", competition.getShortName());
        attributes.put("summary", competition.getDescription());
        attributes.put("status", competition.getStatus());
        attributes.put("registrationStart", competition.getRegistrationStart());
        attributes.put("registrationEnd", competition.getRegistrationEnd());
        attributes.put("eventStart", competition.getCompetitionStart());
        attributes.put("eventEnd", competition.getCompetitionEnd());
        attributes.put("location", competition.getLocation());
        attributes.put("imageUrl", competition.getImageUrl());
        attributes.put("tags", competition.getTags());
        attributes.put("featured", Boolean.TRUE.equals(competition.getFeatured()));
        attributes.put("sort", competition.getSort() == null ? 100 : competition.getSort());
        if (sourceUpdatedAt != null) {
            attributes.put("sourceUpdatedAt", sourceUpdatedAt.toString());
        }
        transactionalEventOutboxPort.record(
                eventType,
                userId,
                "event-catalog.item",
                competition.getId(),
                attributes
        );
    }

    private CompetitionVO.Competition findCompetition(Long id) {
        requirePositiveId(id, "Competition id is required");
        return competitionManagementRepository.findCompetition(id);
    }

    private CompetitionVO.Competition requireCompetitionByUuid(String competitionUuid) {
        String normalized = trimRequired(competitionUuid, "Competition uuid is required");
        CompetitionVO.Competition competition = competitionManagementRepository.findCompetitionByUuid(normalized);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return competition;
    }

    private CompetitionVO.Competition requirePublishedCompetitionByUuid(String competitionUuid) {
        String normalized = trimRequired(competitionUuid, "Competition uuid is required");
        CompetitionVO.Competition competition = competitionManagementRepository.findPublishedCompetitionByUuid(normalized);
        if (competition == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return competition;
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
        normalized.setCategory(dictionaryValueNormalizer.normalizeValue(
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
        normalized.setCategory(dictionaryValueNormalizer.normalizeValue(
                COMPETITION_CATEGORY_DICT,
                firstText(request.getCategory(), existing.getCategory(), "OTHER"),
                existing.getCategory(),
                true,
                "Invalid competition category"
        ));
        String competitionLevel = dictionaryValueNormalizer.normalizeValue(
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
        String competitionLevel = dictionaryValueNormalizer.normalizeValue(
                COMPETITION_LEVEL_DICT,
                request.getCompetitionLevel(),
                request.getLevel(),
                true,
                "Invalid competition level"
        );
        String category = dictionaryValueNormalizer.normalizeValue(
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
            if (!competitionManagementRepository.existsActiveCompetitionNo(candidate)) {
                return candidate;
            }
        }
        throw biz(ErrorCode.BIZ_ERROR, "Failed to generate competition number");
    }

    private CompetitionVO.ConfigSet ensureCurrentConfigSet(CompetitionVO.Competition competition, CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        CompetitionVO.ConfigSet current = competitionSettingsRepository.findCurrentConfigSet(competition.getUuid());
        if (current != null) {
            return current;
        }
        CompetitionSettingsRepository.Actor actor = new CompetitionSettingsRepository.Actor(userId, userUuid);
        CompetitionSettingsRepository.ConfigSetCreateResult created = competitionSettingsRepository.createConfigSet(
                new CompetitionSettingsRepository.ConfigSetCreate(competition.getUuid(), actor)
        );
        requireCompetitionWrite(created.writeCount(), "Competition config set changed, please retry");
        Long id = created.configSetId();
        requireCompetitionWrite(id == null ? 0 : 1, "Competition config set changed, please retry");
        seedDefaultConfigItems(competition.getUuid(), id, actor);
        return requireConfigSet(id);
    }

    private void seedDefaultConfigItems(
            String competitionUuid,
            Long configSetId,
            CompetitionSettingsRepository.Actor actor
    ) {
        if (competitionSettingsRepository.hasActiveConfigItems(configSetId)) {
            return;
        }
        competitionSettingsRepository.seedDefaultConfigItems(
                new CompetitionSettingsRepository.ConfigTemplateSeed(
                        competitionUuid,
                        configSetId,
                        CompetitionStorageSpace.storageKey(competitionUuid),
                        actor
                )
        );
    }

    private void ensureCompetitionStorageSpace(
            Long competitionId,
            String competitionUuid,
            String competitionTitle,
            Long userId,
            String userUuid
    ) {
        fileInternalApi.ensureCompetitionStorageSpace(new CompetitionStorageSpaceRequest(
                competitionId,
                competitionUuid,
                competitionTitle,
                userId,
                userUuid
        ));
    }

    private void enforceCompetitionStorageKey(
            List<CompetitionDTO.ConfigItemRequest> items,
            String storageKey
    ) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        for (CompetitionDTO.ConfigItemRequest item : items) {
            if (!"REQUIRED_FILE".equals(item.getItemType()) && !"STAGE_MATERIAL".equals(item.getItemType())) {
                continue;
            }
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(item.getContentJson());
                if (!(parsed instanceof ObjectNode metadata)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "材料配置格式不正确");
                }
                metadata.put("storageKey", storageKey);
                item.setContentJson(OBJECT_MAPPER.writeValueAsString(metadata));
            } catch (BizException exception) {
                throw exception;
            } catch (Exception exception) {
                throw biz(ErrorCode.VALIDATION_ERROR, "材料配置格式不正确");
            }
        }
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
        competitionSettingsRepository.insertConfigItem(
                new CompetitionSettingsRepository.ConfigItemInsert(
                competitionUuid,
                configSetId,
                itemType,
                itemKey,
                title,
                contentJson,
                contentText,
                sortOrder,
                required,
                enabled,
                new CompetitionSettingsRepository.Actor(userId, userUuid)
                )
        );
    }

    private CompetitionVO.ConfigSet requireConfigSet(Long id) {
        CompetitionVO.ConfigSet configSet = competitionSettingsRepository.findConfigSet(id);
        if (configSet == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition config set not found");
        }
        return configSet;
    }

    private List<CompetitionVO.ConfigItem> listConfigItems(String competitionUuid, Long configSetId, Set<String> itemTypes) {
        return competitionSettingsRepository.findConfigItems(competitionUuid, configSetId, itemTypes);
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
        normalized.setContentText(trimOptional(request.getContentText(), MAX_CONFIG_CONTENT_LENGTH, "Config item text is too long"));
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
        return configItemIdentity(item.getItemType(), item.getItemKey());
    }

    private String configItemIdentity(CompetitionDTO.ConfigItemRequest item) {
        return configItemIdentity(item.getItemType(), item.getItemKey());
    }

    private String configItemIdentity(String itemType, String itemKey) {
        return itemType.trim().toUpperCase(Locale.ROOT) + "\u0000" + itemKey.trim().toLowerCase(Locale.ROOT);
    }

    private void purgePreviousConfigItemTombstones(Long configSetId, List<CompetitionVO.ConfigItem> items) {
        competitionSettingsRepository.purgeConfigItemTombstones(
                configSetId,
                items.stream()
                        .map(item -> new CompetitionSettingsRepository.ConfigItemIdentity(item.getItemType(), item.getItemKey()))
                        .toList()
        );
    }

    private void recordConfigAudit(CurrentUser currentUser, String competitionUuid, String action, String module, String detail) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        competitionSettingsRepository.insertAuditRecord(
                new CompetitionSettingsRepository.ConfigAuditRecord(
                competitionUuid,
                action,
                module,
                detail,
                new CompetitionSettingsRepository.Actor(userId, userUuid)
                )
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
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
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

    private void requireCompetitionWrite(int updated, String message) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
