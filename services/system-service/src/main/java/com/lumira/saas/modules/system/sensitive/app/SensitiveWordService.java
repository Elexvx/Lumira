package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.app.AiKnowledgeTextExtractor;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SensitiveWordService {

    private static final int MAX_PAGE_SIZE = 200L > Integer.MAX_VALUE ? Integer.MAX_VALUE : 200;
    private static final int MAX_WORD_LENGTH = 128;
    private static final int MAX_MATCHES = 20;
    private static final int MAX_PAYLOAD_INSPECTION_DEPTH = 8;
    private static final Pattern IMPORT_SPLITTER = Pattern.compile("[\\r\\n,，;；、]+");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MyBatisQueryOperations jdbcTemplate;
    private final AiKnowledgeTextExtractor textExtractor;
    private final SensitiveWordPluginStateService pluginStateService;

    public SensitiveWordService(
            MyBatisQueryOperations jdbcTemplate,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.textExtractor = textExtractor;
        this.pluginStateService = pluginStateService;
    }

    public PageResponse<SensitiveWordVO.WordRecord> listWords(CurrentUser currentUser, String keyword, Boolean enabled, long pageNo, long pageSize) {
        pluginStateService.ensureEnabled(currentUser);
        Long tenantId = tenantId(currentUser);
        StringBuilder baseSql = new StringBuilder("""
                from sys_sensitive_word
                where tenant_id = ?
                  and deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(keyword)) {
            baseSql.append(" and (word like ? or category like ? or severity like ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (enabled != null) {
            baseSql.append(" and enabled = ?");
            params.add(Boolean.TRUE.equals(enabled) ? 1 : 0);
        }
        String selectSql = """
                select id,
                       tenant_id as tenantId,
                       word,
                       normalized_word as normalizedWord,
                       category,
                       severity,
                       enabled,
                       created_by as createdBy,
                       created_at as createdAt,
                       updated_by as updatedBy,
                       updated_at as updatedAt
                """ + baseSql + " order by updated_at desc, id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SensitiveWordVO.WordRecord.class, pageNo, pageSize, params);
    }

    public SensitiveWordVO.WordRecord getWord(CurrentUser currentUser, Long id) {
        pluginStateService.ensureEnabled(currentUser);
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词 ID 不能为空");
        }
        SensitiveWordVO.WordRecord record = jdbcTemplate.queryForObject("""
                select id,
                       tenant_id as tenantId,
                       word,
                       normalized_word as normalizedWord,
                       category,
                       severity,
                       enabled,
                       created_by as createdBy,
                       created_at as createdAt,
                       updated_by as updatedBy,
                       updated_at as updatedAt
                from sys_sensitive_word
                where id = ?
                  and tenant_id = ?
                  and deleted = 0
                """, new BeanPropertyRowMapper<>(SensitiveWordVO.WordRecord.class), id, tenantId(currentUser));
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
        return record;
    }

    public SensitiveWordVO.WordRecord createWord(CurrentUser currentUser, SensitiveWordDTO.UpsertRequest request) {
        pluginStateService.ensureEnabled(currentUser);
        NormalizedWord normalized = validateAndNormalize(request);
        ensureUniqueWord(tenantId(currentUser), normalized.normalizedWord(), null);
        jdbcTemplate.update("""
                insert into sys_sensitive_word (
                    tenant_id, word, normalized_word, category, severity, enabled,
                    created_by, created_at, updated_by, updated_at, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, now(), ?, now(), 0)
                """,
                tenantId(currentUser),
                normalized.word(),
                normalized.normalizedWord(),
                normalized.category(),
                normalized.severity(),
                normalized.enabled() ? 1 : 0,
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getWord(currentUser, id);
    }

    public SensitiveWordVO.WordRecord updateWord(CurrentUser currentUser, Long id, SensitiveWordDTO.UpsertRequest request) {
        pluginStateService.ensureEnabled(currentUser);
        getWord(currentUser, id);
        NormalizedWord normalized = validateAndNormalize(request);
        ensureUniqueWord(tenantId(currentUser), normalized.normalizedWord(), id);
        jdbcTemplate.update("""
                update sys_sensitive_word
                   set word = ?,
                       normalized_word = ?,
                       category = ?,
                       severity = ?,
                       enabled = ?,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                   and tenant_id = ?
                   and deleted = 0
                """,
                normalized.word(),
                normalized.normalizedWord(),
                normalized.category(),
                normalized.severity(),
                normalized.enabled() ? 1 : 0,
                currentUser.getUserId(),
                id,
                tenantId(currentUser)
        );
        return getWord(currentUser, id);
    }

    public boolean updateStatus(CurrentUser currentUser, Long id, Boolean enabled) {
        pluginStateService.ensureEnabled(currentUser);
        if (enabled == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "启用状态不能为空");
        }
        getWord(currentUser, id);
        jdbcTemplate.update("""
                update sys_sensitive_word
                   set enabled = ?,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                   and tenant_id = ?
                   and deleted = 0
                """,
                enabled ? 1 : 0,
                currentUser.getUserId(),
                id,
                tenantId(currentUser)
        );
        return true;
    }

    public boolean deleteWord(CurrentUser currentUser, Long id) {
        pluginStateService.ensureEnabled(currentUser);
        getWord(currentUser, id);
        jdbcTemplate.update("""
                update sys_sensitive_word
                   set deleted = 1,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                   and tenant_id = ?
                   and deleted = 0
                """,
                currentUser.getUserId(),
                id,
                tenantId(currentUser)
        );
        return true;
    }

    public SensitiveWordVO.ImportResult importWords(CurrentUser currentUser, MultipartFile file) {
        pluginStateService.ensureEnabled(currentUser);
        String text = extractImportText(file);
        String[] fragments = IMPORT_SPLITTER.split(text);
        SensitiveWordVO.ImportResult result = new SensitiveWordVO.ImportResult();
        result.setTotal(fragments.length);
        Set<String> seenInBatch = new LinkedHashSet<>();
        for (String fragment : fragments) {
            String candidate = normalizeWord(fragment);
            if (!StringUtils.hasText(candidate) || candidate.length() > MAX_WORD_LENGTH) {
                result.setInvalid(result.getInvalid() + 1);
                continue;
            }
            String normalized = normalizeForMatch(candidate);
            if (!seenInBatch.add(normalized)) {
                result.setDuplicated(result.getDuplicated() + 1);
                continue;
            }
            if (jdbcTemplate.exists("""
                    select 1
                    from sys_sensitive_word
                    where tenant_id = ?
                      and normalized_word = ?
                      and deleted = 0
                    limit 1
                    """, tenantId(currentUser), normalized)) {
                result.setDuplicated(result.getDuplicated() + 1);
                continue;
            }
            jdbcTemplate.update("""
                    insert into sys_sensitive_word (
                        tenant_id, word, normalized_word, category, severity, enabled,
                        created_by, created_at, updated_by, updated_at, deleted
                    ) values (?, ?, ?, ?, ?, 1, ?, now(), ?, now(), 0)
                    """,
                    tenantId(currentUser),
                    candidate,
                    normalized,
                    "IMPORTED",
                    "MEDIUM",
                    currentUser.getUserId(),
                    currentUser.getUserId()
            );
            result.setImported(result.getImported() + 1);
        }
        return result;
    }

    public SensitiveWordVO.CheckResult checkText(CurrentUser currentUser, String text, String fieldPath) {
        pluginStateService.ensureEnabled(currentUser);
        return buildCheckResult(text, fieldPath, listEnabledWords(tenantId(currentUser)));
    }

    public SensitiveWordVO.CheckResult checkPayload(CurrentUser currentUser, Object payload) {
        if (!pluginStateService.isEnabled(currentUser)) {
            return new SensitiveWordVO.CheckResult(false, List.of());
        }
        List<SensitiveWordRecord> words = listEnabledWords(tenantId(currentUser));
        List<SensitiveWordVO.MatchItem> matches = new ArrayList<>();
        inspectValue(payload, "", matches, words, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        if (matches.size() > MAX_MATCHES) {
            matches = new ArrayList<>(matches.subList(0, MAX_MATCHES));
        }
        return new SensitiveWordVO.CheckResult(!matches.isEmpty(), matches);
    }

    public boolean shouldBypassField(String fieldPath) {
        if (!StringUtils.hasText(fieldPath)) {
            return false;
        }
        String normalized = fieldPath.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("captcha")
                || normalized.contains("verifycode")
                || normalized.contains("verificationcode")
                || normalized.contains("apikey")
                || normalized.contains("privatekey")
                || normalized.contains("publickey")
                || normalized.contains("accesskey");
    }

    public String formatMatchesForUser(List<SensitiveWordVO.MatchItem> matches) {
        if (matches == null || matches.isEmpty()) {
            return "内容包含敏感词，请修改后再提交";
        }
        return "内容包含敏感词，请修改后再提交：" + matches.stream()
                .map(item -> StringUtils.hasText(item.getFieldPath()) ? item.getFieldPath() + " " + item.getMaskedWord() : item.getMaskedWord())
                .distinct()
                .limit(5)
                .reduce((left, right) -> left + "、" + right)
                .orElse("内容包含敏感词，请修改后再提交");
    }

    private String extractImportText(MultipartFile file) {
        String filename = file == null ? "" : file.getOriginalFilename();
        String extension = filename == null || !filename.contains(".")
                ? ""
                : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ("txt".equals(extension) || "md".equals(extension)) {
            try {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new BizException(ErrorCode.BAD_REQUEST, "读取敏感词文件失败");
            }
        }
        return textExtractor.extract(file).text();
    }

    private List<SensitiveWordRecord> listEnabledWords(Long tenantId) {
        return jdbcTemplate.query("""
                select word, normalized_word as normalizedWord
                from sys_sensitive_word
                where tenant_id = ?
                  and enabled = 1
                  and deleted = 0
                order by length(normalized_word) desc, id asc
                """, new BeanPropertyRowMapper<>(SensitiveWordRecord.class), tenantId);
    }

    private SensitiveWordVO.CheckResult buildCheckResult(String text, String fieldPath, List<SensitiveWordRecord> words) {
        if (!StringUtils.hasText(text) || words == null || words.isEmpty() || shouldBypassField(fieldPath)) {
            return new SensitiveWordVO.CheckResult(false, List.of());
        }
        String normalizedText = normalizeForMatch(text);
        if (!StringUtils.hasText(normalizedText)) {
            return new SensitiveWordVO.CheckResult(false, List.of());
        }
        List<SensitiveWordVO.MatchItem> matches = new ArrayList<>();
        for (SensitiveWordRecord record : words) {
            if (normalizedText.contains(record.getNormalizedWord())) {
                matches.add(new SensitiveWordVO.MatchItem(fieldPath, record.getWord(), maskWord(record.getWord())));
                if (matches.size() >= MAX_MATCHES) {
                    break;
                }
            }
        }
        return new SensitiveWordVO.CheckResult(!matches.isEmpty(), matches);
    }

    private void inspectValue(
            Object value,
            String fieldPath,
            List<SensitiveWordVO.MatchItem> matches,
            List<SensitiveWordRecord> words,
            Set<Object> visited,
            int depth
    ) {
        if (value == null || matches.size() >= MAX_MATCHES || depth > MAX_PAYLOAD_INSPECTION_DEPTH) {
            return;
        }
        if (value instanceof String text) {
            SensitiveWordVO.CheckResult result = buildCheckResult(text, fieldPath, words);
            if (result.isHit()) {
                matches.addAll(result.getMatches());
            }
            return;
        }
        if (isScalarValue(value)) {
            return;
        }
        if (!visited.add(value)) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childPath = joinPath(fieldPath, String.valueOf(entry.getKey()));
                inspectValue(entry.getValue(), childPath, matches, words, visited, depth + 1);
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                inspectValue(item, fieldPath + "[" + index + "]", matches, words, visited, depth + 1);
                index += 1;
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i += 1) {
                inspectValue(java.lang.reflect.Array.get(value, i), fieldPath + "[" + i + "]", matches, words, visited, depth + 1);
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
            }
            return;
        }
        if (shouldSkipReflectiveInspection(value.getClass())) {
            return;
        }
        for (java.lang.reflect.Field field : value.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                inspectValue(field.get(value), joinPath(fieldPath, field.getName()), matches, words, visited, depth + 1);
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            if (matches.size() >= MAX_MATCHES) {
                return;
            }
        }
    }

    private boolean isScalarValue(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof java.time.temporal.TemporalAccessor;
    }

    private boolean shouldSkipReflectiveInspection(Class<?> type) {
        Package valuePackage = type.getPackage();
        String packageName = valuePackage == null ? "" : valuePackage.getName();
        return type.isPrimitive()
                || packageName.startsWith("java.")
                || packageName.startsWith("javax.")
                || packageName.startsWith("jakarta.")
                || packageName.startsWith("org.springframework.");
    }

    private String joinPath(String base, String key) {
        return StringUtils.hasText(base) ? base + "." + key : key;
    }

    private NormalizedWord validateAndNormalize(SensitiveWordDTO.UpsertRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词请求不能为空");
        }
        String word = normalizeWord(request.getWord());
        if (!StringUtils.hasText(word)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }
        if (word.length() > MAX_WORD_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词长度不能超过 128 个字符");
        }
        return new NormalizedWord(
                word,
                normalizeForMatch(word),
                normalizeNullableText(request.getCategory(), "DEFAULT"),
                normalizeNullableText(request.getSeverity(), "MEDIUM"),
                !Boolean.FALSE.equals(request.getEnabled())
        );
    }

    private void ensureUniqueWord(Long tenantId, String normalizedWord, Long excludeId) {
        String sql = """
                select 1
                from sys_sensitive_word
                where tenant_id = ?
                  and normalized_word = ?
                  and deleted = 0
                """;
        List<Object> params = new ArrayList<>(List.of(tenantId, normalizedWord));
        if (excludeId != null) {
            sql += " and id <> ?";
            params.add(excludeId);
        }
        if (jdbcTemplate.exists(sql + " limit 1", params.toArray())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "敏感词已存在");
        }
    }

    private String normalizeWord(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeForMatch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private Long tenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "当前租户不存在");
        }
        return currentUser.getCurrentTenantId();
    }

    private String maskWord(String word) {
        if (!StringUtils.hasText(word)) {
            return "***";
        }
        if (word.length() <= 2) {
            return "*".repeat(word.length());
        }
        return word.charAt(0) + "*".repeat(Math.max(1, word.length() - 2)) + word.charAt(word.length() - 1);
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<T> records = jdbcTemplate.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        if (records instanceof List<?> list) {
            list.stream()
                    .filter(SensitiveWordVO.WordRecord.class::isInstance)
                    .map(SensitiveWordVO.WordRecord.class::cast)
                    .forEach(this::formatDateFields);
        }
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void formatDateFields(SensitiveWordVO.WordRecord record) {
        record.setCreatedAt(formatDateText(record.getCreatedAt()));
        record.setUpdatedAt(formatDateText(record.getUpdatedAt()));
    }

    private String formatDateText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T")).format(DATE_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private record NormalizedWord(String word, String normalizedWord, String category, String severity, boolean enabled) {
    }

    public static class SensitiveWordRecord {
        private String word;
        private String normalizedWord;

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public String getNormalizedWord() {
            return normalizedWord;
        }

        public void setNormalizedWord(String normalizedWord) {
            this.normalizedWord = normalizedWord;
        }
    }
}
