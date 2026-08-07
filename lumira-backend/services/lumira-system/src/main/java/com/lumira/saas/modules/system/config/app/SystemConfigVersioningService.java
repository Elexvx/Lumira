package com.lumira.saas.modules.system.config.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxService;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Owns the immutable history boundary for all mutable, database-backed platform
 * configuration.  Callers keep their existing domain validation and sys_config
 * writes; this service surrounds those writes with one transaction-local
 * before/after snapshot and a durable outbox notification.
 */
@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemConfigVersioningService {

    public static final String GROUP_VERIFICATION = "VERIFICATION";
    public static final String DOMAIN_PLATFORM = "PLATFORM";
    public static final String CHANGE_UPDATE = "UPDATE";
    public static final String CHANGE_ROLLBACK = "ROLLBACK";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String SENSITIVITY_NONE = "NONE";
    public static final String SENSITIVITY_SECRET = "SECRET";

    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CONFIG_EVENT_TYPE = PlatformEventTypes.SYSTEM_CONFIG_VERSION_PUBLISHED;
    private static final String READ_MODEL_CONTEXT = "platform";
    private static final String READ_MODEL_SCOPE = "configuration";

    private final MyBatisQueryOperations queryOperations;
    private final ObjectMapper objectMapper;
    private final PlatformEventOutboxService outboxService;
    private final ReadModelVersionService readModelVersionService;
    private final FieldCryptoService fieldCryptoService;
    private final Counter publishCounter;
    private final Counter rollbackCounter;
    private final Counter failureCounter;

    @Autowired
    public SystemConfigVersioningService(
            MyBatisQueryOperations queryOperations,
            ObjectMapper objectMapper,
            PlatformEventOutboxService outboxService,
            ReadModelVersionService readModelVersionService,
            FieldCryptoService fieldCryptoService,
            MeterRegistry meterRegistry
    ) {
        this.queryOperations = queryOperations;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.outboxService = outboxService;
        this.readModelVersionService = readModelVersionService;
        this.fieldCryptoService = fieldCryptoService;
        this.publishCounter = meterRegistry == null ? null : Counter.builder("system.config.governance.publish").register(meterRegistry);
        this.rollbackCounter = meterRegistry == null ? null : Counter.builder("system.config.governance.rollback").register(meterRegistry);
        this.failureCounter = meterRegistry == null ? null : Counter.builder("system.config.governance.failure").register(meterRegistry);
    }

    public SystemConfigVersioningService(
            MyBatisQueryOperations queryOperations,
            ObjectMapper objectMapper,
            PlatformEventOutboxService outboxService,
            ReadModelVersionService readModelVersionService
    ) {
        this(queryOperations, objectMapper, outboxService, readModelVersionService, null, null);
    }

    public SystemConfigVersioningService(
            MyBatisQueryOperations queryOperations,
            ObjectMapper objectMapper,
            PlatformEventOutboxService outboxService,
            ReadModelVersionService readModelVersionService,
            MeterRegistry meterRegistry
    ) {
        this(queryOperations, objectMapper, outboxService, readModelVersionService, null, meterRegistry);
    }

    /**
     * Runs an existing sys_config mutation and publishes a version only when the
     * stored configuration actually changed.  The mutation is deliberately a
     * supplier instead of an after-commit callback: the outbox row and read-model
     * version are written before the surrounding transaction commits.
     */
    @Transactional
    public <T> T publish(ChangeRequest request, Collection<String> seedKeys, Supplier<T> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (queryOperations == null) {
            return mutation.get();
        }
        try {
            GovernanceSession session = begin(request, seedKeys);
            T result = mutation.get();
            finish(session);
            return result;
        } catch (RuntimeException | Error failure) {
            increment(failureCounter);
            throw failure;
        }
    }

    /** Starts a transaction-local governance session around legacy imperative writers. */
    public GovernanceSession begin(ChangeRequest request, Collection<String> seedKeys) {
        if (queryOperations == null) {
            return null;
        }
        ChangeRequest normalized = normalizeRequest(request, CHANGE_UPDATE, null);
        Set<String> keys = normalizeKeys(seedKeys);
        ensureMetadata(normalized, keys);
        long currentVersion = lockHead(normalized.groupCode(), normalized.domainCode());
        verifyExpectedVersion(normalized.expectedVersion(), currentVersion);
        return new GovernanceSession(normalized, currentVersion, readSnapshot(normalized.groupCode(), normalized.domainCode()), Set.copyOf(keys));
    }

    /** Finishes a session in the caller's transaction; no after-commit work is used. */
    public void finish(GovernanceSession session) {
        if (session == null) {
            return;
        }
        ensureMetadata(session.request(), normalizeKeys(session.keys()));
        List<StoredConfig> after = readSnapshot(session.request().groupCode(), session.request().domainCode());
        List<DiffItem> diff = diff(session.before(), after);
        if (diff.isEmpty()) {
            return;
        }
        persistVersion(session.request(), session.currentVersion() + 1, session.before(), after, diff);
        increment(session.request().changeType().equals(CHANGE_ROLLBACK) ? rollbackCounter : publishCounter);
    }

    public void validateGovernedKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Config key is required");
        }
        String key = configKey.trim().toLowerCase(Locale.ROOT);
        if (key.contains("jwt-secret")
                || key.contains("jwt.secret")
                || key.contains("datasource")
                || key.contains("database")
                || key.matches(".*internal[._-]?[^.]*token.*")
                || key.matches(".*(?:oauth|payment)[._-].*(?:secret|private|token|credential).*")
                || key.contains("signing-private-key")
                || key.contains("encryption-key")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "Core authentication, database, internal-token, payment, and OAuth secrets are not platform settings");
        }
    }

    @Transactional
    public VersionDetail rollback(ChangeRequest request, long targetVersion, long expectedCurrentVersion) {
        if (queryOperations == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration version store is unavailable");
        }
        try {
            ChangeRequest normalized = normalizeRequest(request, CHANGE_ROLLBACK, targetVersion);
            if (expectedCurrentVersion < 0) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Expected current config version is required");
            }
            long currentVersion = lockHead(normalized.groupCode(), normalized.domainCode());
            if (expectedCurrentVersion != currentVersion) {
                throw conflict(currentVersion);
            }
            if (targetVersion <= 0 || targetVersion > currentVersion) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Rollback target version is invalid");
            }
            VersionRow target = loadVersion(normalized.groupCode(), normalized.domainCode(), targetVersion, true);
            if (target == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "Rollback target version was not found");
            }
            List<StoredConfig> before = readSnapshot(normalized.groupCode(), normalized.domainCode());
            applySnapshot(normalized, readSnapshotJson(target.snapshotJson()));
            List<StoredConfig> after = readSnapshot(normalized.groupCode(), normalized.domainCode());
            List<DiffItem> diff = diff(before, after);
            if (diff.isEmpty()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Rollback target already matches the current configuration");
            }
            long nextVersion = currentVersion + 1;
            persistVersion(normalized, nextVersion, before, after, diff);
            increment(rollbackCounter);
            return loadVersionDetail(normalized.groupCode(), normalized.domainCode(), nextVersion);
        } catch (RuntimeException | Error failure) {
            increment(failureCounter);
            throw failure;
        }
    }

    public List<VersionSummary> history(String groupCode, String domainCode, long pageNo, long pageSize) {
        String group = normalizeGroup(groupCode);
        String domain = normalizeDomain(domainCode);
        long normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = (int) Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        return queryOperations.query(
                """
                        select id, group_code as groupCode, domain_code as domainCode, version_no as versionNo,
                               change_type as changeType, reason, operator_id as operatorId,
                               operator_uuid as operatorUuid, operator_name as operatorName,
                               expected_version_no as expectedVersionNo, source_version_no as sourceVersionNo,
                               created_at as createdAt
                        from sys_config_version
                        where group_code = ? and domain_code = ?
                        order by version_no desc
                        limit ? offset ?
                        """,
                (row, rowNum) -> toVersionSummary(row.asMap()),
                group,
                domain,
                normalizedPageSize,
                offset
        );
    }

    public long historyTotal(String groupCode, String domainCode) {
        Long total = queryOperations.queryForObject(
                "select count(1) from sys_config_version where group_code = ? and domain_code = ?",
                Long.class,
                normalizeGroup(groupCode),
                normalizeDomain(domainCode)
        );
        return total == null ? 0L : total;
    }

    public VersionDetail detail(String groupCode, String domainCode, long versionNo) {
        VersionDetail detail = loadVersionDetail(normalizeGroup(groupCode), normalizeDomain(domainCode), versionNo);
        if (detail == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Configuration version was not found");
        }
        return detail;
    }

    public ConfigStatus status(String groupCode, String domainCode) {
        String group = normalizeGroup(groupCode);
        String domain = normalizeDomain(domainCode);
        Map<String, Object> row = queryOperations.queryForList(
                """
                        select group_code as groupCode, domain_code as domainCode,
                               current_version_no as currentVersion, status,
                               last_published_at as lastPublishedAt, last_failure_at as lastFailureAt,
                               last_failure_message as lastFailureMessage,
                               last_rollback_version_no as lastRollbackVersion
                        from sys_config_version_head
                        where group_code = ? and domain_code = ?
                        limit 1
                        """,
                group,
                domain
        ).stream().findFirst().orElse(null);
        if (row == null) {
            return new ConfigStatus(group, domain, 0L, "READY", null, null, null, null,
                    counterValue(publishCounter), counterValue(failureCounter), counterValue(rollbackCounter));
        }
        return new ConfigStatus(
                group,
                domain,
                number(row, "currentVersion", "current_version_no"),
                text(row, "status"),
                dateTime(row, "lastPublishedAt", "last_published_at"),
                dateTime(row, "lastFailureAt", "last_failure_at"),
                text(row, "lastFailureMessage"),
                nullableNumber(row, "lastRollbackVersion", "last_rollback_version_no"),
                counterValue(publishCounter),
                counterValue(failureCounter),
                counterValue(rollbackCounter)
        );
    }

    private ChangeRequest normalizeRequest(ChangeRequest request, String changeType, Long sourceVersion) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Configuration change request is required");
        }
        String group = normalizeGroup(request.groupCode());
        String domain = normalizeDomain(request.domainCode());
        String reason = StringUtils.hasText(request.reason()) ? request.reason().trim() : "configuration update";
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Configuration change reason is too long");
        }
        if (request.expectedVersion() != null && request.expectedVersion() < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Expected config version must not be negative");
        }
        return new ChangeRequest(group, domain, request.expectedVersion(), reason,
                request.operator(), changeType, sourceVersion);
    }

    private Set<String> normalizeKeys(Collection<String> keys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (keys == null) {
            return normalized;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key)) {
                String value = key.trim();
                validateGovernedKey(value);
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void ensureMetadata(ChangeRequest request, Collection<String> keys) {
        for (String key : keys) {
            String normalizedKey = key.trim();
            String lower = normalizedKey.toLowerCase(Locale.ROOT);
            String group = request.groupCode();
            String valueType = inferValueType(lower);
            String sensitivity = isSecretKey(lower) ? SENSITIVITY_SECRET : SENSITIVITY_NONE;
            String refreshPolicy = isDynamicKey(lower) ? "DYNAMIC" : "CONTROLLED";
            queryOperations.update(
                    """
                            insert into sys_config_metadata (
                                config_key, group_code, domain_code, value_type, sensitivity,
                                refresh_policy, description, owner_code, created_by, created_by_uuid,
                                updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, 'lumira-system', ?, ?, ?, ?, 0)
                            on duplicate key update
                                group_code = values(group_code), domain_code = values(domain_code),
                                value_type = values(value_type), sensitivity = values(sensitivity),
                                refresh_policy = values(refresh_policy), updated_by = values(updated_by),
                                updated_by_uuid = values(updated_by_uuid), deleted = 0
                            """,
                    normalizedKey,
                    group,
                    request.domainCode(),
                    valueType,
                    sensitivity,
                    refreshPolicy,
                    "Managed platform configuration: " + normalizedKey,
                    operatorId(request.operator()),
                    operatorUuid(request.operator()),
                    operatorId(request.operator()),
                    operatorUuid(request.operator())
            );
        }
    }

    private long lockHead(String group, String domain) {
        queryOperations.update(
                """
                        insert into sys_config_version_head (
                            group_code, domain_code, current_version_no, status
                        ) values (?, ?, 0, 'READY')
                        on duplicate key update group_code = values(group_code)
                        """,
                group,
                domain
        );
        Long current = queryOperations.queryForObject(
                "select current_version_no from sys_config_version_head where group_code = ? and domain_code = ? for update",
                Long.class,
                group,
                domain
        );
        return current == null ? 0L : current;
    }

    private void verifyExpectedVersion(Long expected, long current) {
        if (expected != null && expected != current) {
            throw conflict(current);
        }
    }

    private BizException conflict(long current) {
        return new BizException(ErrorCode.BIZ_ERROR,
                "Configuration version changed, please retry (current=" + current + ")");
    }

    private List<StoredConfig> readSnapshot(String group, String domain) {
        return queryOperations.query(
                """
                        select c.config_key as configKey, c.config_name as configName,
                               c.config_value as configValue, c.config_scope as configScope,
                               c.is_system as isSystem, c.remark as remark,
                               coalesce(m.value_type, 'STRING') as valueType,
                               coalesce(m.sensitivity, 'NONE') as sensitivity,
                               coalesce(m.refresh_policy, 'CONTROLLED') as refreshPolicy,
                               coalesce(m.description, c.remark, c.config_name) as description,
                               coalesce(m.owner_code, 'lumira-system') as ownerCode
                        from sys_config c
                        join sys_config_metadata m on m.config_key = c.config_key
                            and m.domain_code = ? and m.deleted = 0
                        where c.deleted = 0 and m.group_code = ?
                        order by c.config_key asc
                        """,
                (row, rowNum) -> new StoredConfig(
                        text(row.asMap(), "configKey", "config_key"),
                        text(row.asMap(), "configName", "config_name"),
                        text(row.asMap(), "configValue", "config_value"),
                        text(row.asMap(), "configScope", "config_scope"),
                        (int) number(row.asMap(), "isSystem", "is_system"),
                        text(row.asMap(), "remark"),
                        text(row.asMap(), "valueType"),
                        text(row.asMap(), "sensitivity"),
                        text(row.asMap(), "refreshPolicy"),
                        text(row.asMap(), "description"),
                        text(row.asMap(), "ownerCode")
                ),
                domain,
                group
        );
    }

    private List<DiffItem> diff(List<StoredConfig> before, List<StoredConfig> after) {
        Map<String, StoredConfig> beforeByKey = byKey(before);
        Map<String, StoredConfig> afterByKey = byKey(after);
        Set<String> keys = new TreeSet<>();
        keys.addAll(beforeByKey.keySet());
        keys.addAll(afterByKey.keySet());
        List<DiffItem> diff = new ArrayList<>();
        for (String key : keys) {
            StoredConfig oldValue = beforeByKey.get(key);
            StoredConfig newValue = afterByKey.get(key);
            if (!sameConfig(oldValue, newValue)) {
                String sensitivity = newValue != null ? newValue.sensitivity() : oldValue == null ? SENSITIVITY_NONE : oldValue.sensitivity();
                diff.add(new DiffItem(key, sensitivity, maskedValue(oldValue == null ? null : oldValue.configValue(), sensitivity),
                        maskedValue(newValue == null ? null : newValue.configValue(), sensitivity),
                        oldValue == null ? "CREATE" : newValue == null ? "DELETE" : "UPDATE"));
            }
        }
        return diff;
    }

    private void persistVersion(ChangeRequest request, long versionNo, List<StoredConfig> before, List<StoredConfig> after, List<DiffItem> diff) {
        List<StoredConfig> protectedBefore = protectSnapshot(before);
        List<StoredConfig> protectedAfter = protectSnapshot(after);
        String snapshotJson = serialize(protectedAfter);
        CurrentUser operator = request.operator();
        LocalDateTime now = LocalDateTime.now();
        queryOperations.update(
                """
                        insert into sys_config_version (
                            group_code, domain_code, version_no, change_type, reason,
                            operator_id, operator_uuid, operator_name, expected_version_no,
                            source_version_no, snapshot_json, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                request.groupCode(), request.domainCode(), versionNo, request.changeType(), request.reason(),
                operatorId(operator), operatorUuid(operator), operatorName(operator),
                request.expectedVersion(), request.sourceVersion() == null ? null : request.sourceVersion(), snapshotJson, now
        );
        Long versionId = queryOperations.queryForObject(
                "select id from sys_config_version where group_code = ? and domain_code = ? and version_no = ? limit 1",
                Long.class,
                request.groupCode(), request.domainCode(), versionNo
        );
        if (versionId == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration version was not persisted");
        }
        Map<String, StoredConfig> beforeByKey = byKey(protectedBefore);
        Map<String, StoredConfig> afterByKey = byKey(protectedAfter);
        LocalDateTime itemTime = LocalDateTime.now();
        for (DiffItem item : diff) {
            StoredConfig oldValue = beforeByKey.get(item.configKey());
            StoredConfig newValue = afterByKey.get(item.configKey());
            queryOperations.update(
                    """
                            insert into sys_config_version_item (
                                version_id, config_key, value_type, sensitivity, change_type,
                                before_present, after_present, value_before, value_after, created_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    versionId,
                    item.configKey(),
                    newValue != null ? newValue.valueType() : oldValue == null ? "STRING" : oldValue.valueType(),
                    item.sensitivity(),
                    item.changeType(),
                    oldValue == null ? 0 : 1,
                    newValue == null ? 0 : 1,
                    oldValue == null ? null : oldValue.configValue(),
                    newValue == null ? null : newValue.configValue(),
                    itemTime
            );
        }
        queryOperations.update(
                """
                        update sys_config_version_head
                        set current_version_no = ?, status = ?, last_published_at = ?,
                            last_rollback_version_no = ?, updated_at = ?
                        where group_code = ? and domain_code = ?
                        """,
                versionNo,
                request.changeType().equals(CHANGE_ROLLBACK) ? "ROLLED_BACK" : STATUS_PUBLISHED,
                now,
                request.changeType().equals(CHANGE_ROLLBACK) ? request.sourceVersion() : null,
                now,
                request.groupCode(),
                request.domainCode()
        );
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userUuid", operatorUuid(operator));
        event.put("groupCode", request.groupCode());
        event.put("domainCode", request.domainCode());
        event.put("versionNo", versionNo);
        event.put("changeType", request.changeType());
        event.put("sourceVersion", request.sourceVersion());
        event.put("reason", request.reason());
        event.put("changedKeys", diff.stream().map(DiffItem::configKey).toList());
        if (outboxService != null) {
            outboxService.record(
                    PlatformEventTypes.SOURCE_SYSTEM,
                    CONFIG_EVENT_TYPE,
                    operatorId(operator),
                    eventKey(request.groupCode(), request.domainCode(), versionNo),
                    event
            );
        }
        if (readModelVersionService != null) {
            readModelVersionService.bump(READ_MODEL_CONTEXT, READ_MODEL_SCOPE,
                    eventKey(request.groupCode(), request.domainCode(), versionNo));
        }
    }

    private void applySnapshot(ChangeRequest request, List<StoredConfig> target) {
        Set<String> targetKeys = byKey(target).keySet();
        for (StoredConfig current : readSnapshot(request.groupCode(), request.domainCode())) {
            if (!targetKeys.contains(current.configKey())) {
                queryOperations.update(
                        """
                                update sys_config
                                set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                                where config_key = ? and deleted = 0
                                """,
                        operatorId(request.operator()), operatorUuid(request.operator()), LocalDateTime.now(), current.configKey()
                );
            }
        }
        for (StoredConfig config : target) {
            StoredConfig protectedConfig = protectConfig(config);
            queryOperations.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            on duplicate key update
                                config_name = values(config_name), config_value = values(config_value),
                                config_scope = values(config_scope), is_system = values(is_system),
                                remark = values(remark), updated_by = values(updated_by),
                                updated_by_uuid = values(updated_by_uuid), updated_at = values(updated_at), deleted = 0
                            """,
                    protectedConfig.configKey(), protectedConfig.configName(), protectedConfig.configValue(), protectedConfig.configScope(), protectedConfig.isSystem(),
                    protectedConfig.remark(), operatorId(request.operator()), operatorUuid(request.operator()), LocalDateTime.now(),
                    operatorId(request.operator()), operatorUuid(request.operator()), LocalDateTime.now()
            );
        }
    }

    private List<StoredConfig> protectSnapshot(Collection<StoredConfig> configs) {
        List<StoredConfig> protectedConfigs = new ArrayList<>();
        if (configs != null) {
            for (StoredConfig config : configs) {
                if (config != null) {
                    protectedConfigs.add(protectConfig(config));
                }
            }
        }
        return protectedConfigs;
    }

    private StoredConfig protectConfig(StoredConfig config) {
        if (!isSecretSensitivity(config.sensitivity())
                || !StringUtils.hasText(config.configValue())
                || config.configValue().startsWith(FieldCryptoService.PREFIX)) {
            return config;
        }
        if (fieldCryptoService == null) {
            throw new BizException(ErrorCode.BIZ_ERROR,
                    "Sensitive configuration cannot be versioned without field encryption");
        }
        return new StoredConfig(
                config.configKey(),
                config.configName(),
                fieldCryptoService.encrypt(config.configValue()),
                config.configScope(),
                config.isSystem(),
                config.remark(),
                config.valueType(),
                config.sensitivity(),
                config.refreshPolicy(),
                config.description(),
                config.ownerCode()
        );
    }

    private VersionDetail loadVersionDetail(String group, String domain, long versionNo) {
        VersionRow version = loadVersion(group, domain, versionNo, false);
        if (version == null) {
            return null;
        }
        List<DiffItem> items = queryOperations.query(
                """
                        select config_key as configKey, value_type as valueType, sensitivity,
                               change_type as changeType, before_present as beforePresent,
                               after_present as afterPresent, value_before as valueBefore,
                               value_after as valueAfter
                        from sys_config_version_item
                        where version_id = ?
                        order by config_key asc
                        """,
                (row, rowNum) -> {
                    String sensitivity = text(row.asMap(), "sensitivity");
                    return new DiffItem(
                            text(row.asMap(), "configKey", "config_key"),
                            sensitivity,
                            maskedValue(text(row.asMap(), "valueBefore", "value_before"), sensitivity),
                            maskedValue(text(row.asMap(), "valueAfter", "value_after"), sensitivity),
                            text(row.asMap(), "changeType", "change_type")
                    );
                },
                version.id()
        );
        return new VersionDetail(
                toVersionSummary(version.asMap()),
                items
        );
    }

    private VersionRow loadVersion(String group, String domain, long versionNo, boolean includeSnapshot) {
        String snapshotColumn = includeSnapshot ? ", snapshot_json as snapshotJson" : "";
        Map<String, Object> row = queryOperations.queryForList(
                """
                        select id, group_code as groupCode, domain_code as domainCode, version_no as versionNo,
                               change_type as changeType, reason, operator_id as operatorId,
                               operator_uuid as operatorUuid, operator_name as operatorName,
                               expected_version_no as expectedVersionNo, source_version_no as sourceVersionNo,
                               created_at as createdAt
                        """ + snapshotColumn + """
                        from sys_config_version
                        where group_code = ? and domain_code = ? and version_no = ?
                        limit 1
                        """,
                group,
                domain,
                versionNo
        ).stream().findFirst().orElse(null);
        return row == null ? null : new VersionRow(row);
    }

    private List<StoredConfig> readSnapshotJson(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration version snapshot is invalid");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Configuration version snapshot could not be serialized");
        }
    }

    private Map<String, StoredConfig> byKey(Collection<StoredConfig> configs) {
        Map<String, StoredConfig> byKey = new LinkedHashMap<>();
        if (configs != null) {
            for (StoredConfig config : configs) {
                if (config != null && StringUtils.hasText(config.configKey())) {
                    byKey.put(config.configKey(), config);
                }
            }
        }
        return byKey;
    }

    private boolean sameConfig(StoredConfig left, StoredConfig right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.configKey(), right.configKey())
                && Objects.equals(left.configName(), right.configName())
                && Objects.equals(left.configValue(), right.configValue())
                && Objects.equals(left.configScope(), right.configScope())
                && Objects.equals(left.isSystem(), right.isSystem())
                && Objects.equals(left.remark(), right.remark());
    }

    private String maskedValue(String value, String sensitivity) {
        return value == null ? null : isSecretSensitivity(sensitivity) ? "******" : value;
    }

    private boolean isSecretSensitivity(String sensitivity) {
        return SENSITIVITY_SECRET.equalsIgnoreCase(sensitivity);
    }

    private String inferValueType(String key) {
        if (key.endsWith(".enabled") || key.endsWith(".active") || key.endsWith(".allow-multi-device-login")) {
            return "BOOLEAN";
        }
        if (key.endsWith(".port") || key.endsWith(".seconds") || key.endsWith(".minutes") || key.endsWith(".size") || key.endsWith(".weight")) {
            return "INTEGER";
        }
        if (key.endsWith(".order") || key.endsWith(".origins") || key.endsWith(".lines") || key.endsWith(".json")) {
            return "JSON";
        }
        return "STRING";
    }

    private boolean isSecretKey(String key) {
        return key.endsWith(".password") || key.endsWith(".secret") || key.endsWith(".app-secret")
                || key.endsWith(".access-key-secret") || key.endsWith(".private-key")
                || key.endsWith(".credential") || key.endsWith(".token")
                || key.endsWith("-credential") || key.endsWith("-token");
    }

    private boolean isDynamicKey(String key) {
        return key.startsWith("branding.") || key.startsWith("agreement.")
                || key.startsWith("watermark.") || key.startsWith("floating-window.")
                || key.startsWith("profile.field.");
    }

    private String normalizeGroup(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            return "SYSTEM_CONFIG";
        }
        String normalized = groupCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_-]{1,63}")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid config group");
        }
        return normalized;
    }

    private String normalizeDomain(String domainCode) {
        if (!StringUtils.hasText(domainCode)) {
            return DOMAIN_PLATFORM;
        }
        String normalized = domainCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_-]{1,63}")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid config domain");
        }
        return normalized;
    }

    private String eventKey(String group, String domain, long version) {
        return "config-version:" + group + ":" + domain + ":" + version;
    }

    private Long operatorId(CurrentUser operator) {
        return operator == null ? null : operator.getUserId();
    }

    private String operatorUuid(CurrentUser operator) {
        return operator == null ? null : operator.getUserUuid();
    }

    private String operatorName(CurrentUser operator) {
        return operator == null ? "system" : operator.getUsername();
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private double counterValue(Counter counter) {
        return counter == null ? 0D : counter.count();
    }

    private VersionSummary toVersionSummary(Map<String, Object> row) {
        return new VersionSummary(
                number(row, "id"),
                text(row, "groupCode", "group_code"),
                text(row, "domainCode", "domain_code"),
                number(row, "versionNo", "version_no"),
                text(row, "changeType", "change_type"),
                text(row, "reason"),
                nullableNumber(row, "operatorId", "operator_id"),
                text(row, "operatorUuid", "operator_uuid"),
                text(row, "operatorName", "operator_name"),
                nullableNumber(row, "expectedVersionNo", "expected_version_no"),
                nullableNumber(row, "sourceVersionNo", "source_version_no"),
                dateTime(row, "createdAt", "created_at")
        );
    }

    private static String text(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return String.valueOf(row.get(key));
            }
        }
        return null;
    }

    private static long number(Map<String, Object> row, String... keys) {
        Long value = nullableNumber(row, keys);
        return value == null ? 0L : value;
    }

    private static Long nullableNumber(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String... keys) {
        Object value = null;
        for (String key : keys) {
            if (row != null && row.get(key) != null) {
                value = row.get(key);
                break;
            }
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    public record ChangeRequest(
            String groupCode,
            String domainCode,
            Long expectedVersion,
            String reason,
            CurrentUser operator,
            String changeType,
            Long sourceVersion
    ) {
        public ChangeRequest(String groupCode, String domainCode, Long expectedVersion, String reason, CurrentUser operator) {
            this(groupCode, domainCode, expectedVersion, reason, operator, CHANGE_UPDATE, null);
        }
    }

    public record StoredConfig(
            String configKey,
            String configName,
            String configValue,
            String configScope,
            Integer isSystem,
            String remark,
            String valueType,
            String sensitivity,
            String refreshPolicy,
            String description,
            String ownerCode
    ) {
    }

    public record DiffItem(String configKey, String sensitivity, String beforeValue, String afterValue, String changeType) {
    }

    public record VersionSummary(
            long id,
            String groupCode,
            String domainCode,
            long versionNo,
            String changeType,
            String reason,
            Long operatorId,
            String operatorUuid,
            String operatorName,
            Long expectedVersionNo,
            Long sourceVersionNo,
            LocalDateTime createdAt
    ) {
    }

    public record VersionDetail(VersionSummary version, List<DiffItem> diff) {
    }

    public record ConfigStatus(
            String groupCode,
            String domainCode,
            long currentVersion,
            String status,
            LocalDateTime lastPublishedAt,
            LocalDateTime lastFailureAt,
            String lastFailureMessage,
            Long lastRollbackVersion,
            double publishCount,
            double failureCount,
            double rollbackCount
    ) {
    }

    public record GovernanceSession(
            ChangeRequest request,
            long currentVersion,
            List<StoredConfig> before,
            Set<String> keys
    ) {
    }

    private static final class VersionRow {
        private final Map<String, Object> row;

        private VersionRow(Map<String, Object> row) {
            this.row = row;
        }

        private long id() {
            return number(row, "id");
        }

        private String snapshotJson() {
            return text(row, "snapshotJson", "snapshot_json");
        }

        private Map<String, Object> asMap() {
            return row;
        }
    }
}
