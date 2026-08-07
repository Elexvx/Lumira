package com.lumira.saas.modules.system.config.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxMapper;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxService;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemConfigVersioningServiceTest {

    private static final String KEY = "branding.site-name";

    @Test
    void publishPersistsImmutableDiffAndVersionNotifications() {
        SystemConfigVersioningService.StoredConfig before = config(KEY, "Lumira", SystemConfigVersioningService.SENSITIVITY_NONE);
        SystemConfigVersioningService.StoredConfig after = config(KEY, "Lumira Cloud", SystemConfigVersioningService.SENSITIVITY_NONE);
        Fixture fixture = fixture(0L, List.of(before), List.of(after));
        AtomicBoolean mutationCalled = new AtomicBoolean();

        String result = fixture.service.publish(
                change("BRANDING", 0L, "update site name"),
                List.of(KEY),
                () -> {
                    mutationCalled.set(true);
                    return "saved";
                }
        );

        assertThat(result).isEqualTo("saved");
        assertThat(mutationCalled).isTrue();
        assertThat(fixture.updates).anyMatch(sql -> sql.toLowerCase().contains("insert into sys_config_version ("));
        assertThat(fixture.updates).anyMatch(sql -> sql.toLowerCase().contains("insert into sys_config_version_item"));
        verify(fixture.outbox).record(
                eq(PlatformEventTypes.SOURCE_SYSTEM),
                eq("SYSTEM_CONFIG_VERSION_PUBLISHED"),
                eq(100L),
                eq("config-version:BRANDING:PLATFORM:1"),
                any()
        );
        verify(fixture.readModel).bump("platform", "configuration", "config-version:BRANDING:PLATFORM:1");
    }

    @Test
    void publishWritesOutboxPayloadThatMatchesTrustedOperator() throws Exception {
        MyBatisQueryOperations configOperations = mock(MyBatisQueryOperations.class);
        doAnswer(invocation -> 1)
                .when(configOperations)
                .update(anyString(), any(Object[].class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class).toLowerCase();
            return sql.contains("current_version_no") ? 0L : 42L;
        }).when(configOperations).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        AtomicInteger snapshotReads = new AtomicInteger();
        doAnswer(invocation -> snapshotReads.getAndIncrement() == 0
                ? List.of(config(KEY, "Lumira", SystemConfigVersioningService.SENSITIVITY_NONE))
                : List.of(config(KEY, "Lumira Cloud", SystemConfigVersioningService.SENSITIVITY_NONE)))
                .when(configOperations)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        MyBatisQueryOperations identityOperations = mock(MyBatisQueryOperations.class);
        doAnswer(invocation -> operator().getUserUuid())
                .when(identityOperations)
                .queryForObject(anyString(), eq(String.class), any(Object[].class));
        PlatformEventOutboxMapper outboxMapper = mock(PlatformEventOutboxMapper.class);
        when(outboxMapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        ObjectMapper objectMapper = new ObjectMapper();
        PlatformEventOutboxService realOutbox = new PlatformEventOutboxService(
                objectMapper,
                outboxMapper,
                identityOperations
        );
        SystemConfigVersioningService service = new SystemConfigVersioningService(
                configOperations,
                objectMapper,
                realOutbox,
                mock(ReadModelVersionService.class)
        );

        service.publish(
                change("BRANDING", 0L, "update site name"),
                List.of(KEY),
                () -> null
        );

        ArgumentCaptor<PlatformEventOutboxEntity> eventCaptor =
                ArgumentCaptor.forClass(PlatformEventOutboxEntity.class);
        verify(outboxMapper).insert(eventCaptor.capture());
        PlatformEventOutboxEntity event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(operator().getUserId());
        assertThat(event.getUserUuid()).isEqualTo(operator().getUserUuid());
        assertThat(objectMapper.readTree(event.getPayloadJson()).path("userUuid").asText())
                .isEqualTo(operator().getUserUuid());
    }

    @Test
    void failedMutationDoesNotCreateFakeVersionOrNotification() {
        Fixture fixture = fixture(
                0L,
                List.of(config(KEY, "Lumira", SystemConfigVersioningService.SENSITIVITY_NONE)),
                List.of(config(KEY, "never-read", SystemConfigVersioningService.SENSITIVITY_NONE))
        );

        assertThatThrownBy(() -> fixture.service.publish(
                change("BRANDING", 0L, "failed publish"),
                List.of(KEY),
                () -> {
                    throw new IllegalStateException("simulated transaction failure");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(fixture.updates)
                .noneMatch(sql -> sql.toLowerCase().contains("insert into sys_config_version ("));
        verify(fixture.outbox, never()).record(anyString(), anyString(), any(), anyString(), any());
        verify(fixture.readModel, never()).bump(anyString(), anyString(), anyString());
    }

    @Test
    void expectedVersionConflictStopsMutationBeforeSnapshotPublish() {
        MyBatisQueryOperations operations = mock(MyBatisQueryOperations.class);
        PlatformEventOutboxService outbox = mock(PlatformEventOutboxService.class);
        ReadModelVersionService readModel = mock(ReadModelVersionService.class);
        List<String> updates = new ArrayList<>();
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(0, String.class));
            return 1;
        }).when(operations).update(anyString(), any(Object[].class));
        doAnswer(invocation -> 3L)
                .when(operations)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));
        SystemConfigVersioningService service = new SystemConfigVersioningService(
                operations, new ObjectMapper(), outbox, readModel
        );
        AtomicBoolean mutationCalled = new AtomicBoolean();

        assertThatThrownBy(() -> service.publish(
                change("BRANDING", 2L, "stale update"),
                List.of(KEY),
                () -> {
                    mutationCalled.set(true);
                    return null;
                }
        )).isInstanceOf(BizException.class);

        assertThat(mutationCalled).isFalse();
        assertThat(updates).noneMatch(sql -> sql.toLowerCase().contains("insert into sys_config_version ("));
        verify(outbox, never()).record(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void rollbackIsTransactionalAndWritesExplicitUpdatedAtDuringSnapshotApply() throws Exception {
        SystemConfigVersioningService.StoredConfig before = config(KEY, "Lumira", SystemConfigVersioningService.SENSITIVITY_NONE);
        SystemConfigVersioningService.StoredConfig target = config(KEY, "Lumira 1.0", SystemConfigVersioningService.SENSITIVITY_NONE);
        Fixture fixture = fixture(1L, List.of(before), List.of(target));
        fixture.versionRows = new ArrayList<>();
        fixture.versionRows.add(versionRow(1L, 1L, fixture.objectMapper.writeValueAsString(List.of(target)), "UPDATE"));
        fixture.versionRows.add(versionRow(2L, 2L, null, "ROLLBACK"));

        SystemConfigVersioningService.VersionDetail detail = fixture.service.rollback(
                new SystemConfigVersioningService.ChangeRequest(
                        "BRANDING",
                        SystemConfigVersioningService.DOMAIN_PLATFORM,
                        1L,
                        "restore previous branding",
                        operator(),
                        SystemConfigVersioningService.CHANGE_ROLLBACK,
                        1L
                ),
                1L,
                1L
        );

        assertThat(detail.version().versionNo()).isEqualTo(2L);
        assertThat(fixture.updates)
                .anyMatch(sql -> {
                    String normalized = sql.toLowerCase();
                    return normalized.contains("insert into sys_config")
                            && normalized.contains("created_at")
                            && normalized.contains("updated_at")
                            && normalized.contains("updated_at = values(updated_at)");
                });
        verify(fixture.outbox).record(
                eq(PlatformEventTypes.SOURCE_SYSTEM),
                eq("SYSTEM_CONFIG_VERSION_PUBLISHED"),
                eq(100L),
                eq("config-version:BRANDING:PLATFORM:2"),
                any()
        );
        verify(fixture.readModel).bump("platform", "configuration", "config-version:BRANDING:PLATFORM:2");
        assertThat(SystemConfigVersioningService.class
                .getMethod(
                        "rollback",
                        SystemConfigVersioningService.ChangeRequest.class,
                        long.class,
                        long.class
                )
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void historyDetailMasksSecretDiffAndHistoryContainsNoValues() throws Exception {
        MyBatisQueryOperations operations = mock(MyBatisQueryOperations.class);
        PlatformEventOutboxService outbox = mock(PlatformEventOutboxService.class);
        ReadModelVersionService readModel = mock(ReadModelVersionService.class);
        SystemConfigVersioningService service = new SystemConfigVersioningService(
                operations, new ObjectMapper(), outbox, readModel
        );
        Map<String, Object> version = versionRow(7L, 4L, null, "UPDATE");
        doAnswer(invocation -> List.of(version))
                .when(operations)
                .queryForList(anyString(), any(Object[].class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class).toLowerCase();
            if (sql.contains("sys_config_version_item")) {
                @SuppressWarnings("unchecked")
                RowMapper<SystemConfigVersioningService.DiffItem> mapper =
                        (RowMapper<SystemConfigVersioningService.DiffItem>) invocation.getArgument(1);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("configKey", "verification.wechat-login.app-secret");
                item.put("valueType", "STRING");
                item.put("sensitivity", "SECRET");
                item.put("changeType", "UPDATE");
                item.put("beforePresent", 1);
                item.put("afterPresent", 1);
                item.put("valueBefore", "encrypted-before-plaintext-must-not-escape");
                item.put("valueAfter", "encrypted-after-plaintext-must-not-escape");
                return List.of(mapper.mapRow(new SqlRow(item), 0));
            }
            @SuppressWarnings("unchecked")
            RowMapper<SystemConfigVersioningService.VersionSummary> mapper =
                    (RowMapper<SystemConfigVersioningService.VersionSummary>) invocation.getArgument(1);
            return List.of(mapper.mapRow(new SqlRow(version), 0));
        }).when(operations).query(anyString(), any(RowMapper.class), any(Object[].class));
        doAnswer(invocation -> 1L)
                .when(operations)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        SystemConfigVersioningService.VersionDetail detail = service.detail("VERIFICATION", "PLATFORM", 4L);
        List<SystemConfigVersioningService.VersionSummary> history = service.history("VERIFICATION", "PLATFORM", 1, 20);

        assertThat(detail.diff()).singleElement().satisfies(item -> {
            assertThat(item.beforeValue()).isEqualTo("******");
            assertThat(item.afterValue()).isEqualTo("******");
            assertThat(item.beforeValue()).doesNotContain("plaintext");
        });
        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.versionNo()).isEqualTo(4L);
            assertThat(item.reason()).doesNotContain("encrypted-after-plaintext-must-not-escape");
        });
    }

    @Test
    void immutableSecretSnapshotEncryptsLegacyValuesBeforeHistoryPersistence() {
        com.lumira.common.security.FieldCryptoService crypto = mock(com.lumira.common.security.FieldCryptoService.class);
        org.mockito.Mockito.when(crypto.encrypt("legacy-secret")).thenReturn("v1:legacy-secret");
        org.mockito.Mockito.when(crypto.encrypt("new-secret")).thenReturn("v1:new-secret");
        Fixture fixture = fixture(
                0L,
                List.of(config("smtp.password", "legacy-secret", SystemConfigVersioningService.SENSITIVITY_SECRET)),
                List.of(config("smtp.password", "new-secret", SystemConfigVersioningService.SENSITIVITY_SECRET)),
                crypto
        );

        fixture.service.publish(
                change("SMTP", 0L, "rotate smtp password"),
                List.of("smtp.password"),
                () -> null
        );

        verify(crypto).encrypt("legacy-secret");
        verify(crypto).encrypt("new-secret");
    }

    @Test
    void rejectsCoreSecretsButAllowsSafeSecurityPolicyTtl() {
        SystemConfigVersioningService service = new SystemConfigVersioningService(
                mock(MyBatisQueryOperations.class), new ObjectMapper(),
                mock(PlatformEventOutboxService.class), mock(ReadModelVersionService.class)
        );

        assertThatThrownBy(() -> service.validateGovernedKey("security.jwt-secret"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.validateGovernedKey("payment.oauth.client-secret"))
                .isInstanceOf(BizException.class);
        service.validateGovernedKey("security.access-token-expire-seconds");
    }

    private Fixture fixture(long currentVersion, List<SystemConfigVersioningService.StoredConfig> before,
                            List<SystemConfigVersioningService.StoredConfig> after) {
        return fixture(currentVersion, before, after, null);
    }

    private Fixture fixture(long currentVersion, List<SystemConfigVersioningService.StoredConfig> before,
                            List<SystemConfigVersioningService.StoredConfig> after,
                            com.lumira.common.security.FieldCryptoService fieldCryptoService) {
        MyBatisQueryOperations operations = mock(MyBatisQueryOperations.class);
        PlatformEventOutboxService outbox = mock(PlatformEventOutboxService.class);
        ReadModelVersionService readModel = mock(ReadModelVersionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> updates = new ArrayList<>();
        AtomicInteger snapshotReads = new AtomicInteger();
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(0, String.class));
            return 1;
        }).when(operations).update(anyString(), any(Object[].class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class).toLowerCase();
            if (sql.contains("current_version_no")) {
                return currentVersion;
            }
            return 42L;
        }).when(operations).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class).toLowerCase();
            if (sql.contains("sys_config_version_item")) {
                return List.of();
            }
            int read = snapshotReads.getAndIncrement();
            return read == 0 ? before : after;
        }).when(operations).query(anyString(), any(RowMapper.class), any(Object[].class));
        SystemConfigVersioningService service = new SystemConfigVersioningService(
                operations, objectMapper, outbox, readModel, fieldCryptoService, null
        );
        Fixture fixture = new Fixture(service, operations, outbox, readModel, objectMapper, updates, before, after);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Object lastArgument = args.length == 0 ? null : args[args.length - 1];
            long requestedVersion = lastArgument instanceof Number number ? number.longValue() : -1L;
            return fixture.versionRows.stream()
                    .filter(row -> ((Number) row.get("versionNo")).longValue() == requestedVersion)
                    .toList();
        }).when(operations).queryForList(anyString(), any(Object[].class));
        return fixture;
    }

    private static SystemConfigVersioningService.ChangeRequest change(String group, Long expected, String reason) {
        return new SystemConfigVersioningService.ChangeRequest(
                group,
                SystemConfigVersioningService.DOMAIN_PLATFORM,
                expected,
                reason,
                operator()
        );
    }

    private static CurrentUser operator() {
        CurrentUser user = new CurrentUser(100L, "admin", "session", 1, true, Set.of("system:config:update"));
        user.setUserUuid("00000000-0000-0000-0000-000000000100");
        return user;
    }

    private static SystemConfigVersioningService.StoredConfig config(String key, String value, String sensitivity) {
        return new SystemConfigVersioningService.StoredConfig(
                key, key, value, "PLATFORM", 0, "remark", "STRING", sensitivity,
                "DYNAMIC", key, "lumira-system"
        );
    }

    private static Map<String, Object> versionRow(long id, long versionNo, String snapshotJson, String changeType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("groupCode", "BRANDING");
        row.put("domainCode", "PLATFORM");
        row.put("versionNo", versionNo);
        row.put("changeType", changeType);
        row.put("reason", "test reason");
        row.put("operatorId", 100L);
        row.put("operatorUuid", "00000000-0000-0000-0000-000000000100");
        row.put("operatorName", "admin");
        row.put("expectedVersionNo", versionNo - 1);
        row.put("sourceVersionNo", changeType.equals("ROLLBACK") ? 1L : null);
        row.put("createdAt", LocalDateTime.of(2026, 8, 7, 0, 0));
        if (snapshotJson != null) {
            row.put("snapshotJson", snapshotJson);
        }
        return row;
    }

    private static final class Fixture {
        private final SystemConfigVersioningService service;
        private final MyBatisQueryOperations operations;
        private final PlatformEventOutboxService outbox;
        private final ReadModelVersionService readModel;
        private final ObjectMapper objectMapper;
        private final List<String> updates;
        private final List<SystemConfigVersioningService.StoredConfig> before;
        private final List<SystemConfigVersioningService.StoredConfig> after;
        private List<Map<String, Object>> versionRows = new ArrayList<>();

        private Fixture(SystemConfigVersioningService service,
                        MyBatisQueryOperations operations,
                        PlatformEventOutboxService outbox,
                        ReadModelVersionService readModel,
                        ObjectMapper objectMapper,
                        List<String> updates,
                        List<SystemConfigVersioningService.StoredConfig> before,
                        List<SystemConfigVersioningService.StoredConfig> after) {
            this.service = service;
            this.operations = operations;
            this.outbox = outbox;
            this.readModel = readModel;
            this.objectMapper = objectMapper;
            this.updates = updates;
            this.before = before;
            this.after = after;
            // The default fixture uses the read-only snapshot path. Rollback tests replace
            // queryForList below because they also need a version row and serialized target.
        }
    }
}
