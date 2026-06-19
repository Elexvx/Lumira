package com.lumira.saas.modules.localization.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.localization.dto.LocalizationDTO;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.EntryQuery;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.LanguageStatRow;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.NamespaceStatRow;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.RuntimeMessageRow;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.EntryEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.LanguageEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.NamespaceEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.ReleaseEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.TranslationEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.UsageRefEntity;
import com.lumira.saas.modules.localization.domain.model.LocalizationDomainModels.ReleaseAggregate;
import com.lumira.saas.modules.localization.mapper.LocalizationEntryMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationLanguageMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationManagementMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationNamespaceMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationReleaseMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationTranslationMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationUsageRefMapper;
import com.lumira.saas.modules.localization.vo.LocalizationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.LongAdder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class LocalizationManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String DEFAULT_STATUS = "ENABLED";
    private static final String DEFAULT_SOURCE_TYPE = "UI";
    private static final String DEFAULT_TRANSLATION_STATUS = "TRANSLATED";
    private static final String PENDING_TRANSLATION_STATUS = "PENDING";
    private static final String DEFAULT_RELEASE_NOTE = "本地化中心发布";
    private static final String READ_MODEL_CONTEXT_LOCALIZATION = "localization";
    private static final String READ_MODEL_EVENT_LOCALIZATION_RUNTIME_BUNDLE = "localization.runtime-bundle";
    private static final long ENTRY_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MILLIS = 3000L;
    private static final long RUNTIME_BUNDLE_FAST_CACHE_TTL_MILLIS = 30_000L;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private static final Map<String, String> LOCALE_DISPLAY_NAMES = Map.of(
            "zh-CN", "简体中文",
            "en-US", "English"
    );

    private static final Map<String, String> SORT_COLUMN_MAPPING = Map.ofEntries(
            Map.entry("localeCode", "locale_code"),
            Map.entry("languageName", "language_name"),
            Map.entry("namespaceCode", "namespace_code"),
            Map.entry("namespaceName", "namespace_name"),
            Map.entry("messageKey", "message_key"),
            Map.entry("updatedAt", "updated_at"),
            Map.entry("createdAt", "created_at")
    );

    private final LocalizationLanguageMapper languageMapper;
    private final LocalizationNamespaceMapper namespaceMapper;
    private final LocalizationEntryMapper entryMapper;
    private final LocalizationTranslationMapper translationMapper;
    private final LocalizationUsageRefMapper usageRefMapper;
    private final LocalizationReleaseMapper releaseMapper;
    private final LocalizationManagementMapper localizationManagementMapper;
    private final ObjectMapper objectMapper;
    private final LocalizationRuntimeBundleCache runtimeBundleCache = new LocalizationRuntimeBundleCache();
    private final SystemInternalApi systemInternalApi;
    private final Map<String, CachedRuntimeBundle> runtimeBundleFastCache = new ConcurrentHashMap<>();
    private final Map<String, CachedReadModelVersion> readModelVersionCache = new ConcurrentHashMap<>();
    private final LongAdder readModelVersionCacheHits = new LongAdder();
    private final LongAdder readModelVersionCacheMisses = new LongAdder();
    private final LongAdder readModelVersionCacheFallbacks = new LongAdder();

    public LocalizationManagementAppService(
            LocalizationLanguageMapper languageMapper,
            LocalizationNamespaceMapper namespaceMapper,
            LocalizationEntryMapper entryMapper,
            LocalizationTranslationMapper translationMapper,
            LocalizationUsageRefMapper usageRefMapper,
            LocalizationReleaseMapper releaseMapper,
            LocalizationManagementMapper localizationManagementMapper,
            ObjectMapper objectMapper,
            SystemInternalApi systemInternalApi
    ) {
        this.languageMapper = languageMapper;
        this.namespaceMapper = namespaceMapper;
        this.entryMapper = entryMapper;
        this.translationMapper = translationMapper;
        this.usageRefMapper = usageRefMapper;
        this.releaseMapper = releaseMapper;
        this.localizationManagementMapper = localizationManagementMapper;
        this.objectMapper = objectMapper;
        this.systemInternalApi = systemInternalApi;
    }

    public List<LocalizationVO.LanguageVO> listLanguages() {
        List<LocalizationVO.LanguageVO> languages = languageMapper.selectList(new QueryWrapper<LanguageEntity>()
                        .eq("deleted", 0)
                        .orderByDesc("is_default")
                        .orderByAsc("sort_no", "id"))
                .stream()
                .map(this::mapLanguage)
                .toList();
        CompletableFuture<Long> totalEntriesFuture = CompletableFuture.supplyAsync(this::countEntries, BLOCKING_IO_EXECUTOR);
        CompletableFuture<Map<String, LanguageStatRow>> languageStatsFuture = CompletableFuture.supplyAsync(this::loadLanguageStats, BLOCKING_IO_EXECUTOR);
        long totalEntries = totalEntriesFuture.join();
        Map<String, LanguageStatRow> languageStats = languageStatsFuture.join();
        for (LocalizationVO.LanguageVO language : languages) {
            enrichLanguageMetrics(language, totalEntries, languageStats.get(language.getLocaleCode()));
        }
        return languages;
    }

    public List<LocalizationVO.NamespaceVO> listNamespaces(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        List<LocalizationVO.NamespaceVO> namespaces = namespaceMapper.selectList(new QueryWrapper<NamespaceEntity>()
                        .eq("deleted", 0)
                        .orderByAsc("sort_no", "id"))
                .stream()
                .map(this::mapNamespace)
                .toList();
        CompletableFuture<Map<String, NamespaceStatRow>> namespaceStatsFuture = CompletableFuture.supplyAsync(() -> loadNamespaceStats(targetLocale), BLOCKING_IO_EXECUTOR);
        Map<String, NamespaceStatRow> namespaceStats = namespaceStatsFuture.join();
        for (LocalizationVO.NamespaceVO namespace : namespaces) {
            enrichNamespaceMetrics(namespace, namespaceStats.get(namespace.getNamespaceCode()));
        }
        return namespaces;
    }

    public LocalizationVO.EntryPageResponse listEntries(
            String localeCode,
            String namespaceCode,
            String keyword,
            String status,
            String translationStatus,
            long pageNo,
            long pageSize,
            String sortField,
            String sortOrder
    ) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safePage = Math.max(1L, pageNo);
        String targetLocale = normalizeLocale(localeCode);
        String fallbackLocale = resolveFallbackLocale(targetLocale);
        EntryQuery query = new EntryQuery();
        query.setTargetLocale(targetLocale);
        query.setFallbackLocale(fallbackLocale);
        query.setLimit(safePageSize);
        if (StringUtils.hasText(namespaceCode)) {
            query.setNamespaceCode(namespaceCode.trim());
        }
        if (StringUtils.hasText(keyword)) {
            query.setKeywordLike(like(keyword));
        }
        if (StringUtils.hasText(status)) {
            query.setStatus(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(translationStatus)) {
            if ("TRANSLATED".equalsIgnoreCase(translationStatus.trim())) {
                query.setTranslationStatus("TRANSLATED");
            } else if ("PENDING".equalsIgnoreCase(translationStatus.trim())) {
                query.setTranslationStatus("PENDING");
            }
        }
        String sortColumn = StringUtils.hasText(sortField)
                ? SORT_COLUMN_MAPPING.getOrDefault(sortField, "e.updated_at")
                : "e.updated_at";
        String sortDirection = "ascend".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        query.setSortColumn(sortColumn);
        query.setSortDirection(sortDirection);

        long offset = (safePage - 1) * safePageSize;
        query.setOffset(offset);
        query.setCountLimit(calculateEntryCountLimit(safePageSize, offset));
        List<LocalizationVO.EntryVO> records = localizationManagementMapper.listEntries(query);
        Long total = localizationManagementMapper.countEntries(query);
        long normalizedTotal = normalizeEntryTotal(total, query);
        boolean totalCapped = isEntryTotalCapped(total, query);
        Map<Long, Map<String, String>> translationsByEntry = loadTranslationMaps(records.stream().map(LocalizationVO.EntryVO::getId).toList());
        records.forEach(record -> record.setTranslations(translationsByEntry.getOrDefault(record.getId(), Map.of())));
        LocalizationVO.EntryPageResponse response = new LocalizationVO.EntryPageResponse();
        response.setRecords(records);
        response.setTotal(normalizedTotal);
        response.setHasMore(totalCapped);
        response.setTotalCapped(totalCapped);
        response.setPageNo(safePage);
        response.setPageSize(safePageSize);
        return response;
    }

    private boolean isEntryTotalCapped(Long total, EntryQuery query) {
        if (query == null) {
            return false;
        }
        long queryLimit = query.getCountLimit();
        if (queryLimit <= 0L) {
            return false;
        }
        return total != null && total >= queryLimit;
    }

    private long normalizeEntryTotal(Long total, EntryQuery query) {
        if (total == null || total <= 0L) {
            return 0L;
        }
        if (query == null || query.getCountLimit() <= 0L) {
            return total;
        }
        return Math.min(total, query.getCountLimit());
    }

    private long calculateEntryCountLimit(long pageSize, long offset) {
        long safePageSize = Math.max(1L, pageSize);
        long safeOffset = Math.max(0L, offset);
        long dynamicLimit = safeOffset + safePageSize + 1L;
        return Math.min(dynamicLimit, ENTRY_LIST_TOTAL_COUNT_CAP);
    }

    @Transactional
    public LocalizationVO.LanguageVO saveLanguage(Long id, LocalizationDTO.LanguageUpsertRequest request) {
        String localeCode = normalizeLocale(request.getLocaleCode());
        boolean isDefault = Boolean.TRUE.equals(request.getDefaultLanguage());
        if (isDefault) {
            languageMapper.update(null, new UpdateWrapper<LanguageEntity>()
                    .set("is_default", 0)
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("deleted", 0));
        }

        Long existingId = id == null ? queryLanguageId(localeCode).orElse(null) : id;
        if (existingId == null) {
            LanguageEntity entity = new LanguageEntity();
            entity.localeCode = localeCode;
            entity.languageName = request.getLanguageName().trim();
            entity.nativeName = normalizeText(request.getNativeName());
            entity.fallbackLocale = normalizeLocaleOrNull(request.getFallbackLocale());
            entity.sortNo = request.getSortNo() == null ? 0 : request.getSortNo();
            entity.isDefault = isDefault ? 1 : 0;
            entity.status = normalizeStatus(request.getStatus());
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            languageMapper.insert(entity);
        } else {
            languageMapper.update(null, new UpdateWrapper<LanguageEntity>()
                    .set("locale_code", localeCode)
                    .set("language_name", request.getLanguageName().trim())
                    .set("native_name", normalizeText(request.getNativeName()))
                    .set("fallback_locale", normalizeLocaleOrNull(request.getFallbackLocale()))
                    .set("sort_no", request.getSortNo() == null ? 0 : request.getSortNo())
                    .set("is_default", isDefault ? 1 : 0)
                    .set("status", normalizeStatus(request.getStatus()))
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", existingId)
                    .eq("deleted", 0));
        }
        return getLanguage(localeCode);
    }

    @Transactional
    public void deleteLanguage(Long id) {
        languageMapper.update(null, new UpdateWrapper<LanguageEntity>()
                .set("deleted", 1)
                .set("updated_by", 0)
                .set("updated_at", LocalDateTime.now())
                .eq("id", id));
    }

    @Transactional
    public LocalizationVO.NamespaceVO saveNamespace(Long id, LocalizationDTO.NamespaceUpsertRequest request) {
        String namespaceCode = request.getNamespaceCode().trim();
        Long existingId = id == null ? queryNamespaceId(namespaceCode).orElse(null) : id;
        if (existingId == null) {
            NamespaceEntity entity = new NamespaceEntity();
            entity.namespaceCode = namespaceCode;
            entity.namespaceName = request.getNamespaceName().trim();
            entity.sourceType = normalizeSourceType(request.getSourceType());
            entity.sourceRef = normalizeText(request.getSourceRef());
            entity.sortNo = request.getSortNo() == null ? 0 : request.getSortNo();
            entity.status = normalizeStatus(request.getStatus());
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            namespaceMapper.insert(entity);
        } else {
            namespaceMapper.update(null, new UpdateWrapper<NamespaceEntity>()
                    .set("namespace_code", namespaceCode)
                    .set("namespace_name", request.getNamespaceName().trim())
                    .set("source_type", normalizeSourceType(request.getSourceType()))
                    .set("source_ref", normalizeText(request.getSourceRef()))
                    .set("sort_no", request.getSortNo() == null ? 0 : request.getSortNo())
                    .set("status", normalizeStatus(request.getStatus()))
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", existingId)
                    .eq("deleted", 0));
        }
        return getNamespace(namespaceCode);
    }

    @Transactional
    public void deleteNamespace(Long id) {
        namespaceMapper.update(null, new UpdateWrapper<NamespaceEntity>()
                .set("deleted", 1)
                .set("updated_by", 0)
                .set("updated_at", LocalDateTime.now())
                .eq("id", id));
    }

    @Transactional
    public LocalizationVO.EntryVO saveEntry(LocalizationDTO.EntryUpsertRequest request) {
        return saveEntryInternal(request);
    }

    @Transactional
    public void deleteEntry(Long id) {
        LocalDateTime now = LocalDateTime.now();
        entryMapper.update(null, new UpdateWrapper<EntryEntity>()
                .set("deleted", 1)
                .set("updated_by", 0)
                .set("updated_at", now)
                .eq("id", id));
        translationMapper.update(null, new UpdateWrapper<TranslationEntity>()
                .set("deleted", 1)
                .set("updated_by", 0)
                .set("updated_at", now)
                .eq("entry_id", id));
        usageRefMapper.update(null, new UpdateWrapper<UsageRefEntity>()
                .set("deleted", 1)
                .set("updated_by", 0)
                .set("updated_at", now)
                .eq("entry_id", id));
    }

    @Transactional
    public LocalizationVO.SyncResultVO sync(LocalizationDTO.SyncRequest request) {
        LocalizationVO.SyncResultVO result = new LocalizationVO.SyncResultVO();
        Map<String, String> localesEncountered = new LinkedHashMap<>();
        localesEncountered.put(normalizeLocale(request.getSourceLocale()), request.getSourceLocale());
        SyncIndexes indexes = loadSyncIndexes();
        for (LocalizationDTO.EntryUpsertRequest item : request.getItems()) {
            syncEntryInternal(item, indexes);
            if (StringUtils.hasText(item.getSourceLocale())) {
                localesEncountered.put(normalizeLocale(item.getSourceLocale()), item.getSourceLocale());
            }
            if (item.getTranslations() != null) {
                item.getTranslations().keySet().forEach(locale -> localesEncountered.put(normalizeLocale(locale), locale));
            }
        }

        result.setLanguageCount(localesEncountered.size());
        result.setNamespaceCount((int) countNamespaces());
        result.setEntryCount((int) countEntries());
        result.setTranslationCount((int) countTranslations());
        result.setUsageCount((int) countUsageRefs());
        return result;
    }

    private void syncEntryInternal(LocalizationDTO.EntryUpsertRequest request, SyncIndexes indexes) {
        String namespaceCode = request.getNamespaceCode().trim();
        NamespaceEntity namespace = indexes.namespacesByCode.get(namespaceCode);
        Long namespaceId = namespace == null ? null : namespace.id;
        if (namespaceId == null) {
            NamespaceEntity newNamespace = new NamespaceEntity();
            newNamespace.namespaceCode = namespaceCode;
            newNamespace.namespaceName = resolveNamespaceName(namespaceCode, request.getSourceType());
            newNamespace.sourceType = normalizeSourceType(request.getSourceType());
            newNamespace.sourceRef = normalizeText(request.getSourceRef());
            newNamespace.sortNo = 0;
            newNamespace.status = normalizeStatus(request.getStatus());
            newNamespace.createdBy = 0L;
            newNamespace.updatedBy = 0L;
            newNamespace.deleted = 0;
            namespaceMapper.insert(newNamespace);
            namespaceId = newNamespace.id;
            indexes.namespacesByCode.put(namespaceCode, newNamespace);
        }

        String key = request.getMessageKey().trim();
        String entryKey = entryKey(namespaceId, key);
        EntryEntity entry = request.getId() != null ? indexes.entriesById.get(request.getId()) : indexes.entriesByNamespaceAndKey.get(entryKey);
        Long entryId = entry == null ? request.getId() : entry.id;
        if (entryId == null) {
            EntryEntity entity = new EntryEntity();
            entity.namespaceId = namespaceId;
            entity.messageKey = key;
            entity.defaultMessage = request.getDefaultMessage().trim();
            entity.sourceLocale = normalizeLocale(request.getSourceLocale());
            entity.sourceType = normalizeSourceType(request.getSourceType());
            entity.sourceRef = normalizeText(request.getSourceRef());
            entity.status = normalizeStatus(request.getStatus());
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            entryMapper.insert(entity);
            entryId = entity.id;
            indexes.entriesByNamespaceAndKey.put(entryKey, entity);
            indexes.entriesById.put(entryId, entity);
        } else if (entry == null || shouldUpdateEntry(entry, namespaceId, request)) {
            entryMapper.update(null, new UpdateWrapper<EntryEntity>()
                    .set("namespace_id", namespaceId)
                    .set("message_key", key)
                    .set("default_message", request.getDefaultMessage().trim())
                    .set("source_locale", normalizeLocale(request.getSourceLocale()))
                    .set("source_type", normalizeSourceType(request.getSourceType()))
                    .set("source_ref", normalizeText(request.getSourceRef()))
                    .set("status", normalizeStatus(request.getStatus()))
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", entryId)
                    .eq("deleted", 0));
            if (entry != null) {
                entry.namespaceId = namespaceId;
                entry.messageKey = key;
                entry.defaultMessage = request.getDefaultMessage().trim();
                entry.sourceLocale = normalizeLocale(request.getSourceLocale());
                entry.sourceType = normalizeSourceType(request.getSourceType());
                entry.sourceRef = normalizeText(request.getSourceRef());
                entry.status = normalizeStatus(request.getStatus());
            }
        }

        upsertUsageRef(entryId, request.getSourceType(), request.getSourceRef(), request.getDefaultMessage(), indexes);
        Map<String, String> translations = new LinkedHashMap<>(request.getTranslations() == null ? Map.of() : request.getTranslations());
        if (StringUtils.hasText(request.getLocaleCode()) || StringUtils.hasText(request.getTranslatedMessage())) {
            translations.put(normalizeLocale(request.getLocaleCode()), request.getTranslatedMessage());
        }
        for (Map.Entry<String, String> translationEntry : translations.entrySet()) {
            upsertTranslation(entryId, translationEntry.getKey(), translationEntry.getValue(), indexes);
        }
    }

    public List<LocalizationVO.ReleaseVO> listReleases(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        return releaseMapper.selectList(new QueryWrapper<ReleaseEntity>()
                        .eq("deleted", 0)
                        .eq("locale_code", targetLocale)
                        .orderByDesc("release_version", "id"))
                .stream()
                .map(this::mapRelease)
                .toList();
    }

    @Transactional
    public LocalizationVO.ReleaseVO publish(LocalizationDTO.PublishRequest request, CurrentUser currentUser) {
        String localeCode = normalizeLocale(request.getLocaleCode());
        LocalizationVO.RuntimeBundleVO bundle = buildRuntimeBundle(localeCode);
        String fallbackLocale = resolveFallbackLocale(localeCode);
        long nextVersion = nextReleaseVersion(localeCode);
        bundle.setReleaseVersion(nextVersion);

        try {
            LocalDateTime now = LocalDateTime.now();
            releaseMapper.update(null, new UpdateWrapper<ReleaseEntity>()
                    .set("active_flag", 0)
                    .set("updated_by", currentUser.getUserId())
                    .set("updated_at", now)
                    .eq("deleted", 0)
                    .eq("locale_code", localeCode));
            String bundleJson = objectMapper.writeValueAsString(bundle);
            ReleaseEntity release = new ReleaseEntity();
            release.localeCode = localeCode;
            release.releaseVersion = nextVersion;
            release.fallbackLocale = fallbackLocale;
            release.bundleJson = bundleJson;
            release.note = StringUtils.hasText(request.getNote()) ? request.getNote().trim() : DEFAULT_RELEASE_NOTE;
            release.activeFlag = 1;
            release.publishedBy = currentUser.getUserId();
            release.publishedAt = now;
            release.createdBy = currentUser.getUserId();
            release.updatedBy = currentUser.getUserId();
            release.deleted = 0;
            releaseMapper.insert(release);
            ReleaseAggregate releaseAggregate = new ReleaseAggregate(
                    release.id == null ? nextVersion : release.id,
                    currentUser.getCurrentTenantId(),
                    localeCode,
                    false
            );
            releaseAggregate.publish(bundle.getMessages() == null ? 0L : bundle.getMessages().size());
            evictRuntimeBundleCache(localeCode);
            bumpRuntimeBundleReadModelVersion(localeCode, currentUser);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("本地化发布失败", error);
        }

        return listReleases(localeCode).stream().findFirst().orElseThrow();
    }

    @Transactional
    public LocalizationVO.ReleaseVO rollback(LocalizationDTO.RollbackRequest request, CurrentUser currentUser) {
        LocalizationVO.ReleaseVO release = getRelease(request.getReleaseId());
        if (release == null) {
            throw new IllegalArgumentException("发布版本不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        releaseMapper.update(null, new UpdateWrapper<ReleaseEntity>()
                .set("active_flag", 0)
                .set("updated_by", currentUser.getUserId())
                .set("updated_at", now)
                .eq("deleted", 0)
                .eq("locale_code", release.getLocaleCode()));
        releaseMapper.update(null, new UpdateWrapper<ReleaseEntity>()
                .set("active_flag", 1)
                .set("updated_by", currentUser.getUserId())
                .set("updated_at", now)
                .eq("id", request.getReleaseId())
                .eq("deleted", 0));
        ReleaseAggregate releaseAggregate = new ReleaseAggregate(
                request.getReleaseId(),
                currentUser.getCurrentTenantId(),
                release.getLocaleCode(),
                false
        );
        releaseAggregate.rollbackTo(release.getReleaseVersion() == null ? 0L : release.getReleaseVersion());
        evictRuntimeBundleCache(release.getLocaleCode());
        bumpRuntimeBundleReadModelVersion(release.getLocaleCode(), currentUser);
        return getRelease(request.getReleaseId());
    }

    public LocalizationVO.RuntimeBundleVO runtimeBundle(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        long now = System.currentTimeMillis();
        CachedRuntimeBundle cached = runtimeBundleFastCache.get(targetLocale);
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            return cached.bundle();
        }
        if (cached != null) {
            runtimeBundleFastCache.remove(targetLocale, cached);
        }
        LocalizationVO.RuntimeBundleVO bundle = buildRuntimeBundle(targetLocale);
        runtimeBundleFastCache.put(targetLocale, new CachedRuntimeBundle(
                bundle,
                now + RUNTIME_BUNDLE_FAST_CACHE_TTL_MILLIS
        ));
        return bundle;
    }

    public int runtimeBundleCacheSize() {
        return runtimeBundleCache.size();
    }

    public long runtimeBundleCacheHits() {
        return runtimeBundleCache.hits();
    }

    public long runtimeBundleCacheMisses() {
        return runtimeBundleCache.misses();
    }

    public double runtimeBundleCacheHitRatio() {
        return runtimeBundleCache.hitRatio();
    }

    public long readModelVersionCacheHits() {
        return readModelVersionCacheHits.sum();
    }

    public long readModelVersionCacheMisses() {
        return readModelVersionCacheMisses.sum();
    }

    public long readModelVersionCacheFallbacks() {
        return readModelVersionCacheFallbacks.sum();
    }

    public double readModelVersionCacheHitRatio() {
        long hits = readModelVersionCacheHits.sum();
        long total = hits + readModelVersionCacheMisses.sum();
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public MetricsSnapshot snapshotMetrics() {
        return new MetricsSnapshot(
                runtimeBundleCache.size(),
                runtimeBundleCache.hits(),
                runtimeBundleCache.misses(),
                runtimeBundleCache.hitRatio(),
                readModelVersionCacheHits(),
                readModelVersionCacheMisses(),
                readModelVersionCacheFallbacks(),
                readModelVersionCacheHitRatio()
        );
    }

    private LocalizationVO.EntryVO saveEntryInternal(LocalizationDTO.EntryUpsertRequest request) {
        String namespaceCode = request.getNamespaceCode().trim();
        LocalizationVO.NamespaceVO namespace = saveNamespace(null, buildNamespaceRequest(request));
        Long namespaceId = queryNamespaceId(namespace.getNamespaceCode()).orElseThrow();
        String key = request.getMessageKey().trim();
        Long entryId = request.getId() != null ? request.getId() : queryEntryId(namespaceId, key).orElse(null);
        if (entryId == null) {
            EntryEntity entity = new EntryEntity();
            entity.namespaceId = namespaceId;
            entity.messageKey = key;
            entity.defaultMessage = request.getDefaultMessage().trim();
            entity.sourceLocale = normalizeLocale(request.getSourceLocale());
            entity.sourceType = normalizeSourceType(request.getSourceType());
            entity.sourceRef = normalizeText(request.getSourceRef());
            entity.status = normalizeStatus(request.getStatus());
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            entryMapper.insert(entity);
            entryId = entity.id;
        } else {
            entryMapper.update(null, new UpdateWrapper<EntryEntity>()
                    .set("namespace_id", namespaceId)
                    .set("message_key", key)
                    .set("default_message", request.getDefaultMessage().trim())
                    .set("source_locale", normalizeLocale(request.getSourceLocale()))
                    .set("source_type", normalizeSourceType(request.getSourceType()))
                    .set("source_ref", normalizeText(request.getSourceRef()))
                    .set("status", normalizeStatus(request.getStatus()))
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", entryId)
                    .eq("deleted", 0));
        }

        upsertUsageRef(entryId, request.getSourceType(), request.getSourceRef(), request.getDefaultMessage());
        Map<String, String> translations = new LinkedHashMap<>(request.getTranslations());
        if (StringUtils.hasText(request.getLocaleCode()) || StringUtils.hasText(request.getTranslatedMessage())) {
            translations.put(normalizeLocale(request.getLocaleCode()), request.getTranslatedMessage());
        }
        for (Map.Entry<String, String> translationEntry : translations.entrySet()) {
            upsertTranslation(entryId, translationEntry.getKey(), translationEntry.getValue());
        }
        String targetLocale = StringUtils.hasText(request.getLocaleCode()) ? request.getLocaleCode() : request.getSourceLocale();
        return getEntry(entryId, normalizeLocale(targetLocale));
    }

    private LocalizationDTO.NamespaceUpsertRequest buildNamespaceRequest(LocalizationDTO.EntryUpsertRequest request) {
        LocalizationDTO.NamespaceUpsertRequest namespace = new LocalizationDTO.NamespaceUpsertRequest();
        namespace.setNamespaceCode(request.getNamespaceCode());
        namespace.setNamespaceName(resolveNamespaceName(request.getNamespaceCode(), request.getSourceType()));
        namespace.setSourceType(request.getSourceType());
        namespace.setSourceRef(request.getSourceRef());
        namespace.setSortNo(0);
        namespace.setStatus(request.getStatus());
        return namespace;
    }

    private void upsertTranslation(Long entryId, String localeCode, String translatedMessage) {
        String normalizedLocale = normalizeLocale(localeCode);
        String value = normalizeText(translatedMessage);
        Long translationId = queryTranslationId(entryId, normalizedLocale).orElse(null);
        if (!StringUtils.hasText(value)) {
            if (translationId != null) {
                translationMapper.update(null, new UpdateWrapper<TranslationEntity>()
                        .set("deleted", 1)
                        .set("updated_by", 0)
                        .set("updated_at", LocalDateTime.now())
                        .eq("id", translationId)
                        .eq("deleted", 0));
            }
            return;
        }

        if (translationId == null) {
            TranslationEntity entity = new TranslationEntity();
            entity.entryId = entryId;
            entity.localeCode = normalizedLocale;
            entity.translatedMessage = value;
            entity.translationStatus = DEFAULT_TRANSLATION_STATUS;
            entity.machineGenerated = 0;
            entity.reviewStatus = "PENDING";
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            translationMapper.insert(entity);
        } else {
            translationMapper.update(null, new UpdateWrapper<TranslationEntity>()
                    .set("translated_message", value)
                    .set("translation_status", DEFAULT_TRANSLATION_STATUS)
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", translationId)
                    .eq("deleted", 0));
        }
    }

    private void upsertTranslation(Long entryId, String localeCode, String translatedMessage, SyncIndexes indexes) {
        String normalizedLocale = normalizeLocale(localeCode);
        String value = normalizeText(translatedMessage);
        String key = translationKey(entryId, normalizedLocale);
        TranslationEntity translation = indexes.translationsByEntryAndLocale.get(key);
        Long translationId = translation == null ? null : translation.id;
        if (!StringUtils.hasText(value)) {
            if (translationId != null) {
                translationMapper.update(null, new UpdateWrapper<TranslationEntity>()
                        .set("deleted", 1)
                        .set("updated_by", 0)
                        .set("updated_at", LocalDateTime.now())
                        .eq("id", translationId)
                        .eq("deleted", 0));
                indexes.translationsByEntryAndLocale.remove(key);
            }
            return;
        }

        if (translationId == null) {
            TranslationEntity entity = new TranslationEntity();
            entity.entryId = entryId;
            entity.localeCode = normalizedLocale;
            entity.translatedMessage = value;
            entity.translationStatus = DEFAULT_TRANSLATION_STATUS;
            entity.machineGenerated = 0;
            entity.reviewStatus = "PENDING";
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            translationMapper.insert(entity);
            indexes.translationsByEntryAndLocale.put(key, entity);
        } else if (!sameText(translation.translatedMessage, value) || !DEFAULT_TRANSLATION_STATUS.equalsIgnoreCase(String.valueOf(translation.translationStatus))) {
            translationMapper.update(null, new UpdateWrapper<TranslationEntity>()
                    .set("translated_message", value)
                    .set("translation_status", DEFAULT_TRANSLATION_STATUS)
                    .set("updated_by", 0)
                    .set("updated_at", LocalDateTime.now())
                    .eq("id", translationId)
                    .eq("deleted", 0));
            translation.translatedMessage = value;
            translation.translationStatus = DEFAULT_TRANSLATION_STATUS;
        }
    }

    private void upsertUsageRef(Long entryId, String sourceType, String sourceRef, String sourceText) {
        if (!StringUtils.hasText(sourceRef)) {
            return;
        }
        Long usageId = queryUsageRefId(entryId, normalizeSourceType(sourceType), sourceRef.trim(), null).orElse(null);
        if (usageId == null) {
            UsageRefEntity entity = new UsageRefEntity();
            entity.entryId = entryId;
            entity.sourceType = normalizeSourceType(sourceType);
            entity.sourceRef = sourceRef.trim();
            entity.sourceLine = null;
            entity.sourceText = sourceText;
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            usageRefMapper.insert(entity);
            return;
        }

        usageRefMapper.update(null, new UpdateWrapper<UsageRefEntity>()
                .set("source_text", sourceText)
                .set("updated_by", 0)
                .set("updated_at", LocalDateTime.now())
                .eq("id", usageId)
                .eq("deleted", 0));
    }

    private void upsertUsageRef(Long entryId, String sourceType, String sourceRef, String sourceText, SyncIndexes indexes) {
        if (!StringUtils.hasText(sourceRef)) {
            return;
        }
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedSourceRef = sourceRef.trim();
        String key = usageRefKey(entryId, normalizedSourceType, normalizedSourceRef, null);
        UsageRefEntity usageRef = indexes.usageRefsByEntryAndSource.get(key);
        Long usageId = usageRef == null ? null : usageRef.id;
        if (usageId == null) {
            UsageRefEntity entity = new UsageRefEntity();
            entity.entryId = entryId;
            entity.sourceType = normalizedSourceType;
            entity.sourceRef = normalizedSourceRef;
            entity.sourceLine = null;
            entity.sourceText = sourceText;
            entity.createdBy = 0L;
            entity.updatedBy = 0L;
            entity.deleted = 0;
            usageRefMapper.insert(entity);
            indexes.usageRefsByEntryAndSource.put(key, entity);
            return;
        }

        if (sameText(usageRef.sourceText, sourceText)) {
            return;
        }
        usageRefMapper.update(null, new UpdateWrapper<UsageRefEntity>()
                .set("source_text", sourceText)
                .set("updated_by", 0)
                .set("updated_at", LocalDateTime.now())
                .eq("id", usageId)
                .eq("deleted", 0));
        usageRef.sourceText = sourceText;
    }

    private LocalizationVO.RuntimeBundleVO buildRuntimeBundle(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        String fallbackLocale = resolveFallbackLocale(targetLocale);
        LocalizationVO.RuntimeBundleVO bundle = new LocalizationVO.RuntimeBundleVO();
        bundle.setLocaleCode(targetLocale);
        bundle.setFallbackLocale(fallbackLocale);
        long readModelVersion = readModelVersionForLocale(targetLocale);

        LocalizationVO.ReleaseVO release = getActiveRelease(targetLocale);
        if (release != null) {
            bundle.setReleaseVersion(release.getReleaseVersion());
            runtimeBundleCache.evictStale(targetLocale, release.getReleaseVersion());
            LocalizationVO.RuntimeBundleVO cached = runtimeBundleCache.get(targetLocale, readModelVersion, release.getReleaseVersion());
            if (cached != null) {
                return cached;
            }
            try {
                ReleaseEntity releaseEntity = releaseMapper.selectById(release.getId());
                LocalizationVO.RuntimeBundleVO storedBundle = objectMapper.readValue(
                        releaseEntity == null ? null : releaseEntity.bundleJson,
                        LocalizationVO.RuntimeBundleVO.class
                );
                if (storedBundle != null && storedBundle.getMessages() != null && !storedBundle.getMessages().isEmpty()) {
                    runtimeBundleCache.put(targetLocale, readModelVersion, release.getReleaseVersion(), storedBundle);
                    return storedBundle;
                }
            } catch (Exception ignored) {
                // Fallback to live data below when bundle json cannot be parsed.
            }
        }

        LocalizationVO.RuntimeBundleVO cachedFallback = runtimeBundleCache.get(targetLocale, readModelVersion, 0L);
        if (cachedFallback != null) {
            return cachedFallback;
        }
        bundle.setReleaseVersion(0L);
        bundle.setMessages(loadRuntimeMessages(targetLocale, fallbackLocale));
        runtimeBundleCache.put(targetLocale, readModelVersion, 0L, bundle);
        return bundle;
    }

    private void evictRuntimeBundleCache(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        runtimeBundleFastCache.remove(targetLocale);
        runtimeBundleCache.evictLocale(targetLocale);
    }

    private long readModelVersionForLocale(String localeCode) {
        return readModelVersionForTenantAndScope(PlatformConstants.PLATFORM_TENANT_ID, normalizeLocale(localeCode));
    }

    private long readModelVersionForTenantAndScope(Long tenantId, String scope) {
        String normalizedScope = normalizeLocale(scope);
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        String cacheKey = readModelVersionCacheKey(effectiveTenantId, normalizedScope);
        CachedReadModelVersion cached = readModelVersionCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            readModelVersionCacheHits.increment();
            return cached.version();
        }
        if (cached != null) {
            readModelVersionCacheMisses.increment();
            readModelVersionCache.remove(cacheKey);
        }

        long version = 0L;
        if (cached == null) {
            readModelVersionCacheMisses.increment();
        }
        try {
            Long actualVersion = systemInternalApi.readModelVersion(effectiveTenantId, READ_MODEL_CONTEXT_LOCALIZATION, normalizedScope);
            if (actualVersion != null) {
                version = actualVersion;
            } else {
                readModelVersionCacheFallbacks.increment();
            }
        } catch (Exception ignored) {
            // Keep runtime bundle read-path resilient when read-model infra is temporarily unavailable.
            readModelVersionCacheFallbacks.increment();
        }
        readModelVersionCache.put(cacheKey, new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private void bumpRuntimeBundleReadModelVersion(String localeCode, CurrentUser currentUser) {
        String normalizedLocale = normalizeLocale(localeCode);
        Long tenantId = currentUser == null || currentUser.getCurrentTenantId() == null
                ? PlatformConstants.PLATFORM_TENANT_ID
                : currentUser.getCurrentTenantId();
        readModelVersionCache.remove(readModelVersionCacheKey(tenantId, normalizedLocale));
        try {
            systemInternalApi.bumpReadModelVersion(
                    tenantId,
                    READ_MODEL_CONTEXT_LOCALIZATION,
                    normalizedLocale,
                    READ_MODEL_EVENT_LOCALIZATION_RUNTIME_BUNDLE
            );
        } catch (Exception ignored) {
            // Keep write operations stable when read-model infra is temporarily unavailable.
        }
    }

    private String readModelVersionCacheKey(Long tenantId, String scope) {
        return tenantId + ":" + normalizeLocale(scope);
    }

    private Map<String, String> loadRuntimeMessages(String localeCode, String fallbackLocale) {
        List<RuntimeMessageRow> rows = localizationManagementMapper.listRuntimeMessages(localeCode, fallbackLocale);
        Map<String, String> messages = new LinkedHashMap<>();
        for (RuntimeMessageRow row : rows) {
            String key = row.getMessageKey();
            String defaultMessage = row.getDefaultMessage();
            String targetMessage = row.getTargetMessage();
            String fallbackMessage = row.getFallbackMessage();
            String resolved = StringUtils.hasText(targetMessage)
                    ? targetMessage
                    : (StringUtils.hasText(fallbackMessage) ? fallbackMessage : (StringUtils.hasText(defaultMessage) ? defaultMessage : key));
            messages.put(key, resolved);
        }
        return messages;
    }

    private void enrichLanguageMetrics(LocalizationVO.LanguageVO language, long totalEntries, LanguageStatRow stats) {
        long translatedCount = stats == null || stats.getTranslatedCount() == null ? 0L : stats.getTranslatedCount();
        language.setEntryCount(totalEntries);
        language.setTranslatedCount(translatedCount);
        language.setCoverageRate(totalEntries == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(translatedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalEntries), 2, RoundingMode.HALF_UP));
        language.setPublishedVersion(stats == null || stats.getPublishedVersion() == null ? 0L : stats.getPublishedVersion());
        language.setLastPublishedAt(stats == null ? null : stats.getLastPublishedAt());
    }

    private void enrichNamespaceMetrics(LocalizationVO.NamespaceVO namespace, NamespaceStatRow stats) {
        long entryCount = stats == null || stats.getEntryCount() == null ? 0L : stats.getEntryCount();
        long translatedCount = stats == null || stats.getTranslatedCount() == null ? 0L : stats.getTranslatedCount();
        namespace.setEntryCount(entryCount);
        namespace.setTranslatedCount(translatedCount);
        namespace.setCoverageRate(entryCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(translatedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(entryCount), 2, RoundingMode.HALF_UP));
    }

    private LocalizationVO.LanguageVO mapLanguage(LanguageEntity entity) {
        LocalizationVO.LanguageVO vo = new LocalizationVO.LanguageVO();
        vo.setId(entity.id);
        vo.setLocaleCode(entity.localeCode);
        vo.setLanguageName(entity.languageName);
        vo.setNativeName(entity.nativeName);
        vo.setFallbackLocale(entity.fallbackLocale);
        vo.setSortNo(entity.sortNo);
        vo.setStatus(entity.status);
        vo.setDefaultLanguage(entity.isDefault != null && entity.isDefault == 1);
        return vo;
    }

    private LocalizationVO.NamespaceVO mapNamespace(NamespaceEntity entity) {
        LocalizationVO.NamespaceVO vo = new LocalizationVO.NamespaceVO();
        vo.setId(entity.id);
        vo.setNamespaceCode(entity.namespaceCode);
        vo.setNamespaceName(entity.namespaceName);
        vo.setSourceType(entity.sourceType);
        vo.setSourceRef(entity.sourceRef);
        vo.setSortNo(entity.sortNo);
        vo.setStatus(entity.status);
        return vo;
    }

    private LocalizationVO.ReleaseVO mapRelease(ReleaseEntity entity) {
        LocalizationVO.ReleaseVO vo = new LocalizationVO.ReleaseVO();
        vo.setId(entity.id);
        vo.setLocaleCode(entity.localeCode);
        vo.setReleaseVersion(entity.releaseVersion);
        vo.setFallbackLocale(entity.fallbackLocale);
        vo.setNote(entity.note);
        vo.setActive(entity.activeFlag != null && entity.activeFlag == 1);
        vo.setPublishedBy(entity.publishedBy);
        vo.setPublishedAt(entity.publishedAt);
        return vo;
    }

    private Optional<Long> queryLanguageId(String localeCode) {
        LanguageEntity entity = languageMapper.selectOne(new QueryWrapper<LanguageEntity>()
                .select("id")
                .eq("locale_code", localeCode)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.id);
    }

    private Optional<Long> queryNamespaceId(String namespaceCode) {
        NamespaceEntity entity = namespaceMapper.selectOne(new QueryWrapper<NamespaceEntity>()
                .select("id")
                .eq("namespace_code", namespaceCode)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.id);
    }

    private Optional<Long> queryEntryId(Long namespaceId, String messageKey) {
        EntryEntity entity = entryMapper.selectOne(new QueryWrapper<EntryEntity>()
                .select("id")
                .eq("namespace_id", namespaceId)
                .eq("message_key", messageKey)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.id);
    }

    private Optional<Long> queryTranslationId(Long entryId, String localeCode) {
        TranslationEntity entity = translationMapper.selectOne(new QueryWrapper<TranslationEntity>()
                .select("id")
                .eq("entry_id", entryId)
                .eq("locale_code", localeCode)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.id);
    }

    private Optional<Long> queryUsageRefId(Long entryId, String sourceType, String sourceRef, Integer sourceLine) {
        QueryWrapper<UsageRefEntity> queryWrapper = new QueryWrapper<UsageRefEntity>()
                .select("id")
                .eq("entry_id", entryId)
                .eq("source_type", sourceType)
                .eq("source_ref", sourceRef)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("limit 1");
        if (sourceLine == null) {
            queryWrapper.isNull("source_line");
        } else {
            queryWrapper.eq("source_line", sourceLine);
        }
        UsageRefEntity entity = usageRefMapper.selectOne(queryWrapper);
        return Optional.ofNullable(entity == null ? null : entity.id);
    }

    private LocalizationVO.LanguageVO getLanguage(String localeCode) {
        LanguageEntity entity = languageMapper.selectOne(new QueryWrapper<LanguageEntity>()
                .eq("locale_code", localeCode)
                .eq("deleted", 0)
                .last("limit 1"));
        return mapLanguage(entity);
    }

    private LocalizationVO.NamespaceVO getNamespace(String namespaceCode) {
        NamespaceEntity entity = namespaceMapper.selectOne(new QueryWrapper<NamespaceEntity>()
                .eq("namespace_code", namespaceCode)
                .eq("deleted", 0)
                .last("limit 1"));
        return mapNamespace(entity);
    }

    private LocalizationVO.EntryVO getEntry(Long entryId, String localeCode) {
        String fallbackLocale = resolveFallbackLocale(localeCode);
        return localizationManagementMapper.findEntry(entryId, localeCode, fallbackLocale);
    }

    private LocalizationVO.ReleaseVO getRelease(Long releaseId) {
        ReleaseEntity entity = releaseMapper.selectOne(new QueryWrapper<ReleaseEntity>()
                .eq("id", releaseId)
                .eq("deleted", 0)
                .last("limit 1"));
        return entity == null ? null : mapRelease(entity);
    }

    private LocalizationVO.ReleaseVO getActiveRelease(String localeCode) {
        ReleaseEntity entity = releaseMapper.selectOne(new QueryWrapper<ReleaseEntity>()
                .eq("locale_code", localeCode)
                .eq("active_flag", 1)
                .eq("deleted", 0)
                .orderByDesc("release_version", "id")
                .last("limit 1"));
        return entity == null ? null : mapRelease(entity);
    }

    private Optional<Long> getActiveReleaseVersion(String localeCode) {
        ReleaseEntity entity = releaseMapper.selectOne(new QueryWrapper<ReleaseEntity>()
                .select("release_version")
                .eq("locale_code", localeCode)
                .eq("active_flag", 1)
                .eq("deleted", 0)
                .orderByDesc("release_version", "id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.releaseVersion);
    }

    private Optional<LocalDateTime> getLastPublishedAt(String localeCode) {
        ReleaseEntity entity = releaseMapper.selectOne(new QueryWrapper<ReleaseEntity>()
                .select("published_at")
                .eq("locale_code", localeCode)
                .eq("deleted", 0)
                .orderByDesc("published_at", "id")
                .last("limit 1"));
        return Optional.ofNullable(entity == null ? null : entity.publishedAt);
    }

    private long nextReleaseVersion(String localeCode) {
        ReleaseEntity entity = releaseMapper.selectOne(new QueryWrapper<ReleaseEntity>()
                .select("release_version")
                .eq("locale_code", localeCode)
                .eq("deleted", 0)
                .orderByDesc("release_version")
                .last("limit 1"));
        return (entity == null || entity.releaseVersion == null ? 0L : entity.releaseVersion) + 1L;
    }

    private long countEntries() {
        return count(entryMapper.selectCount(new QueryWrapper<EntryEntity>().eq("deleted", 0)));
    }

    private long countNamespaces() {
        return count(namespaceMapper.selectCount(new QueryWrapper<NamespaceEntity>().eq("deleted", 0)));
    }

    private long countTranslations() {
        return count(translationMapper.selectCount(new QueryWrapper<TranslationEntity>().eq("deleted", 0)));
    }

    private long countUsageRefs() {
        return count(usageRefMapper.selectCount(new QueryWrapper<UsageRefEntity>().eq("deleted", 0)));
    }

    private long count(Long count) {
        return count == null ? 0L : count;
    }

    private long countTranslations(String localeCode) {
        Long count = localizationManagementMapper.countTranslatedEntries(localeCode);
        return count == null ? 0L : count;
    }

    private long countEntriesByNamespace(String namespaceCode) {
        Long count = localizationManagementMapper.countEntriesByNamespace(namespaceCode);
        return count == null ? 0L : count;
    }

    private long countTranslationsByNamespace(String namespaceCode, String localeCode) {
        Long count = localizationManagementMapper.countTranslatedEntriesByNamespace(namespaceCode, localeCode);
        return count == null ? 0L : count;
    }

    private Map<String, LanguageStatRow> loadLanguageStats() {
        List<LanguageStatRow> rows = localizationManagementMapper.listLanguageStats();
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row != null && StringUtils.hasText(row.getLocaleCode()))
                .collect(Collectors.toMap(
                        row -> row.getLocaleCode(),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, NamespaceStatRow> loadNamespaceStats(String localeCode) {
        List<NamespaceStatRow> rows = localizationManagementMapper.listNamespaceStats(localeCode);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row != null && StringUtils.hasText(row.getNamespaceCode()))
                .collect(Collectors.toMap(
                        row -> row.getNamespaceCode(),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> loadTranslationMap(Long entryId) {
        Map<String, String> translations = new LinkedHashMap<>();
        List<TranslationEntity> entities = translationMapper.selectList(new QueryWrapper<TranslationEntity>()
                .eq("entry_id", entryId)
                .eq("deleted", 0)
                .orderByAsc("locale_code"));
        for (TranslationEntity entity : entities) {
            if (StringUtils.hasText(entity.localeCode) && StringUtils.hasText(entity.translatedMessage)) {
                translations.put(entity.localeCode, entity.translatedMessage);
            }
        }
        return translations;
    }

    private Map<Long, Map<String, String>> loadTranslationMaps(List<Long> entryIds) {
        List<Long> distinctEntryIds = entryIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctEntryIds.isEmpty()) {
            return Map.of();
        }
        List<TranslationEntity> entities = translationMapper.selectList(new QueryWrapper<TranslationEntity>()
                .in("entry_id", distinctEntryIds)
                .eq("deleted", 0)
                .orderByAsc("entry_id", "locale_code"));
        Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (TranslationEntity entity : entities) {
            if (entity.entryId == null) {
                continue;
            }
            if (StringUtils.hasText(entity.localeCode) && StringUtils.hasText(entity.translatedMessage)) {
                result.computeIfAbsent(entity.entryId, ignored -> new LinkedHashMap<>()).put(entity.localeCode, entity.translatedMessage);
            }
        }
        return result;
    }

    private SyncIndexes loadSyncIndexes() {
        SyncIndexes indexes = new SyncIndexes();
        namespaceMapper.selectList(new QueryWrapper<NamespaceEntity>().eq("deleted", 0))
                .forEach(namespace -> indexes.namespacesByCode.put(namespace.namespaceCode, namespace));
        entryMapper.selectList(new QueryWrapper<EntryEntity>().eq("deleted", 0)).forEach(entry -> {
            indexes.entriesByNamespaceAndKey.put(entryKey(entry.namespaceId, entry.messageKey), entry);
            indexes.entriesById.put(entry.id, entry);
        });
        translationMapper.selectList(new QueryWrapper<TranslationEntity>().eq("deleted", 0))
                .forEach(translation -> indexes.translationsByEntryAndLocale.put(translationKey(translation.entryId, translation.localeCode), translation));
        usageRefMapper.selectList(new QueryWrapper<UsageRefEntity>().eq("deleted", 0))
                .forEach(usageRef -> indexes.usageRefsByEntryAndSource.put(usageRefKey(usageRef.entryId, usageRef.sourceType, usageRef.sourceRef, usageRef.sourceLine), usageRef));
        return indexes;
    }

    private String entryKey(Long namespaceId, String messageKey) {
        return namespaceId + "\u0000" + messageKey;
    }

    private String translationKey(Long entryId, String localeCode) {
        return entryId + "\u0000" + normalizeLocale(localeCode);
    }

    private String usageRefKey(Long entryId, String sourceType, String sourceRef, Integer sourceLine) {
        return entryId + "\u0000" + normalizeSourceType(sourceType) + "\u0000" + sourceRef + "\u0000" + (sourceLine == null ? "" : sourceLine);
    }

    private boolean shouldUpdateEntry(EntryEntity entry, Long namespaceId, LocalizationDTO.EntryUpsertRequest request) {
        return !sameLong(entry.namespaceId, namespaceId)
                || !sameText(entry.messageKey, request.getMessageKey().trim())
                || !sameText(entry.defaultMessage, request.getDefaultMessage().trim())
                || !sameText(entry.sourceLocale, normalizeLocale(request.getSourceLocale()))
                || !sameText(entry.sourceType, normalizeSourceType(request.getSourceType()))
                || !sameText(entry.sourceRef, normalizeText(request.getSourceRef()))
                || !sameText(entry.status, normalizeStatus(request.getStatus()));
    }

    private boolean sameLong(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return normalizeCompareText(left).equals(normalizeCompareText(right));
    }

    private String normalizeCompareText(String value) {
        return value == null ? "" : value;
    }

    private static class SyncIndexes {
        private final Map<String, NamespaceEntity> namespacesByCode = new HashMap<>();
        private final Map<String, EntryEntity> entriesByNamespaceAndKey = new HashMap<>();
        private final Map<Long, EntryEntity> entriesById = new HashMap<>();
        private final Map<String, TranslationEntity> translationsByEntryAndLocale = new HashMap<>();
        private final Map<String, UsageRefEntity> usageRefsByEntryAndSource = new HashMap<>();
    }

    public record MetricsSnapshot(
            int runtimeBundleCacheSize,
            long runtimeBundleCacheHits,
            long runtimeBundleCacheMisses,
            double runtimeBundleCacheHitRatio,
            long readModelVersionCacheHits,
            long readModelVersionCacheMisses,
            long readModelVersionCacheFallbacks,
            double readModelVersionCacheHitRatio
    ) {
    }

    private String resolveFallbackLocale(String localeCode) {
        if (!StringUtils.hasText(localeCode)) {
            return DEFAULT_LOCALE;
        }
        String normalized = normalizeLocale(localeCode);
        LanguageEntity language = languageMapper.selectOne(new QueryWrapper<LanguageEntity>()
                .select("fallback_locale")
                .eq("locale_code", normalized)
                .eq("deleted", 0)
                .orderByDesc("is_default")
                .orderByAsc("sort_no", "id")
                .last("limit 1"));
        String fallback = language == null ? null : language.fallbackLocale;
        if (StringUtils.hasText(fallback)) {
            return normalizeLocale(fallback);
        }
        return DEFAULT_LOCALE.equalsIgnoreCase(normalized) ? DEFAULT_LOCALE : DEFAULT_LOCALE;
    }

    private String resolveNamespaceName(String namespaceCode, String sourceType) {
        if (!StringUtils.hasText(namespaceCode)) {
            return "默认";
        }
        return switch (namespaceCode.trim()) {
            case "common" -> "公共";
            case "nav" -> "导航";
            case "page" -> "页面";
            case "message" -> "消息";
            case "theme" -> "主题";
            case "tenant" -> "平台";
            case "auth" -> "认证";
            case "system" -> "系统";
            default -> StringUtils.hasText(sourceType) ? namespaceCode.trim() + " · " + normalizeSourceType(sourceType) : namespaceCode.trim();
        };
    }

    private String normalizeLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return DEFAULT_LOCALE;
        }

        String trimmed = locale.trim();
        if ("zh".equalsIgnoreCase(trimmed)) {
            return DEFAULT_LOCALE;
        }
        if ("en".equalsIgnoreCase(trimmed)) {
            return "en-US";
        }
        if (!trimmed.matches("^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")) {
            return DEFAULT_LOCALE;
        }
        String[] segments = trimmed.split("-");
        if (segments.length == 1) {
            return segments[0].toLowerCase(Locale.ROOT);
        }
        StringJoiner joiner = new StringJoiner("-");
        joiner.add(segments[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.length() == 2) {
                joiner.add(segment.toUpperCase(Locale.ROOT));
            } else {
                joiner.add(segment);
            }
        }
        return joiner.toString();
    }

    private String normalizeLocaleOrNull(String locale) {
        return StringUtils.hasText(locale) ? normalizeLocale(locale) : null;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : DEFAULT_STATUS;
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : DEFAULT_SOURCE_TYPE;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private record CachedReadModelVersion(long version, long expiresAtEpochMillis) {
    }

    private record CachedRuntimeBundle(LocalizationVO.RuntimeBundleVO bundle, long expiresAtEpochMillis) {
    }

}
