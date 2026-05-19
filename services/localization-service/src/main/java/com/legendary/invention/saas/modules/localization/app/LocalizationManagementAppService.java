package com.legendary.invention.saas.modules.localization.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.saas.modules.localization.dto.LocalizationDTO;
import com.legendary.invention.saas.modules.localization.vo.LocalizationVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class LocalizationManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String DEFAULT_STATUS = "ENABLED";
    private static final String DEFAULT_SOURCE_TYPE = "UI";
    private static final String DEFAULT_TRANSLATION_STATUS = "TRANSLATED";
    private static final String PENDING_TRANSLATION_STATUS = "PENDING";
    private static final String DEFAULT_RELEASE_NOTE = "本地化中心发布";

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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LocalizationManagementAppService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<LocalizationVO.LanguageVO> listLanguages() {
        List<LocalizationVO.LanguageVO> languages = jdbcTemplate.query(
                """
                        select
                          l.id,
                          l.locale_code as localeCode,
                          l.language_name as languageName,
                          l.native_name as nativeName,
                          l.fallback_locale as fallbackLocale,
                          l.sort_no as sortNo,
                          l.status,
                          l.is_default as defaultLanguage
                        from sys_localization_language l
                        where l.deleted = 0
                        order by l.is_default desc, l.sort_no asc, l.id asc
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.LanguageVO.class)
        );
        long totalEntries = countEntries();
        for (LocalizationVO.LanguageVO language : languages) {
            enrichLanguageMetrics(language, totalEntries);
        }
        return languages;
    }

    public List<LocalizationVO.NamespaceVO> listNamespaces(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        List<LocalizationVO.NamespaceVO> namespaces = jdbcTemplate.query(
                """
                        select
                          n.id,
                          n.namespace_code as namespaceCode,
                          n.namespace_name as namespaceName,
                          n.source_type as sourceType,
                          n.source_ref as sourceRef,
                          n.sort_no as sortNo,
                          n.status
                        from sys_localization_namespace n
                        where n.deleted = 0
                        order by n.sort_no asc, n.id asc
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.NamespaceVO.class)
        );
        for (LocalizationVO.NamespaceVO namespace : namespaces) {
            enrichNamespaceMetrics(namespace, targetLocale);
        }
        return namespaces;
    }

    public PageResponse<LocalizationVO.EntryVO> listEntries(
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
        StringBuilder baseSql = new StringBuilder("""
                from sys_localization_entry e
                join sys_localization_namespace n on n.id = e.namespace_id and n.deleted = 0
                left join sys_localization_translation t_target on t_target.entry_id = e.id and t_target.locale_code = ? and t_target.deleted = 0
                left join sys_localization_translation t_fallback on t_fallback.entry_id = e.id and t_fallback.locale_code = ? and t_fallback.deleted = 0
                left join (
                    select entry_id, count(1) as usageCount
                    from sys_localization_usage_ref
                    where deleted = 0
                    group by entry_id
                ) u on u.entry_id = e.id
                where e.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(targetLocale);
        params.add(fallbackLocale);
        if (StringUtils.hasText(namespaceCode)) {
            baseSql.append(" and n.namespace_code = ?");
            params.add(namespaceCode.trim());
        }
        if (StringUtils.hasText(keyword)) {
            baseSql.append(" and (e.message_key like ? or e.default_message like ? or n.namespace_name like ? or e.source_ref like ?)");
            String likeKeyword = like(keyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (StringUtils.hasText(status)) {
            baseSql.append(" and e.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(translationStatus)) {
            if ("TRANSLATED".equalsIgnoreCase(translationStatus.trim())) {
                baseSql.append(" and coalesce(t_target.translated_message, t_fallback.translated_message, '') <> ''");
            } else if ("PENDING".equalsIgnoreCase(translationStatus.trim())) {
                baseSql.append(" and coalesce(t_target.translated_message, t_fallback.translated_message, '') = ''");
            }
        }
        String sortColumn = StringUtils.hasText(sortField)
                ? SORT_COLUMN_MAPPING.getOrDefault(sortField, "e.updated_at")
                : "e.updated_at";
        String sortDirection = "ascend".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        String selectSql = """
                select
                  e.id,
                  n.namespace_code as namespaceCode,
                  n.namespace_name as namespaceName,
                  e.message_key as messageKey,
                  e.default_message as defaultMessage,
                  e.source_locale as sourceLocale,
                  e.source_type as sourceType,
                  e.source_ref as sourceRef,
                  e.status,
                  case
                    when coalesce(t_target.translated_message, t_fallback.translated_message, '') <> '' then 'TRANSLATED'
                    else 'PENDING'
                  end as translationStatus,
                  coalesce(t_target.translated_message, t_fallback.translated_message, '') as currentTranslation,
                  coalesce(u.usageCount, 0) as usageCount,
                  e.created_at as createdAt,
                  e.updated_at as updatedAt
                """ + baseSql
                + " order by " + sortColumn + " " + sortDirection
                + " limit ? offset ?";

        List<Object> pagedParams = new ArrayList<>(params);
        pagedParams.add(safePageSize);
        pagedParams.add((safePage - 1) * safePageSize);

        List<LocalizationVO.EntryVO> records = jdbcTemplate.query(selectSql, new BeanPropertyRowMapper<>(LocalizationVO.EntryVO.class), pagedParams.toArray());
        Long total = jdbcTemplate.queryForObject("select count(1) " + baseSql, Long.class, params.toArray());
        Map<Long, Map<String, String>> translationsByEntry = loadTranslationMaps(records.stream().map(LocalizationVO.EntryVO::getId).toList());
        records.forEach(record -> record.setTranslations(translationsByEntry.getOrDefault(record.getId(), Map.of())));
        PageResponse<LocalizationVO.EntryVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePage);
        response.setPageSize(safePageSize);
        return response;
    }

    @Transactional
    public LocalizationVO.LanguageVO saveLanguage(Long id, LocalizationDTO.LanguageUpsertRequest request) {
        String localeCode = normalizeLocale(request.getLocaleCode());
        boolean isDefault = Boolean.TRUE.equals(request.getDefaultLanguage());
        if (isDefault) {
            jdbcTemplate.update("update sys_localization_language set is_default = 0, updated_by = 0, updated_at = ? where deleted = 0", LocalDateTime.now());
        }

        Long existingId = id == null ? queryLanguageId(localeCode).orElse(null) : id;
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_localization_language (
                              locale_code, language_name, native_name, fallback_locale, sort_no, is_default, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, 0, 0, 0)
                            """,
                    localeCode,
                    request.getLanguageName().trim(),
                    normalizeText(request.getNativeName()),
                    normalizeLocaleOrNull(request.getFallbackLocale()),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    isDefault ? 1 : 0,
                    normalizeStatus(request.getStatus())
            );
        } else {
            jdbcTemplate.update(
                    """
                            update sys_localization_language
                            set locale_code = ?, language_name = ?, native_name = ?, fallback_locale = ?, sort_no = ?, is_default = ?, status = ?, updated_by = 0, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    localeCode,
                    request.getLanguageName().trim(),
                    normalizeText(request.getNativeName()),
                    normalizeLocaleOrNull(request.getFallbackLocale()),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    isDefault ? 1 : 0,
                    normalizeStatus(request.getStatus()),
                    LocalDateTime.now(),
                    existingId
            );
        }
        return getLanguage(localeCode);
    }

    @Transactional
    public void deleteLanguage(Long id) {
        jdbcTemplate.update("update sys_localization_language set deleted = 1, updated_by = 0, updated_at = ? where id = ?", LocalDateTime.now(), id);
    }

    @Transactional
    public LocalizationVO.NamespaceVO saveNamespace(Long id, LocalizationDTO.NamespaceUpsertRequest request) {
        String namespaceCode = request.getNamespaceCode().trim();
        Long existingId = id == null ? queryNamespaceId(namespaceCode).orElse(null) : id;
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_localization_namespace (
                              namespace_code, namespace_name, source_type, source_ref, sort_no, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, 0, 0, 0)
                            """,
                    namespaceCode,
                    request.getNamespaceName().trim(),
                    normalizeSourceType(request.getSourceType()),
                    normalizeText(request.getSourceRef()),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus())
            );
        } else {
            jdbcTemplate.update(
                    """
                            update sys_localization_namespace
                            set namespace_code = ?, namespace_name = ?, source_type = ?, source_ref = ?, sort_no = ?, status = ?, updated_by = 0, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    namespaceCode,
                    request.getNamespaceName().trim(),
                    normalizeSourceType(request.getSourceType()),
                    normalizeText(request.getSourceRef()),
                    request.getSortNo() == null ? 0 : request.getSortNo(),
                    normalizeStatus(request.getStatus()),
                    LocalDateTime.now(),
                    existingId
            );
        }
        return getNamespace(namespaceCode);
    }

    @Transactional
    public void deleteNamespace(Long id) {
        jdbcTemplate.update("update sys_localization_namespace set deleted = 1, updated_by = 0, updated_at = ? where id = ?", LocalDateTime.now(), id);
    }

    @Transactional
    public LocalizationVO.EntryVO saveEntry(LocalizationDTO.EntryUpsertRequest request) {
        return saveEntryInternal(request);
    }

    @Transactional
    public void deleteEntry(Long id) {
        jdbcTemplate.update("update sys_localization_entry set deleted = 1, updated_by = 0, updated_at = ? where id = ?", LocalDateTime.now(), id);
        jdbcTemplate.update("update sys_localization_translation set deleted = 1, updated_by = 0, updated_at = ? where entry_id = ?", LocalDateTime.now(), id);
        jdbcTemplate.update("update sys_localization_usage_ref set deleted = 1, updated_by = 0, updated_at = ? where entry_id = ?", LocalDateTime.now(), id);
    }

    @Transactional
    public LocalizationVO.SyncResultVO sync(LocalizationDTO.SyncRequest request) {
        LocalizationVO.SyncResultVO result = new LocalizationVO.SyncResultVO();
        Map<String, String> localesEncountered = new LinkedHashMap<>();
        localesEncountered.put(normalizeLocale(request.getSourceLocale()), request.getSourceLocale());
        for (LocalizationDTO.EntryUpsertRequest item : request.getItems()) {
            saveEntryInternal(item);
            if (StringUtils.hasText(item.getSourceLocale())) {
                localesEncountered.put(normalizeLocale(item.getSourceLocale()), item.getSourceLocale());
            }
            item.getTranslations().keySet().forEach(locale -> localesEncountered.put(normalizeLocale(locale), locale));
        }

        result.setLanguageCount(localesEncountered.size());
        result.setNamespaceCount((int) countNamespaces());
        result.setEntryCount((int) countEntries());
        result.setTranslationCount((int) countTranslations());
        result.setUsageCount((int) countUsageRefs());
        return result;
    }

    public List<LocalizationVO.ReleaseVO> listReleases(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        return jdbcTemplate.query(
                """
                        select
                          id,
                          locale_code as localeCode,
                          release_version as releaseVersion,
                          fallback_locale as fallbackLocale,
                          note,
                          active_flag as active,
                          published_by as publishedBy,
                          published_at as publishedAt
                        from sys_localization_release
                        where deleted = 0 and locale_code = ?
                        order by release_version desc, id desc
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.ReleaseVO.class),
                targetLocale
        );
    }

    @Transactional
    public LocalizationVO.ReleaseVO publish(LocalizationDTO.PublishRequest request, CurrentUser currentUser) {
        String localeCode = normalizeLocale(request.getLocaleCode());
        LocalizationVO.RuntimeBundleVO bundle = buildRuntimeBundle(localeCode);
        String fallbackLocale = resolveFallbackLocale(localeCode);
        long nextVersion = nextReleaseVersion(localeCode);
        bundle.setReleaseVersion(nextVersion);

        try {
            jdbcTemplate.update("update sys_localization_release set active_flag = 0, updated_by = ?, updated_at = ? where deleted = 0 and locale_code = ?", currentUser.getUserId(), LocalDateTime.now(), localeCode);
            String bundleJson = objectMapper.writeValueAsString(bundle);
            jdbcTemplate.update(
                    """
                            insert into sys_localization_release (
                              locale_code, release_version, fallback_locale, bundle_json, note, active_flag, published_by, published_at, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 0)
                            """,
                    localeCode,
                    nextVersion,
                    fallbackLocale,
                    bundleJson,
                    StringUtils.hasText(request.getNote()) ? request.getNote().trim() : DEFAULT_RELEASE_NOTE,
                    currentUser.getUserId(),
                    LocalDateTime.now(),
                    currentUser.getUserId(),
                    LocalDateTime.now()
            );
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
        jdbcTemplate.update("update sys_localization_release set active_flag = 0, updated_by = ?, updated_at = ? where deleted = 0 and locale_code = ?", currentUser.getUserId(), LocalDateTime.now(), release.getLocaleCode());
        jdbcTemplate.update("update sys_localization_release set active_flag = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0", currentUser.getUserId(), LocalDateTime.now(), request.getReleaseId());
        return getRelease(request.getReleaseId());
    }

    public LocalizationVO.RuntimeBundleVO runtimeBundle(String localeCode) {
        return buildRuntimeBundle(normalizeLocale(localeCode));
    }

    private LocalizationVO.EntryVO saveEntryInternal(LocalizationDTO.EntryUpsertRequest request) {
        String namespaceCode = request.getNamespaceCode().trim();
        LocalizationVO.NamespaceVO namespace = saveNamespace(null, buildNamespaceRequest(request));
        Long namespaceId = queryNamespaceId(namespace.getNamespaceCode()).orElseThrow();
        String key = request.getMessageKey().trim();
        Long entryId = request.getId() != null ? request.getId() : queryEntryId(namespaceId, key).orElse(null);
        if (entryId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_localization_entry (
                              namespace_id, message_key, default_message, source_locale, source_type, source_ref, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, 0, 0, 0)
                            """,
                    namespaceId,
                    key,
                    request.getDefaultMessage().trim(),
                    normalizeLocale(request.getSourceLocale()),
                    normalizeSourceType(request.getSourceType()),
                    normalizeText(request.getSourceRef()),
                    normalizeStatus(request.getStatus())
            );
            entryId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            jdbcTemplate.update(
                    """
                            update sys_localization_entry
                            set namespace_id = ?, message_key = ?, default_message = ?, source_locale = ?, source_type = ?, source_ref = ?, status = ?, updated_by = 0, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    namespaceId,
                    key,
                    request.getDefaultMessage().trim(),
                    normalizeLocale(request.getSourceLocale()),
                    normalizeSourceType(request.getSourceType()),
                    normalizeText(request.getSourceRef()),
                    normalizeStatus(request.getStatus()),
                    LocalDateTime.now(),
                    entryId
            );
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
        if (!StringUtils.hasText(value)) {
            return;
        }

        Long translationId = queryTranslationId(entryId, normalizedLocale).orElse(null);
        if (translationId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_localization_translation (
                              entry_id, locale_code, translated_message, translation_status, machine_generated, review_status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 0, 'PENDING', 0, 0, 0)
                            """,
                    entryId,
                    normalizedLocale,
                    value,
                    DEFAULT_TRANSLATION_STATUS
            );
        } else {
            jdbcTemplate.update(
                    """
                            update sys_localization_translation
                            set translated_message = ?, translation_status = ?, updated_by = 0, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    value,
                    DEFAULT_TRANSLATION_STATUS,
                    LocalDateTime.now(),
                    translationId
            );
        }
    }

    private void upsertUsageRef(Long entryId, String sourceType, String sourceRef, String sourceText) {
        if (!StringUtils.hasText(sourceRef)) {
            return;
        }
        Long usageId = queryUsageRefId(entryId, normalizeSourceType(sourceType), sourceRef.trim(), null).orElse(null);
        if (usageId == null) {
            jdbcTemplate.update(
                    """
                            insert into sys_localization_usage_ref (
                              entry_id, source_type, source_ref, source_line, source_text, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, 0, 0, 0)
                            """,
                    entryId,
                    normalizeSourceType(sourceType),
                    sourceRef.trim(),
                    null,
                    sourceText
            );
            return;
        }

        jdbcTemplate.update(
                """
                        update sys_localization_usage_ref
                        set source_text = ?, updated_by = 0, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                sourceText,
                LocalDateTime.now(),
                usageId
        );
    }

    private LocalizationVO.RuntimeBundleVO buildRuntimeBundle(String localeCode) {
        String targetLocale = normalizeLocale(localeCode);
        String fallbackLocale = resolveFallbackLocale(targetLocale);
        LocalizationVO.RuntimeBundleVO bundle = new LocalizationVO.RuntimeBundleVO();
        bundle.setLocaleCode(targetLocale);
        bundle.setFallbackLocale(fallbackLocale);

        LocalizationVO.ReleaseVO release = getActiveRelease(targetLocale);
        if (release != null) {
            bundle.setReleaseVersion(release.getReleaseVersion());
            try {
                LocalizationVO.RuntimeBundleVO storedBundle = objectMapper.readValue(
                        jdbcTemplate.queryForObject(
                                "select bundle_json from sys_localization_release where id = ? and deleted = 0",
                                String.class,
                                release.getId()
                        ),
                        LocalizationVO.RuntimeBundleVO.class
                );
                if (storedBundle != null && storedBundle.getMessages() != null && !storedBundle.getMessages().isEmpty()) {
                    return storedBundle;
                }
            } catch (Exception ignored) {
                // Fallback to live data below when bundle json cannot be parsed.
            }
        }

        bundle.setReleaseVersion(0L);
        bundle.setMessages(loadRuntimeMessages(targetLocale, fallbackLocale));
        return bundle;
    }

    private Map<String, String> loadRuntimeMessages(String localeCode, String fallbackLocale) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select
                          e.message_key as messageKey,
                          e.default_message as defaultMessage,
                          t_target.translated_message as targetMessage,
                          t_fallback.translated_message as fallbackMessage
                        from sys_localization_entry e
                        join sys_localization_namespace n on n.id = e.namespace_id and n.deleted = 0
                        left join sys_localization_translation t_target on t_target.entry_id = e.id and t_target.locale_code = ? and t_target.deleted = 0
                        left join sys_localization_translation t_fallback on t_fallback.entry_id = e.id and t_fallback.locale_code = ? and t_fallback.deleted = 0
                        where e.deleted = 0 and e.status = 'ENABLED'
                        order by n.sort_no asc, e.id asc
                        """,
                localeCode,
                fallbackLocale
        );
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = stringValue(row.get("messageKey"));
            String defaultMessage = stringValue(row.get("defaultMessage"));
            String targetMessage = stringValue(row.get("targetMessage"));
            String fallbackMessage = stringValue(row.get("fallbackMessage"));
            String resolved = StringUtils.hasText(targetMessage)
                    ? targetMessage
                    : (StringUtils.hasText(fallbackMessage) ? fallbackMessage : (StringUtils.hasText(defaultMessage) ? defaultMessage : key));
            messages.put(key, resolved);
        }
        return messages;
    }

    private void enrichLanguageMetrics(LocalizationVO.LanguageVO language, long totalEntries) {
        long translatedCount = countTranslations(language.getLocaleCode());
        language.setEntryCount(totalEntries);
        language.setTranslatedCount(translatedCount);
        language.setCoverageRate(totalEntries == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(translatedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalEntries), 2, RoundingMode.HALF_UP));
        language.setPublishedVersion(getActiveReleaseVersion(language.getLocaleCode()).orElse(0L));
        language.setLastPublishedAt(getLastPublishedAt(language.getLocaleCode()).orElse(null));
    }

    private void enrichNamespaceMetrics(LocalizationVO.NamespaceVO namespace, String localeCode) {
        long entryCount = countEntriesByNamespace(namespace.getNamespaceCode());
        long translatedCount = countTranslationsByNamespace(namespace.getNamespaceCode(), localeCode);
        namespace.setEntryCount(entryCount);
        namespace.setTranslatedCount(translatedCount);
        namespace.setCoverageRate(entryCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(translatedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(entryCount), 2, RoundingMode.HALF_UP));
    }

    private Optional<Long> queryLanguageId(String localeCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id from sys_localization_language where locale_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    localeCode
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private Optional<Long> queryNamespaceId(String namespaceCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id from sys_localization_namespace where namespace_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    namespaceCode
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private Optional<Long> queryEntryId(Long namespaceId, String messageKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id from sys_localization_entry where namespace_id = ? and message_key = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    namespaceId,
                    messageKey
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private Optional<Long> queryTranslationId(Long entryId, String localeCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id from sys_localization_translation where entry_id = ? and locale_code = ? and deleted = 0 order by id desc limit 1",
                    Long.class,
                    entryId,
                    localeCode
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private Optional<Long> queryUsageRefId(Long entryId, String sourceType, String sourceRef, Integer sourceLine) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                            select id from sys_localization_usage_ref
                            where entry_id = ? and source_type = ? and source_ref = ? and ((source_line is null and ? is null) or source_line = ?)
                              and deleted = 0
                            order by id desc limit 1
                            """,
                    Long.class,
                    entryId,
                    sourceType,
                    sourceRef,
                    sourceLine,
                    sourceLine
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private LocalizationVO.LanguageVO getLanguage(String localeCode) {
        return jdbcTemplate.queryForObject(
                """
                        select
                          id,
                          locale_code as localeCode,
                          language_name as languageName,
                          native_name as nativeName,
                          fallback_locale as fallbackLocale,
                          sort_no as sortNo,
                          status,
                          is_default as defaultLanguage
                        from sys_localization_language
                        where locale_code = ? and deleted = 0
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.LanguageVO.class),
                localeCode
        );
    }

    private LocalizationVO.NamespaceVO getNamespace(String namespaceCode) {
        return jdbcTemplate.queryForObject(
                """
                        select
                          id,
                          namespace_code as namespaceCode,
                          namespace_name as namespaceName,
                          source_type as sourceType,
                          source_ref as sourceRef,
                          sort_no as sortNo,
                          status
                        from sys_localization_namespace
                        where namespace_code = ? and deleted = 0
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.NamespaceVO.class),
                namespaceCode
        );
    }

    private LocalizationVO.EntryVO getEntry(Long entryId, String localeCode) {
        String fallbackLocale = resolveFallbackLocale(localeCode);
        return jdbcTemplate.queryForObject(
                """
                        select
                          e.id,
                          n.namespace_code as namespaceCode,
                          n.namespace_name as namespaceName,
                          e.message_key as messageKey,
                          e.default_message as defaultMessage,
                          e.source_locale as sourceLocale,
                          e.source_type as sourceType,
                          e.source_ref as sourceRef,
                          e.status,
                          case
                            when coalesce(t_target.translated_message, t_fallback.translated_message, '') <> '' then 'TRANSLATED'
                            else 'PENDING'
                          end as translationStatus,
                          coalesce(t_target.translated_message, t_fallback.translated_message, '') as currentTranslation,
                          coalesce(u.usageCount, 0) as usageCount,
                          e.created_at as createdAt,
                          e.updated_at as updatedAt
                        from sys_localization_entry e
                        join sys_localization_namespace n on n.id = e.namespace_id and n.deleted = 0
                        left join sys_localization_translation t_target on t_target.entry_id = e.id and t_target.locale_code = ? and t_target.deleted = 0
                        left join sys_localization_translation t_fallback on t_fallback.entry_id = e.id and t_fallback.locale_code = ? and t_fallback.deleted = 0
                        left join (
                            select entry_id, count(1) as usageCount
                            from sys_localization_usage_ref
                            where deleted = 0
                            group by entry_id
                        ) u on u.entry_id = e.id
                        where e.id = ? and e.deleted = 0
                        """,
                new BeanPropertyRowMapper<>(LocalizationVO.EntryVO.class),
                localeCode,
                fallbackLocale,
                entryId
        );
    }

    private LocalizationVO.ReleaseVO getRelease(Long releaseId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select
                              id,
                              locale_code as localeCode,
                              release_version as releaseVersion,
                              fallback_locale as fallbackLocale,
                              note,
                              active_flag as active,
                              published_by as publishedBy,
                              published_at as publishedAt
                            from sys_localization_release
                            where id = ? and deleted = 0
                            """,
                    new BeanPropertyRowMapper<>(LocalizationVO.ReleaseVO.class),
                    releaseId
            );
        } catch (EmptyResultDataAccessException error) {
            return null;
        }
    }

    private LocalizationVO.ReleaseVO getActiveRelease(String localeCode) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select
                              id,
                              locale_code as localeCode,
                              release_version as releaseVersion,
                              fallback_locale as fallbackLocale,
                              note,
                              active_flag as active,
                              published_by as publishedBy,
                              published_at as publishedAt
                            from sys_localization_release
                            where locale_code = ? and active_flag = 1 and deleted = 0
                            order by release_version desc, id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(LocalizationVO.ReleaseVO.class),
                    localeCode
            );
        } catch (EmptyResultDataAccessException error) {
            return null;
        }
    }

    private Optional<Long> getActiveReleaseVersion(String localeCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select release_version from sys_localization_release where locale_code = ? and active_flag = 1 and deleted = 0 order by release_version desc, id desc limit 1",
                    Long.class,
                    localeCode
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> getLastPublishedAt(String localeCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select published_at from sys_localization_release where locale_code = ? and deleted = 0 order by published_at desc, id desc limit 1",
                    LocalDateTime.class,
                    localeCode
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private long nextReleaseVersion(String localeCode) {
        Long current = jdbcTemplate.queryForObject(
                "select coalesce(max(release_version), 0) from sys_localization_release where locale_code = ? and deleted = 0",
                Long.class,
                localeCode
        );
        return (current == null ? 0L : current) + 1L;
    }

    private long countEntries() {
        Long count = jdbcTemplate.queryForObject("select count(1) from sys_localization_entry where deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    private long countNamespaces() {
        Long count = jdbcTemplate.queryForObject("select count(1) from sys_localization_namespace where deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    private long countTranslations() {
        Long count = jdbcTemplate.queryForObject("select count(1) from sys_localization_translation where deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    private long countUsageRefs() {
        Long count = jdbcTemplate.queryForObject("select count(1) from sys_localization_usage_ref where deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    private long countTranslations(String localeCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_localization_entry e
                        left join sys_localization_translation t on t.entry_id = e.id and t.locale_code = ? and t.deleted = 0
                        where e.deleted = 0 and coalesce(t.translated_message, '') <> ''
                        """,
                Long.class,
                localeCode
        );
        return count == null ? 0L : count;
    }

    private long countEntriesByNamespace(String namespaceCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_localization_entry e
                        join sys_localization_namespace n on n.id = e.namespace_id and n.deleted = 0
                        where e.deleted = 0 and n.namespace_code = ?
                        """,
                Long.class,
                namespaceCode
        );
        return count == null ? 0L : count;
    }

    private long countTranslationsByNamespace(String namespaceCode, String localeCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_localization_entry e
                        join sys_localization_namespace n on n.id = e.namespace_id and n.deleted = 0
                        left join sys_localization_translation t on t.entry_id = e.id and t.locale_code = ? and t.deleted = 0
                        where e.deleted = 0 and n.namespace_code = ? and coalesce(t.translated_message, '') <> ''
                        """,
                Long.class,
                localeCode,
                namespaceCode
        );
        return count == null ? 0L : count;
    }

    private Map<String, String> loadTranslationMap(Long entryId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select locale_code as localeCode, translated_message as translatedMessage
                        from sys_localization_translation
                        where entry_id = ? and deleted = 0
                        order by locale_code asc
                        """,
                entryId
        );
        Map<String, String> translations = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String locale = stringValue(row.get("localeCode"));
            String translation = stringValue(row.get("translatedMessage"));
            if (StringUtils.hasText(locale) && StringUtils.hasText(translation)) {
                translations.put(locale, translation);
            }
        }
        return translations;
    }

    private Map<Long, Map<String, String>> loadTranslationMaps(List<Long> entryIds) {
        List<Long> distinctEntryIds = entryIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctEntryIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(distinctEntryIds.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select entry_id as entryId, locale_code as localeCode, translated_message as translatedMessage
                        from sys_localization_translation
                        where entry_id in (%s) and deleted = 0
                        order by entry_id asc, locale_code asc
                        """.formatted(placeholders),
                distinctEntryIds.toArray()
        );
        Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long entryId = longValue(row.get("entryId"));
            if (entryId == null) {
                continue;
            }
            String locale = stringValue(row.get("localeCode"));
            String translation = stringValue(row.get("translatedMessage"));
            if (StringUtils.hasText(locale) && StringUtils.hasText(translation)) {
                result.computeIfAbsent(entryId, ignored -> new LinkedHashMap<>()).put(locale, translation);
            }
        }
        return result;
    }

    private String resolveFallbackLocale(String localeCode) {
        if (!StringUtils.hasText(localeCode)) {
            return DEFAULT_LOCALE;
        }
        String normalized = normalizeLocale(localeCode);
        String fallback = jdbcTemplate.query(
                """
                        select fallback_locale
                        from sys_localization_language
                        where locale_code = ? and deleted = 0
                        order by is_default desc, sort_no asc, id asc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                normalized
        );
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
