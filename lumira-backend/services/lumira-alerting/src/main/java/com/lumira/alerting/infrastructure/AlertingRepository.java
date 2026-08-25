package com.lumira.alerting.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AlertingRepository {
    public static final String PLUGIN_CODE = "builtin-alerting";
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AlertingRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public boolean pluginEnabled() {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_plugin_definition where plugin_code = ? and status = 'ENABLED' and deleted = 0",
                Integer.class,
                PLUGIN_CODE
        );
        return count != null && count > 0;
    }

    public List<ChannelRecord> listChannels() {
        return jdbc.query("""
                select id, name, channel_type, enabled, config_encrypted, config_fingerprint,
                       last_test_status, last_test_error, last_test_at, version, updated_at
                  from alert_channel where deleted = 0 order by id desc
                """, channelMapper());
    }

    public Optional<ChannelRecord> findChannel(long id) {
        return jdbc.query("""
                select id, name, channel_type, enabled, config_encrypted, config_fingerprint,
                       last_test_status, last_test_error, last_test_at, version, updated_at
                  from alert_channel where id = ? and deleted = 0
                """, channelMapper(), id).stream().findFirst();
    }

    public long insertChannel(String name, String type, boolean enabled, String encryptedConfig, String fingerprint, long operatorId) {
        jdbc.update("""
                insert into alert_channel(name, channel_type, enabled, config_encrypted, config_fingerprint,
                    version, created_by, updated_by, deleted)
                values (?, ?, ?, ?, ?, 1, ?, ?, 0)
                """, name, type, enabled ? 1 : 0, encryptedConfig, fingerprint, operatorId, operatorId);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    public void updateChannel(long id, long version, String name, String type, boolean enabled,
                              String encryptedConfig, String fingerprint, long operatorId) {
        int updated = jdbc.update("""
                update alert_channel set name = ?, channel_type = ?, enabled = ?, config_encrypted = ?,
                    config_fingerprint = ?, version = version + 1, updated_by = ?, updated_at = current_timestamp
                where id = ? and version = ? and deleted = 0
                """, name, type, enabled ? 1 : 0, encryptedConfig, fingerprint, operatorId, id, version);
        requireUpdated(updated, "Channel configuration changed; refresh and retry");
    }

    public void deleteChannel(long id, long operatorId) {
        Integer references = jdbc.queryForObject(
                "select count(*) from alert_contact_member where channel_id = ? and deleted = 0",
                Integer.class,
                id
        );
        if (references != null && references > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Channel is still used by a contact group");
        }
        requireUpdated(jdbc.update("""
                update alert_channel set deleted = 1, enabled = 0, version = version + 1,
                    updated_by = ?, updated_at = current_timestamp where id = ? and deleted = 0
                """, operatorId, id), "Channel not found");
    }

    public void recordChannelTest(long id, String status, String error) {
        jdbc.update("""
                update alert_channel set last_test_status = ?, last_test_error = ?, last_test_at = current_timestamp,
                    updated_at = current_timestamp where id = ? and deleted = 0
                """, status, truncate(error, 1000), id);
    }

    public List<AlertingModels.ContactGroupView> listContactGroups() {
        return jdbc.query("""
                select id, name, enabled, version, updated_at
                  from alert_contact_group where deleted = 0 order by id desc
                """, (rs, row) -> new AlertingModels.ContactGroupView(
                rs.getLong("id"), rs.getString("name"), rs.getBoolean("enabled"),
                listContactMembers(rs.getLong("id")), rs.getLong("version"), local(rs, "updated_at")
        ));
    }

    public Optional<AlertingModels.ContactGroupView> findContactGroup(long id) {
        return listContactGroups().stream().filter(group -> group.id() == id).findFirst();
    }

    public List<AlertingModels.ContactMemberView> listContactMembers(long groupId) {
        return jdbc.query("""
                select m.id, m.channel_id, c.name channel_name, c.channel_type, m.member_type,
                       m.target_identifier, m.display_name, m.enabled
                  from alert_contact_member m
                  join alert_channel c on c.id = m.channel_id and c.deleted = 0
                 where m.contact_group_id = ? and m.deleted = 0 order by m.id
                """, (rs, row) -> new AlertingModels.ContactMemberView(
                rs.getLong("id"), rs.getLong("channel_id"), rs.getString("channel_name"),
                rs.getString("channel_type"), rs.getString("member_type"),
                rs.getString("target_identifier"), rs.getString("display_name"), rs.getBoolean("enabled")
        ), groupId);
    }

    @Transactional
    public long saveContactGroup(Long id, Long expectedVersion, String name, boolean enabled,
                                 List<AlertingModels.ContactMemberRequest> members, long operatorId) {
        long groupId;
        if (id == null) {
            jdbc.update("""
                    insert into alert_contact_group(name, enabled, version, created_by, updated_by, deleted)
                    values (?, ?, 1, ?, ?, 0)
                    """, name, enabled ? 1 : 0, operatorId, operatorId);
            groupId = jdbc.queryForObject("select last_insert_id()", Long.class);
        } else {
            if (expectedVersion == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Contact group version is required");
            }
            requireUpdated(jdbc.update("""
                    update alert_contact_group set name = ?, enabled = ?, version = version + 1,
                        updated_by = ?, updated_at = current_timestamp
                    where id = ? and version = ? and deleted = 0
                    """, name, enabled ? 1 : 0, operatorId, id, expectedVersion),
                    "Contact group changed; refresh and retry");
            groupId = id;
            jdbc.update("update alert_contact_member set deleted = 1, updated_by = ?, updated_at = current_timestamp where contact_group_id = ? and deleted = 0",
                    operatorId, groupId);
        }
        for (AlertingModels.ContactMemberRequest member : members) {
            jdbc.update("""
                    insert into alert_contact_member(contact_group_id, channel_id, member_type, target_identifier,
                        display_name, enabled, created_by, updated_by, deleted)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, groupId, member.channelId(), member.memberType(), member.targetIdentifier(),
                    member.displayName(), member.enabled() ? 1 : 0, operatorId, operatorId);
        }
        return groupId;
    }

    public void deleteContactGroup(long id, long operatorId) {
        Integer references = jdbc.queryForObject("select count(*) from alert_rule where contact_group_id = ? and deleted = 0", Integer.class, id);
        if (references != null && references > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Contact group is still used by an alert rule");
        }
        requireUpdated(jdbc.update("""
                update alert_contact_group set deleted = 1, enabled = 0, version = version + 1,
                    updated_by = ?, updated_at = current_timestamp where id = ? and deleted = 0
                """, operatorId, id), "Contact group not found");
        jdbc.update("update alert_contact_member set deleted = 1, updated_by = ?, updated_at = current_timestamp where contact_group_id = ? and deleted = 0",
                operatorId, id);
    }

    public List<AlertingModels.RuleView> listRules() {
        return jdbc.query("""
                select r.id, r.name, r.source_type, r.signal_key, r.comparator, r.threshold_value,
                       r.window_seconds, r.pending_seconds, r.severity, r.contact_group_id,
                       g.name contact_group_name, r.enabled, r.labels_json, r.evaluation_error,
                       r.last_evaluated_at, r.version, r.updated_at
                  from alert_rule r
                  join alert_contact_group g on g.id = r.contact_group_id and g.deleted = 0
                 where r.deleted = 0 order by r.id desc
                """, ruleMapper());
    }

    public Optional<AlertingModels.RuleView> findRule(long id) {
        return listRules().stream().filter(rule -> rule.id() == id).findFirst();
    }

    public long insertRule(AlertingModels.RuleRequest request, long operatorId) {
        jdbc.update("""
                insert into alert_rule(name, source_type, signal_key, comparator, threshold_value,
                    window_seconds, pending_seconds, severity, contact_group_id, enabled, labels_json,
                    version, created_by, updated_by, deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 0)
                """, request.name(), request.sourceType(), request.signalKey(), request.comparator(), request.threshold(),
                request.windowSeconds(), request.pendingSeconds(), request.severity(), request.contactGroupId(),
                request.enabled() ? 1 : 0, json(request.labels() == null ? Map.of() : request.labels()), operatorId, operatorId);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    public void updateRule(long id, AlertingModels.RuleRequest request, long operatorId) {
        if (request.version() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Rule version is required");
        }
        requireUpdated(jdbc.update("""
                update alert_rule set name = ?, source_type = ?, signal_key = ?, comparator = ?, threshold_value = ?,
                    window_seconds = ?, pending_seconds = ?, severity = ?, contact_group_id = ?, enabled = ?,
                    labels_json = ?, evaluation_error = null, version = version + 1,
                    updated_by = ?, updated_at = current_timestamp
                where id = ? and version = ? and deleted = 0
                """, request.name(), request.sourceType(), request.signalKey(), request.comparator(), request.threshold(),
                request.windowSeconds(), request.pendingSeconds(), request.severity(), request.contactGroupId(),
                request.enabled() ? 1 : 0, json(request.labels() == null ? Map.of() : request.labels()),
                operatorId, id, request.version()), "Rule changed; refresh and retry");
    }

    public void deleteRule(long id, long operatorId) {
        requireUpdated(jdbc.update("""
                update alert_rule set deleted = 1, enabled = 0, version = version + 1,
                    updated_by = ?, updated_at = current_timestamp where id = ? and deleted = 0
                """, operatorId, id), "Rule not found");
    }

    public List<AlertingModels.SilenceView> listSilences() {
        return jdbc.query("""
                select s.id, s.name, s.rule_id, r.name rule_name, s.starts_at, s.ends_at, s.reason,
                       s.enabled, s.version, s.updated_at
                  from alert_silence s left join alert_rule r on r.id = s.rule_id
                 where s.deleted = 0 order by s.id desc
                """, (rs, row) -> new AlertingModels.SilenceView(
                rs.getLong("id"), rs.getString("name").isBlank() ? "-" : rs.getString("name"),
                nullableLong(rs, "rule_id"), rs.getString("rule_name"), local(rs, "starts_at"), local(rs, "ends_at"), rs.getString("reason"),
                rs.getBoolean("enabled"), rs.getLong("version"), local(rs, "updated_at")
        ));
    }

    public long saveSilence(Long id, AlertingModels.SilenceRequest request, long operatorId) {
        if (id == null) {
            jdbc.update("""
                    insert into alert_silence(name, rule_id, starts_at, ends_at, reason, enabled,
                        version, created_by, updated_by, deleted)
                    values (?, ?, ?, ?, ?, ?, 1, ?, ?, 0)
                    """, request.name(), request.ruleId(), request.startsAt(), request.endsAt(), request.reason(),
                    request.enabled() ? 1 : 0, operatorId, operatorId);
            return jdbc.queryForObject("select last_insert_id()", Long.class);
        }
        if (request.version() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Silence version is required");
        }
        requireUpdated(jdbc.update("""
                update alert_silence set name = ?, rule_id = ?, starts_at = ?, ends_at = ?, reason = ?, enabled = ?,
                    version = version + 1, updated_by = ?, updated_at = current_timestamp
                where id = ? and version = ? and deleted = 0
                """, request.name(), request.ruleId(), request.startsAt(), request.endsAt(), request.reason(),
                request.enabled() ? 1 : 0, operatorId, id, request.version()), "Silence changed; refresh and retry");
        return id;
    }

    public void deleteSilence(long id, long operatorId) {
        requireUpdated(jdbc.update("""
                update alert_silence set deleted = 1, enabled = 0, version = version + 1,
                    updated_by = ?, updated_at = current_timestamp where id = ? and deleted = 0
                """, operatorId, id), "Silence not found");
    }

    public boolean isSilenced(long ruleId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from alert_silence
                 where deleted = 0 and enabled = 1 and starts_at <= current_timestamp and ends_at > current_timestamp
                   and (rule_id is null or rule_id = ?)
                """, Integer.class, ruleId);
        return count != null && count > 0;
    }

    public List<AlertingModels.AlertInstanceView> listInstances(String status) {
        String sql = """
                select i.id, i.rule_id, r.name rule_name, r.severity, i.status, i.last_value,
                       i.started_at, i.firing_at, i.resolved_at, i.acknowledged_at, i.acknowledged_by,
                       r.evaluation_error, i.version
                  from alert_instance i join alert_rule r on r.id = i.rule_id
                 where (? is null or i.status = ?) order by i.id desc limit 500
                """;
        return jdbc.query(sql, (rs, row) -> new AlertingModels.AlertInstanceView(
                rs.getLong("id"), rs.getLong("rule_id"), rs.getString("rule_name"), rs.getString("severity"),
                rs.getString("status"), rs.getBigDecimal("last_value"), local(rs, "started_at"),
                local(rs, "firing_at"), local(rs, "resolved_at"), local(rs, "acknowledged_at"),
                nullableLong(rs, "acknowledged_by"), rs.getString("evaluation_error"), rs.getLong("version")
        ), status, status);
    }

    public void acknowledge(long instanceId, long version, long operatorId) {
        requireUpdated(jdbc.update("""
                update alert_instance set acknowledged_at = current_timestamp, acknowledged_by = ?,
                    version = version + 1, updated_at = current_timestamp
                where id = ? and version = ? and status = 'FIRING'
                """, operatorId, instanceId, version), "Alert changed or is no longer firing");
    }

    public List<AlertingModels.DeliveryView> listDeliveries(String status) {
        return jdbc.query("""
                select d.id, d.instance_id, e.event_type, c.name channel_name, c.channel_type,
                       d.recipient, d.status, d.attempts, d.last_error, d.next_attempt_at, d.sent_at, d.created_at
                  from alert_delivery d
                  join alert_event e on e.id = d.event_id
                  join alert_channel c on c.id = d.channel_id
                 where (? is null or d.status = ?) order by d.id desc limit 500
                """, (rs, row) -> new AlertingModels.DeliveryView(
                rs.getLong("id"), rs.getLong("instance_id"), rs.getString("event_type"),
                rs.getString("channel_name"), rs.getString("channel_type"), rs.getString("recipient"),
                rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                local(rs, "next_attempt_at"), local(rs, "sent_at"), local(rs, "created_at")
        ), status, status);
    }

    public void retryDelivery(long id) {
        requireUpdated(jdbc.update("""
                update alert_delivery set status = 'PENDING', attempts = 0, last_error = null,
                    next_attempt_at = current_timestamp, claim_token = null, claim_until = null,
                    updated_at = current_timestamp where id = ? and status in ('FAILED', 'DEAD_LETTER')
                """, id), "Delivery is not retryable");
    }

    public List<AlertingModels.DirectoryMappingView> listDirectoryMappings(Long channelId) {
        return jdbc.query("""
                select id, channel_id, user_id, user_uuid, provider_user_id, provider_display_name,
                       match_source, mapping_status, manual_override, synced_at
                  from alert_directory_mapping
                 where deleted = 0 and (? is null or channel_id = ?) order by id desc
                """, (rs, row) -> new AlertingModels.DirectoryMappingView(
                rs.getLong("id"), rs.getLong("channel_id"), rs.getLong("user_id"), rs.getString("user_uuid"),
                rs.getString("provider_user_id"), rs.getString("provider_display_name"), rs.getString("match_source"),
                rs.getString("mapping_status"), rs.getBoolean("manual_override"), local(rs, "synced_at")
        ), channelId, channelId);
    }

    public long saveDirectoryMapping(AlertingModels.DirectoryMappingRequest request, long operatorId) {
        jdbc.update("""
                insert into alert_directory_mapping(channel_id, user_id, user_uuid, provider_user_id,
                    provider_display_name, match_source, mapping_status, manual_override, synced_at,
                    created_by, updated_by, deleted)
                values (?, ?, ?, ?, ?, 'MANUAL', 'MATCHED', 1, current_timestamp, ?, ?, 0)
                on duplicate key update provider_user_id = values(provider_user_id),
                    provider_display_name = values(provider_display_name), match_source = 'MANUAL',
                    mapping_status = 'MATCHED', manual_override = 1, synced_at = current_timestamp,
                    updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                """, request.channelId(), request.userId(), request.userUuid(), request.providerUserId(),
                request.providerDisplayName(), operatorId, operatorId);
        return jdbc.queryForObject("""
                select id from alert_directory_mapping where channel_id = ? and user_uuid = ? and deleted = 0
                """, Long.class, request.channelId(), request.userUuid());
    }

    public List<LocalDirectoryUser> localDirectoryUsers() {
        return jdbc.query("""
                select id, uuid, coalesce(real_name, nickname, username) display_name, email, mobile
                  from sys_user where deleted = 0 and status = 'ENABLED' order by id
                """, (rs, row) -> new LocalDirectoryUser(
                rs.getLong("id"), rs.getString("uuid"), rs.getString("display_name"),
                rs.getString("email"), rs.getString("mobile")
        ));
    }

    @Transactional
    public void replaceAutomaticDirectoryMappings(long channelId, List<AutomaticMapping> mappings, long operatorId) {
        for (AutomaticMapping mapping : mappings) {
            jdbc.update("""
                    insert into alert_directory_mapping(channel_id, user_id, user_uuid, provider_user_id,
                        provider_display_name, match_source, mapping_status, manual_override, synced_at,
                        created_by, updated_by, deleted)
                    values (?, ?, ?, ?, ?, ?, ?, 0, current_timestamp, ?, ?, 0)
                    on duplicate key update
                        provider_user_id = if(manual_override = 0, values(provider_user_id), provider_user_id),
                        provider_display_name = if(manual_override = 0, values(provider_display_name), provider_display_name),
                        match_source = if(manual_override = 0, values(match_source), match_source),
                        mapping_status = if(manual_override = 0, values(mapping_status), mapping_status),
                        synced_at = current_timestamp,
                        updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                    """, channelId, mapping.userId(), mapping.userUuid(), mapping.providerUserId(),
                    mapping.providerDisplayName(), mapping.matchSource(), mapping.status(), operatorId, operatorId);
        }
    }

    public AlertingModels.HealthView health() {
        WorkerHeartbeat worker = jdbc.query("""
                select owner_id, heartbeat_at from alert_worker_lease where lease_name = 'alerting-main'
                """, (rs, row) -> new WorkerHeartbeat(rs.getString("owner_id"), local(rs, "heartbeat_at")))
                .stream().findFirst().orElse(new WorkerHeartbeat(null, null));
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(2);
        String workerStatus = worker.heartbeatAt() == null ? "NEVER_SEEN"
                : worker.heartbeatAt().isBefore(staleBefore) ? "STALE" : "HEALTHY";
        return new AlertingModels.HealthView(
                pluginEnabled(), workerStatus, worker.heartbeatAt(), count("select count(*) from alert_rule where deleted = 0 and enabled = 1"),
                count("select count(*) from alert_instance where status = 'FIRING'"),
                count("select count(*) from alert_delivery where status in ('PENDING','RETRY')"),
                count("select count(*) from alert_delivery where status = 'DEAD_LETTER'"),
                jdbc.query("select evaluation_error from alert_rule where evaluation_error is not null order by last_evaluated_at desc limit 1",
                        (rs, row) -> rs.getString(1)).stream().findFirst().orElse(null)
        );
    }

    public void pausePendingDeliveries() {
        jdbc.update("""
                update alert_delivery set status = 'PAUSED', claim_token = null, claim_until = null,
                    updated_at = current_timestamp
                 where status in ('PENDING','RETRY')
                    or (status = 'SENDING' and claim_until < current_timestamp)
                """);
    }

    public void resumePendingDeliveries() {
        jdbc.update("update alert_delivery set status = 'PENDING', next_attempt_at = current_timestamp, updated_at = current_timestamp where status = 'PAUSED'");
    }

    public boolean acquireLease(String ownerId, int leaseSeconds) {
        jdbc.update("""
                insert into alert_worker_lease(lease_name, owner_id, lease_until, heartbeat_at)
                values ('alerting-main', ?, date_add(current_timestamp, interval ? second), current_timestamp)
                on duplicate key update
                    owner_id = if(lease_until < current_timestamp or owner_id = values(owner_id), values(owner_id), owner_id),
                    lease_until = if(lease_until < current_timestamp or owner_id = values(owner_id), values(lease_until), lease_until),
                    heartbeat_at = if(owner_id = values(owner_id), current_timestamp, heartbeat_at)
                """, ownerId, leaseSeconds);
        String currentOwner = jdbc.queryForObject("select owner_id from alert_worker_lease where lease_name = 'alerting-main'", String.class);
        return ownerId.equals(currentOwner);
    }

    public List<AlertingModels.RuleView> dueRules(int limit) {
        return jdbc.query("""
                select r.id, r.name, r.source_type, r.signal_key, r.comparator, r.threshold_value,
                       r.window_seconds, r.pending_seconds, r.severity, r.contact_group_id,
                       g.name contact_group_name, r.enabled, r.labels_json, r.evaluation_error,
                       r.last_evaluated_at, r.version, r.updated_at
                  from alert_rule r join alert_contact_group g on g.id = r.contact_group_id and g.deleted = 0 and g.enabled = 1
                 where r.deleted = 0 and r.enabled = 1
                   and (r.last_evaluated_at is null or r.last_evaluated_at <= date_sub(current_timestamp, interval 55 second))
                 order by coalesce(r.last_evaluated_at, '1970-01-01') limit ?
                """, ruleMapper(), limit);
    }

    public BigDecimal businessSignalValue(String signalKey, int windowSeconds) {
        String sql = switch (signalKey) {
            case "business.payment.paid" -> """
                    select count(*) from payment_event_outbox
                     where deleted = 0 and event_type = 'PAYMENT_ORDER_PAID'
                       and created_at >= date_sub(current_timestamp, interval ? second)
                    """;
            case "business.registration.submitted" -> """
                    select count(*) from competition_registration
                     where deleted = 0 and created_at >= date_sub(current_timestamp, interval ? second)
                    """;
            case "business.review.completed" -> """
                    select count(*) from competition_review_publication
                     where deleted = 0 and status = 'PUBLISHED'
                       and published_at >= date_sub(current_timestamp, interval ? second)
                    """;
            case "business.file.scan.failed" -> """
                    select count(*) from file_processing_task
                     where deleted = 0 and task_type = 'SECURITY_SCAN' and status = 'FAILED'
                       and updated_at >= date_sub(current_timestamp, interval ? second)
                    """;
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported business signal");
        };
        Long value = jdbc.queryForObject(sql, Long.class, windowSeconds);
        return BigDecimal.valueOf(value == null ? 0 : value);
    }

    public Optional<InstanceRecord> activeInstance(long ruleId) {
        return jdbc.query("""
                select id, rule_id, fingerprint, status, last_value, pending_since, firing_at,
                       acknowledged_at, consecutive_ok, version
                  from alert_instance where rule_id = ? and status in ('PENDING','FIRING') order by id desc limit 1
                """, instanceMapper(), ruleId).stream().findFirst();
    }

    public long createPendingInstance(long ruleId, BigDecimal value) {
        String fingerprint = "rule:" + ruleId;
        jdbc.update("""
                insert into alert_instance(rule_id, fingerprint, status, last_value, pending_since,
                    started_at, consecutive_ok, version, created_at, updated_at)
                values (?, ?, 'PENDING', ?, current_timestamp, current_timestamp, 0, 1, current_timestamp, current_timestamp)
                """, ruleId, fingerprint, value);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    public void updatePendingValue(long instanceId, BigDecimal value) {
        jdbc.update("update alert_instance set last_value = ?, consecutive_ok = 0, version = version + 1, updated_at = current_timestamp where id = ? and status = 'PENDING'",
                value, instanceId);
    }

    public void recordBreached(long instanceId, BigDecimal value) {
        jdbc.update("""
                update alert_instance set last_value = ?, consecutive_ok = 0,
                    version = version + 1, updated_at = current_timestamp
                 where id = ? and status in ('PENDING','FIRING')
                """, value, instanceId);
    }

    public boolean promoteToFiring(long instanceId, BigDecimal value) {
        return jdbc.update("""
                update alert_instance set status = 'FIRING', last_value = ?, firing_at = current_timestamp,
                    consecutive_ok = 0, version = version + 1, updated_at = current_timestamp
                 where id = ? and status = 'PENDING'
                """, value, instanceId) == 1;
    }

    public void removePending(long instanceId) {
        jdbc.update("update alert_instance set status = 'RESOLVED', resolved_at = current_timestamp, version = version + 1, updated_at = current_timestamp where id = ? and status = 'PENDING'",
                instanceId);
    }

    public boolean recordHealthyEvaluation(InstanceRecord instance, BigDecimal value) {
        int next = instance.consecutiveOk() + 1;
        if (instance.status().equals("FIRING") && next >= 2) {
            return jdbc.update("""
                    update alert_instance set status = 'RESOLVED', last_value = ?, consecutive_ok = ?,
                        resolved_at = current_timestamp, version = version + 1, updated_at = current_timestamp
                     where id = ? and status = 'FIRING'
                    """, value, next, instance.id()) == 1;
        }
        jdbc.update("update alert_instance set last_value = ?, consecutive_ok = ?, version = version + 1, updated_at = current_timestamp where id = ?",
                value, next, instance.id());
        return false;
    }

    public List<RepeatCandidate> repeatCandidates(int limit) {
        return jdbc.query("""
                select i.id instance_id, i.last_value,
                       r.id, r.name, r.source_type, r.signal_key, r.comparator, r.threshold_value,
                       r.window_seconds, r.pending_seconds, r.severity, r.contact_group_id,
                       g.name contact_group_name, r.enabled, r.labels_json, r.evaluation_error,
                       r.last_evaluated_at, r.version, r.updated_at
                  from alert_instance i
                  join alert_rule r on r.id = i.rule_id and r.deleted = 0 and r.enabled = 1
                  join alert_contact_group g on g.id = r.contact_group_id and g.deleted = 0 and g.enabled = 1
                 where i.status = 'FIRING' and i.acknowledged_at is null
                   and (i.last_notified_at is null or i.last_notified_at <= date_sub(current_timestamp, interval 4 hour))
                 order by coalesce(i.last_notified_at, i.firing_at) limit ?
                """, (rs, row) -> new RepeatCandidate(
                rs.getLong("instance_id"), rs.getBigDecimal("last_value"),
                new AlertingModels.RuleView(
                        rs.getLong("id"), rs.getString("name"), rs.getString("source_type"), rs.getString("signal_key"),
                        rs.getString("comparator"), rs.getBigDecimal("threshold_value"), rs.getInt("window_seconds"),
                        rs.getInt("pending_seconds"), rs.getString("severity"), rs.getLong("contact_group_id"),
                        rs.getString("contact_group_name"), rs.getBoolean("enabled"), stringMap(rs.getString("labels_json")),
                        rs.getString("evaluation_error"), local(rs, "last_evaluated_at"), rs.getLong("version"), local(rs, "updated_at")
                )
        ), limit);
    }

    public void recordEvaluation(long ruleId, String error) {
        jdbc.update("""
                update alert_rule set last_evaluated_at = current_timestamp, evaluation_error = ?,
                    updated_at = current_timestamp where id = ? and deleted = 0
                """, truncate(error, 1000), ruleId);
    }

    @Transactional
    public long createEventAndDeliveries(long instanceId, long contactGroupId, String eventType, String payloadJson) {
        jdbc.update("insert into alert_event(instance_id, event_type, payload_json, created_at) values (?, ?, ?, current_timestamp)",
                instanceId, eventType, payloadJson);
        long eventId = jdbc.queryForObject("select last_insert_id()", Long.class);
        List<AlertingModels.ContactMemberView> members = listContactMembers(contactGroupId).stream()
                .filter(AlertingModels.ContactMemberView::enabled).toList();
        for (AlertingModels.ContactMemberView member : members) {
            String dedupe = eventId + ":" + member.channelId() + ":" + member.targetIdentifier();
            try {
                jdbc.update("""
                        insert into alert_delivery(event_id, instance_id, channel_id, member_type, recipient, dedupe_key, status,
                            attempts, next_attempt_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, sha2(?, 256), 'PENDING', 0, date_add(current_timestamp, interval 30 second),
                            current_timestamp, current_timestamp)
                        """, eventId, instanceId, member.channelId(), member.memberType(), member.targetIdentifier(), dedupe);
            } catch (DuplicateKeyException ignored) {
                // At-least-once producer retries are collapsed by the durable dedupe key.
            }
        }
        jdbc.update("update alert_instance set last_notified_at = current_timestamp, updated_at = current_timestamp where id = ?", instanceId);
        return eventId;
    }

    public List<DeliveryJob> claimDeliveries(String claimToken, int limit) {
        jdbc.update("""
                update alert_delivery set claim_token = ?, claim_until = date_add(current_timestamp, interval 60 second),
                    status = 'SENDING', updated_at = current_timestamp
                 where ((status in ('PENDING','RETRY') and next_attempt_at <= current_timestamp)
                    or (status = 'SENDING' and claim_until < current_timestamp))
                   and (claim_until is null or claim_until < current_timestamp)
                 order by id limit ?
                """, claimToken, limit);
        return jdbc.query("""
                select d.id, d.event_id, d.instance_id, d.channel_id, d.member_type, d.recipient, d.attempts,
                       e.event_type, e.payload_json
                  from alert_delivery d join alert_event e on e.id = d.event_id
                 where d.claim_token = ? and d.status = 'SENDING' order by d.id
                """, (rs, row) -> new DeliveryJob(
                rs.getLong("id"), rs.getLong("event_id"), rs.getLong("instance_id"), rs.getLong("channel_id"),
                rs.getString("member_type"), rs.getString("recipient"), rs.getInt("attempts"),
                rs.getString("event_type"), rs.getString("payload_json")
        ), claimToken);
    }

    public void pauseClaimedDelivery(long deliveryId) {
        jdbc.update("""
                update alert_delivery set status = 'PAUSED', claim_token = null, claim_until = null,
                    updated_at = current_timestamp
                 where id = ? and status = 'SENDING'
                """, deliveryId);
    }

    public void completeDelivery(long deliveryId, String providerMessageId, String responseSummary) {
        jdbc.update("""
                update alert_delivery set status = 'SENT', attempts = attempts + 1, provider_message_id = ?,
                    provider_response = ?, sent_at = current_timestamp, claim_token = null, claim_until = null,
                    last_error = null, updated_at = current_timestamp where id = ? and status = 'SENDING'
                """, truncate(providerMessageId, 256), truncate(responseSummary, 1000), deliveryId);
        recordAttempt(deliveryId, "SENT", null, responseSummary);
    }

    public void failDelivery(DeliveryJob job, boolean retryable, String error, String responseSummary) {
        int nextAttempts = job.attempts() + 1;
        boolean dead = !retryable || nextAttempts >= 8;
        long delaySeconds = Math.min(3600L, 30L * (1L << Math.min(nextAttempts - 1, 7)));
        jdbc.update("""
                update alert_delivery set status = ?, attempts = ?, last_error = ?, provider_response = ?,
                    next_attempt_at = date_add(current_timestamp, interval ? second), claim_token = null,
                    claim_until = null, updated_at = current_timestamp where id = ? and status = 'SENDING'
                """, dead ? "DEAD_LETTER" : "RETRY", nextAttempts, truncate(error, 1000),
                truncate(responseSummary, 1000), delaySeconds, job.id());
        recordAttempt(job.id(), dead ? "DEAD_LETTER" : "RETRY", error, responseSummary);
    }

    public String newClaimToken() {
        return UUID.randomUUID().toString();
    }

    public Optional<String> resolveMappedRecipient(long channelId, String target) {
        if (target == null || !target.startsWith("user:")) {
            return Optional.ofNullable(target);
        }
        String userUuid = target.substring("user:".length());
        return jdbc.query("""
                select provider_user_id from alert_directory_mapping
                 where channel_id = ? and user_uuid = ? and mapping_status = 'MATCHED' and deleted = 0
                """, (rs, row) -> rs.getString(1), channelId, userUuid).stream().findFirst();
    }

    private void recordAttempt(long deliveryId, String outcome, String error, String response) {
        jdbc.update("""
                insert into alert_delivery_attempt(delivery_id, outcome, error_message, response_summary, created_at)
                values (?, ?, ?, ?, current_timestamp)
                """, deliveryId, outcome, truncate(error, 1000), truncate(response, 1000));
    }

    private RowMapper<ChannelRecord> channelMapper() {
        return (rs, row) -> new ChannelRecord(
                rs.getLong("id"), rs.getString("name"), rs.getString("channel_type"), rs.getBoolean("enabled"),
                rs.getString("config_encrypted"), rs.getString("config_fingerprint"), rs.getString("last_test_status"),
                rs.getString("last_test_error"), local(rs, "last_test_at"), rs.getLong("version"), local(rs, "updated_at")
        );
    }

    private RowMapper<AlertingModels.RuleView> ruleMapper() {
        return (rs, row) -> new AlertingModels.RuleView(
                rs.getLong("id"), rs.getString("name"), rs.getString("source_type"), rs.getString("signal_key"),
                rs.getString("comparator"), rs.getBigDecimal("threshold_value"), rs.getInt("window_seconds"),
                rs.getInt("pending_seconds"), rs.getString("severity"), rs.getLong("contact_group_id"),
                rs.getString("contact_group_name"), rs.getBoolean("enabled"), stringMap(rs.getString("labels_json")),
                rs.getString("evaluation_error"), local(rs, "last_evaluated_at"), rs.getLong("version"), local(rs, "updated_at")
        );
    }

    private RowMapper<InstanceRecord> instanceMapper() {
        return (rs, row) -> new InstanceRecord(
                rs.getLong("id"), rs.getLong("rule_id"), rs.getString("fingerprint"), rs.getString("status"),
                rs.getBigDecimal("last_value"), local(rs, "pending_since"), local(rs, "firing_at"),
                local(rs, "acknowledged_at"), rs.getInt("consecutive_ok"), rs.getLong("version")
        );
    }

    private Map<String, String> stringMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid JSON configuration");
        }
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static void requireUpdated(int updated, String message) {
        if (updated != 1) throw new BizException(ErrorCode.BIZ_ERROR, message);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime local(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record ChannelRecord(long id, String name, String type, boolean enabled, String encryptedConfig,
                                String configFingerprint, String lastTestStatus, String lastTestError,
                                LocalDateTime lastTestAt, long version, LocalDateTime updatedAt) { }

    public record InstanceRecord(long id, long ruleId, String fingerprint, String status, BigDecimal lastValue,
                                 LocalDateTime pendingSince, LocalDateTime firingAt, LocalDateTime acknowledgedAt,
                                 int consecutiveOk, long version) { }

    public record DeliveryJob(long id, long eventId, long instanceId, long channelId, String memberType, String recipient,
                              int attempts, String eventType, String payloadJson) { }

    public record RepeatCandidate(long instanceId, BigDecimal lastValue, AlertingModels.RuleView rule) { }

    public record LocalDirectoryUser(long userId, String userUuid, String displayName, String email, String mobile) { }

    public record AutomaticMapping(long userId, String userUuid, String providerUserId,
                                   String providerDisplayName, String matchSource, String status) { }

    private record WorkerHeartbeat(String ownerId, LocalDateTime heartbeatAt) { }
}
