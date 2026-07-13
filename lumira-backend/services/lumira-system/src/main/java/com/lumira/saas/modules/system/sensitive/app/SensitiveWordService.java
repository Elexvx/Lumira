package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.ai.app.AiKnowledgeTextExtractor;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.sensitive.dto.SensitiveWordDTO;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordManagementRepository;
import com.lumira.saas.modules.system.sensitive.vo.SensitiveWordVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SensitiveWordService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_WORD_LENGTH = 128;
    private static final int MAX_MATCHES = 20;
    private static final int MAX_PAYLOAD_INSPECTION_DEPTH = 8;
    private static final int MAX_PAYLOAD_NODES = 5000;
    private static final int MAX_PAYLOAD_TEXT_CHARS = 200_000;
    private static final int IMPORT_BATCH_SIZE = 500;
    private static final int MAX_IMPORT_FRAGMENTS = 5000;
    private static final long MAX_IMPORT_FILE_BYTES = 1L * 1024L * 1024L;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String PERMISSION_VIEW = "plugin:sensitive-words:view";
    private static final String PERMISSION_MANAGE = "plugin:sensitive-words:manage";
    private static final String PERMISSION_IMPORT = "plugin:sensitive-words:import";
    private static final String ACTION_DICT_CODE = "sys_sensitive_word_action";
    private static final String BLOCKING_ACTION_DICT_CODE = "sys_sensitive_word_blocking_action";
    private static final String DEFAULT_CATEGORY_DICT_CODE = "sys_sensitive_word_default_category";
    private static final String IMPORT_CATEGORY_DICT_CODE = "sys_sensitive_word_import_category";
    private static final String DEFAULT_SEVERITY_DICT_CODE = "sys_sensitive_word_default_severity";
    private static final Pattern IMPORT_SPLITTER = Pattern.compile("[\\r\\n,;，；、]+");
    private static final Pattern FIELD_BYPASS_PATTERN = Pattern.compile("(password|secret|token|captcha|verifycode|verificationcode|apikey|privatekey|publickey|accesskey)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SensitiveWordManagementRepository wordRepository;
    private final AiKnowledgeTextExtractor textExtractor;
    private final SensitiveWordPluginStateService pluginStateService;
    private final SensitiveWordDictionaryCache dictionaryCache;
    private final SensitiveWordMetrics metrics;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final Map<Class<?>, java.lang.reflect.Field[]> reflectiveFieldCache = new ConcurrentHashMap<>();

    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            SensitiveWordDictionaryCache dictionaryCache,
            SensitiveWordMetrics metrics
    ) {
        this(wordRepository, textExtractor, pluginStateService, dictionaryCache, metrics, null, null, null, false);
    }

    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            SensitiveWordDictionaryCache dictionaryCache,
            SensitiveWordMetrics metrics,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(wordRepository, textExtractor, pluginStateService, dictionaryCache, metrics, permissionSnapshotService, null, null, false);
    }

    @Autowired
    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            SensitiveWordDictionaryCache dictionaryCache,
            SensitiveWordMetrics metrics,
            PermissionSnapshotService permissionSnapshotService,
            @Lazy
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                wordRepository,
                textExtractor,
                pluginStateService,
                dictionaryCache,
                metrics,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            SensitiveWordDictionaryCache dictionaryCache,
            SensitiveWordMetrics metrics,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.wordRepository = wordRepository;
        this.textExtractor = textExtractor;
        this.pluginStateService = pluginStateService;
        this.dictionaryCache = dictionaryCache;
        this.metrics = metrics;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            SensitiveWordDictionaryCache dictionaryCache,
            SensitiveWordMetrics metrics,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(wordRepository, textExtractor, pluginStateService, dictionaryCache, metrics, permissionSnapshotService, null, sessionAuthenticationService, false);
    }

    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService
    ) {
        this(wordRepository, textExtractor, pluginStateService, null);
    }

    public SensitiveWordService(
            SensitiveWordManagementRepository wordRepository,
            AiKnowledgeTextExtractor textExtractor,
            SensitiveWordPluginStateService pluginStateService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                wordRepository,
                textExtractor,
                pluginStateService,
                new SensitiveWordDictionaryCache(
                        wordRepository,
                        new SensitiveWordDictionaryVersionService(),
                        new SensitiveWordMetrics(new SimpleMeterRegistry())
                ),
                new SensitiveWordMetrics(new SimpleMeterRegistry()),
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    public PageResponse<SensitiveWordVO.WordRecord> listWords(CurrentUser currentUser, String keyword, Boolean enabled, long pageNo, long pageSize) {
        requirePermission(currentUser, PERMISSION_VIEW);
        pluginStateService.ensureEnabled(currentUser);
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        SensitiveWordManagementRepository.PageData page = wordRepository.search(keyword, enabled,
                (safePageNo - 1) * safePageSize, safePageSize);
        page.records().forEach(this::formatDateFields);
        PageResponse<SensitiveWordVO.WordRecord> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public SensitiveWordVO.WordRecord getWord(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, PERMISSION_VIEW);
        requirePositiveId(id);
        pluginStateService.ensureEnabled(currentUser);
        SensitiveWordVO.WordRecord record = wordRepository.findById(id).orElse(null);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Sensitive word not found");
        }
        formatDateFields(record);
        return record;
    }

    public SensitiveWordVO.WordRecord createWord(CurrentUser currentUser, SensitiveWordDTO.UpsertRequest request) {
        Long operatorId = requirePermission(currentUser, PERMISSION_MANAGE);
        String operatorUuid = requireUserUuid(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        NormalizedWord normalized = validateAndNormalize(request);
        ensureUniqueWord(normalized.normalizedWord(), null);
        Long id = wordRepository.create(toWrite(normalized), operatorId, operatorUuid);
        if (id == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word changed, please retry");
        }
        dictionaryCache.invalidate();
        return getWord(currentUser, id);
    }

    public SensitiveWordVO.WordRecord updateWord(CurrentUser currentUser, Long id, SensitiveWordDTO.UpsertRequest request) {
        Long operatorId = requirePermission(currentUser, PERMISSION_MANAGE);
        String operatorUuid = requireUserUuid(currentUser);
        requirePositiveId(id);
        pluginStateService.ensureEnabled(currentUser);
        SensitiveWordVO.WordRecord currentRecord = getWord(currentUser, id);
        NormalizedWord normalized = validateAndNormalize(request);
        ensureUniqueWord(normalized.normalizedWord(), id);
        int updated = wordRepository.update(id, currentRecord.getNormalizedWord(), toWrite(normalized), operatorId, operatorUuid);
        if (updated == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word changed, please retry");
        }
        dictionaryCache.invalidate();
        return getWord(currentUser, id);
    }

    public boolean updateStatus(CurrentUser currentUser, Long id, Boolean enabled) {
        Long operatorId = requirePermission(currentUser, PERMISSION_MANAGE);
        String operatorUuid = requireUserUuid(currentUser);
        requirePositiveId(id);
        pluginStateService.ensureEnabled(currentUser);
        if (!Boolean.TRUE.equals(enabled)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive words are enabled once added");
        }
        SensitiveWordVO.WordRecord currentRecord = getWord(currentUser, id);
        int updated = wordRepository.enable(id, currentRecord.getNormalizedWord(), operatorId, operatorUuid);
        if (updated == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word changed, please retry");
        }
        dictionaryCache.invalidate();
        return true;
    }

    public boolean deleteWord(CurrentUser currentUser, Long id) {
        Long operatorId = requirePermission(currentUser, PERMISSION_MANAGE);
        String operatorUuid = requireUserUuid(currentUser);
        requirePositiveId(id);
        pluginStateService.ensureEnabled(currentUser);
        SensitiveWordVO.WordRecord currentRecord = getWord(currentUser, id);
        int updated = wordRepository.delete(id, currentRecord.getNormalizedWord(), operatorId, operatorUuid);
        if (updated == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word changed, please retry");
        }
        dictionaryCache.invalidate();
        return true;
    }

    @Transactional
    public SensitiveWordVO.ImportResult importWords(CurrentUser currentUser, MultipartFile file) {
        Long operatorId = requirePermission(currentUser, PERMISSION_IMPORT);
        String operatorUuid = requireUserUuid(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        String[] fragments = IMPORT_SPLITTER.split(extractImportText(file));
        if (fragments.length > MAX_IMPORT_FRAGMENTS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Too many sensitive words to import");
        }
        SensitiveWordVO.ImportResult result = new SensitiveWordVO.ImportResult();
        result.setTotal(fragments.length);
        Map<String, String> normalizedToWord = new LinkedHashMap<>();
        for (String fragment : fragments) {
            String candidate = normalizeWord(fragment);
            if (!StringUtils.hasText(candidate) || candidate.length() > MAX_WORD_LENGTH) {
                result.setInvalid(result.getInvalid() + 1);
                continue;
            }
            String normalized = normalizeForMatch(candidate);
            if (normalizedToWord.putIfAbsent(normalized, candidate) != null) {
                result.setDuplicated(result.getDuplicated() + 1);
            }
        }
        Set<String> existing = loadExistingNormalizedWords(new ArrayList<>(normalizedToWord.keySet()));
        result.setDuplicated(result.getDuplicated() + existing.size());
        List<Map.Entry<String, String>> toInsert = normalizedToWord.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getKey()))
                .toList();
        for (List<Map.Entry<String, String>> batch : partition(toInsert, IMPORT_BATCH_SIZE)) {
            batchInsertImportedWords(operatorId, operatorUuid, batch);
            result.setImported(result.getImported() + batch.size());
        }
        if (!toInsert.isEmpty()) {
            dictionaryCache.invalidate();
        }
        return result;
    }

    public SensitiveWordVO.CheckResult checkText(CurrentUser currentUser, String text, String fieldPath) {
        requirePermission(currentUser, PERMISSION_VIEW);
        pluginStateService.ensureEnabled(currentUser);
        return buildCheckResult(text, fieldPath, dictionaryCache.getMatcher());
    }

    public SensitiveWordVO.CheckResult checkTextForSubmission(CurrentUser currentUser, String text, String fieldPath) {
        pluginStateService.ensureEnabled(currentUser);
        return buildCheckResult(text, fieldPath, dictionaryCache.getMatcher());
    }

    public SensitiveWordVO.CheckResult checkPayload(CurrentUser currentUser, Object payload) {
        requirePermission(currentUser, PERMISSION_VIEW);
        return checkPayloadForSubmission(currentUser, payload);
    }

    public SensitiveWordVO.CheckResult checkPayloadForSubmission(CurrentUser currentUser, Object payload) {
        if (!pluginStateService.isEnabled(currentUser)) {
            return new SensitiveWordVO.CheckResult(false, List.of());
        }
        SensitiveWordMatcher matcher = dictionaryCache.getMatcher();
        List<SensitiveWordVO.MatchItem> matches = new ArrayList<>();
        inspectValue(payload, "", matches, matcher, Collections.newSetFromMap(new IdentityHashMap<>()), 0, new PayloadInspectionBudget());
        if (matches.size() > MAX_MATCHES) {
            matches = new ArrayList<>(matches.subList(0, MAX_MATCHES));
        }
        return new SensitiveWordVO.CheckResult(!matches.isEmpty(), hasBlockingMatch(matches), matches);
    }

    public boolean shouldBypassField(String fieldPath) {
        return StringUtils.hasText(fieldPath) && FIELD_BYPASS_PATTERN.matcher(fieldPath).find();
    }

    public String formatMatchesForUser(List<SensitiveWordVO.MatchItem> matches) {
        if (matches == null || matches.isEmpty()) {
            return "Content contains sensitive words. Please revise and submit again.";
        }
        return "Content contains sensitive words: " + matches.stream()
                .map(item -> StringUtils.hasText(item.getFieldPath()) ? item.getFieldPath() + " " + item.getMaskedWord() : item.getMaskedWord())
                .distinct()
                .limit(5)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Content contains sensitive words. Please revise and submit again.");
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || permissions.isEmpty() || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return currentUser.getUserId();
    }

    private String requireUserUuid(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserUuid().trim();
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
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
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
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
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word id is required");
        }
    }

    private SensitiveWordVO.CheckResult buildCheckResult(String text, String fieldPath, SensitiveWordMatcher matcher) {
        if (!StringUtils.hasText(text) || matcher == null || shouldBypassField(fieldPath)) {
            return new SensitiveWordVO.CheckResult(false, List.of());
        }
        Instant startedAt = Instant.now();
        List<SensitiveWordVO.MatchItem> matches = matcher.find(text, fieldPath, MAX_MATCHES).stream()
                .map(match -> new SensitiveWordVO.MatchItem(match.fieldPath(), match.word(), maskWord(match.word()), normalizeAction(match.action())))
                .toList();
        metrics.recordMatch(Duration.between(startedAt, Instant.now()));
        return new SensitiveWordVO.CheckResult(!matches.isEmpty(), hasBlockingMatch(matches), matches);
    }

    private void inspectValue(Object value, String fieldPath, List<SensitiveWordVO.MatchItem> matches,
                              SensitiveWordMatcher matcher, Set<Object> visited, int depth, PayloadInspectionBudget budget) {
        if (value == null || matches.size() >= MAX_MATCHES || depth > MAX_PAYLOAD_INSPECTION_DEPTH || !budget.consumeNode()) {
            return;
        }
        if (value instanceof String text) {
            if (!budget.consumeText(text.length())) {
                return;
            }
            SensitiveWordVO.CheckResult result = buildCheckResult(text, fieldPath, matcher);
            if (result.isHit()) {
                matches.addAll(result.getMatches());
            }
            return;
        }
        if (isScalarValue(value) || shouldSkipReflectiveInspection(value.getClass()) || !visited.add(value)) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                inspectValue(entry.getValue(), joinPath(fieldPath, String.valueOf(entry.getKey())), matches, matcher, visited, depth + 1, budget);
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                inspectValue(item, fieldPath + "[" + index + "]", matches, matcher, visited, depth + 1, budget);
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
                inspectValue(java.lang.reflect.Array.get(value, i), fieldPath + "[" + i + "]", matches, matcher, visited, depth + 1, budget);
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
            }
            return;
        }
        for (java.lang.reflect.Field field : reflectiveFields(value.getClass())) {
            try {
                inspectValue(field.get(value), joinPath(fieldPath, field.getName()), matches, matcher, visited, depth + 1, budget);
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            if (matches.size() >= MAX_MATCHES) {
                return;
            }
        }
    }

    private Set<String> loadExistingNormalizedWords(List<String> normalizedWords) {
        return wordRepository.findExistingNormalizedWords(normalizedWords);
    }

    private void batchInsertImportedWords(Long userId, String userUuid, List<Map.Entry<String, String>> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<SensitiveWordManagementRepository.ImportedWord> words = batch.stream()
                .map(entry -> new SensitiveWordManagementRepository.ImportedWord(entry.getKey(), entry.getValue()))
                .toList();
        int inserted = wordRepository.insertImported(words,
                requiredDictValue(IMPORT_CATEGORY_DICT_CODE), requiredDictValue(DEFAULT_SEVERITY_DICT_CODE),
                requiredDictValue(BLOCKING_ACTION_DICT_CODE), userId, userUuid);
        if (inserted != batch.size()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word import changed, please retry");
        }
    }

    private String extractImportText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word import file is required");
        }
        if (file.getSize() > MAX_IMPORT_FILE_BYTES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word import file is too large");
        }
        String filename = file == null ? "" : file.getOriginalFilename();
        String extension = filename == null || !filename.contains(".")
                ? ""
                : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ("txt".equals(extension) || "md".equals(extension)) {
            try {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Failed to read sensitive word file");
            }
        }
        return textExtractor.extract(file).text();
    }

    private NormalizedWord validateAndNormalize(SensitiveWordDTO.UpsertRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word request is required");
        }
        String word = normalizeWord(request.getWord());
        if (!StringUtils.hasText(word)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word is required");
        }
        if (word.length() > MAX_WORD_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Sensitive word length must be <= 128");
        }
        return new NormalizedWord(
                word,
                normalizeForMatch(word),
                normalizeNullableText(request.getCategory(), requiredDictValue(DEFAULT_CATEGORY_DICT_CODE)),
                normalizeNullableText(request.getSeverity(), requiredDictValue(DEFAULT_SEVERITY_DICT_CODE)),
                normalizeAction(request.getAction()),
                true
        );
    }

    private boolean hasBlockingMatch(List<SensitiveWordVO.MatchItem> matches) {
        String blockingAction = requiredDictValue(BLOCKING_ACTION_DICT_CODE);
        return matches != null && matches.stream().anyMatch(match -> blockingAction.equals(normalizeAction(match.getAction())));
    }

    private String normalizeAction(String action) {
        List<String> actions = requiredDictValues(ACTION_DICT_CODE);
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return actions.contains(normalized) ? normalized : actions.getFirst();
    }

    private String requiredDictValue(String dictCode) {
        return requiredDictValues(dictCode).getFirst();
    }

    private List<String> requiredDictValues(String dictCode) {
        List<String> values = wordRepository.findEnabledDictValues(dictCode);
        if (values == null || values.isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word dictionary is not configured: " + dictCode);
        }
        return values;
    }

    private void ensureUniqueWord(String normalizedWord, Long excludeId) {
        if (wordRepository.existsByNormalizedWord(normalizedWord, excludeId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Sensitive word already exists");
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
                || packageName.startsWith("org.springframework.")
                || java.io.File.class.isAssignableFrom(type)
                || Path.class.isAssignableFrom(type)
                || java.io.InputStream.class.isAssignableFrom(type)
                || Resource.class.isAssignableFrom(type)
                || MultipartFile.class.isAssignableFrom(type);
    }

    private java.lang.reflect.Field[] reflectiveFields(Class<?> type) {
        return reflectiveFieldCache.computeIfAbsent(type, key -> Arrays.stream(key.getDeclaredFields())
                .peek(field -> field.setAccessible(true))
                .toArray(java.lang.reflect.Field[]::new));
    }

    private String joinPath(String base, String key) {
        return StringUtils.hasText(base) ? base + "." + key : key;
    }

    private String normalizeWord(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeForMatch(String value) {
        return SensitiveWordMatcher.normalizeForMatch(value);
    }

    private String normalizeNullableText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
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

    private <T> List<List<T>> partition(List<T> values, int batchSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += batchSize) {
            result.add(values.subList(start, Math.min(values.size(), start + batchSize)));
        }
        return result;
    }

    private record NormalizedWord(String word, String normalizedWord, String category, String severity, String action, boolean enabled) {
    }

    private SensitiveWordManagementRepository.WordWrite toWrite(NormalizedWord word) {
        return new SensitiveWordManagementRepository.WordWrite(word.word(), word.normalizedWord(), word.category(),
                word.severity(), word.action(), word.enabled());
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

    private static final class PayloadInspectionBudget {
        private int nodes;
        private int textChars;

        private boolean consumeNode() {
            nodes += 1;
            return nodes <= MAX_PAYLOAD_NODES;
        }

        private boolean consumeText(int chars) {
            textChars += Math.max(0, chars);
            return textChars <= MAX_PAYLOAD_TEXT_CHARS;
        }
    }
}
