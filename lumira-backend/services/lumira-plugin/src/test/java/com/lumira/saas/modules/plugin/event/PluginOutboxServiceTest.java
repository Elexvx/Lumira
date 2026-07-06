package com.lumira.saas.modules.plugin.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

class PluginOutboxServiceTest {

    @Test
    void recordShouldRejectInvalidUserIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(-1L, "PLUGIN_INSTALLED", "plugin:1", List.of())
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectBlankEventTypeBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, " ", "plugin:1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectOversizedEventKeyBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "k".repeat(129), Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectUntrustedEventKeyBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "../plugin:1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectOversizedPayloadBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "plugin:1", Map.of("body", "x".repeat(256 * 1024)))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldNotInventAuditUserForAnonymousPluginEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        service.record(null, "PLUGIN_INSTALLED", "plugin:1", List.of());

        verify(jdbcTemplate).update(
                anyString(),
                eq(null),
                eq(null),
                eq("PLUGIN_INSTALLED"),
                eq("plugin:1"),
                anyString(),
                any(),
                eq(null),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void recordShouldRejectWhenInsertMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalStateException.class, () ->
                service.record(null, "PLUGIN_INSTALLED", "plugin:1", List.of())
        );
    }

    @Test
    void recordShouldRejectMissingUserUuidWhenUserIdIsPresent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "plugin:1", List.of())
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectUserUuidMismatchWhenDatabaseCanResolveUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "plugin:1", Map.of("userUuid", "user-uuid-other"))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectUserUuidWhenDatabaseCannotVerifyUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "plugin:1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void recordShouldRejectDisabledUserEvenWhenUserUuidMatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "PLUGIN_INSTALLED", "plugin:1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate).queryForObject(contains("status = 'ENABLED'"), eq(String.class), eq(9L));
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void dispatchPendingClaimsAndMarksDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(10L, "PENDING", 0);
        row.setClaimToken("claim-10");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isEqualTo(1);
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(contains("set t.status = ?,"), eq("PENDING"), eq("FAILED"), any(), eq("DISPATCHING"), any(), eq(50), eq("DISPATCHING"), anyString(), anyString(), any(), any());
        verify(jdbcTemplate).update(contains("claim_token = ?"),
                eq("DELIVERED"), any(), eq(9L), eq("user-uuid-9"), eq(10L),
                eq("plugin.enabled"), eq("plugin:sms"), eq("DISPATCHING"), anyString(),
                eq(9L), eq(9L), eq("user-uuid-9"), eq(0), eq(0));
    }

    @Test
    void dispatchPendingShouldRejectDeliveredWhenClaimWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(17L, "PENDING", 0);
        row.setClaimToken("claim-17");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0, 1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Plugin outbox changed, please retry"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(17L),
                eq("plugin.enabled"),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void dispatchPendingShouldRejectInvalidLimitBeforeClaiming() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.dispatchPending(dispatcher, 0));
        assertThrows(IllegalArgumentException.class, () -> service.dispatchPending(dispatcher, 201));

        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(11L, "FAILED", 7);
        row.setClaimToken("claim-11");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = failedDispatcher();
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("DEAD_LETTER"),
                eq(8),
                eq(null),
                eq("boom"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(11L),
                eq("plugin.enabled"),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(7),
                eq(7)
        );
    }

    @Test
    void dispatchPendingShouldRejectUntrustedClaimedRowBeforeDispatcher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(13L, "DISPATCHING", 0);
        row.setEventType(" ");
        row.setClaimToken("claim-13");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Plugin outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(13L),
                eq(" "),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void dispatchPendingShouldRejectHumanRowMissingUserUuidBeforeDispatcher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(14L, "DISPATCHING", 0);
        row.setClaimToken("claim-14");
        row.setPayloadJson("{}");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Plugin outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(14L),
                eq("plugin.enabled"),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void dispatchPendingShouldRejectPayloadUserUuidMismatchBeforeDispatcher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(15L, "DISPATCHING", 0);
        row.setClaimToken("claim-15");
        row.setPayloadJson("{\"userUuid\":\"user-uuid-other\"}");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Plugin outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(15L),
                eq("plugin.enabled"),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void dispatchPendingShouldRejectRowUserUuidMismatchBeforeDispatcher() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(16L, "DISPATCHING", 0);
        row.setClaimToken("claim-16");
        row.setUserUuid("user-uuid-other");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Plugin outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-other"),
                eq(16L),
                eq("plugin.enabled"),
                eq("plugin:sms"),
                eq("DISPATCHING"),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-other"),
                eq(0),
                eq(0)
        );
    }


    @Test
    void replayResetsEventBeforeDispatchingAgain() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(12L, "DEAD_LETTER", 8);
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L)
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        ArgumentCaptor<String> resetSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> resetArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(resetSql.capture(), resetArgs.capture());
        assertThat(resetSql.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("retry_count = 0")
                .contains("event_type = ?")
                .contains("event_key = ?")
                .contains("status = ?")
                .contains("retry_count is null")
                .contains("user_id = ? and user_uuid = ?"));
        assertThat(resetArgs.getAllValues()).anySatisfy(args -> assertThat(args)
                .contains("PENDING", 9L, "user-uuid-9", 12L, "plugin.enabled", "plugin:sms", "DEAD_LETTER", 8));
        verify(dispatcher).dispatch(row);
        assertThat(row.getStatus()).isEqualTo("DISPATCHING");
        assertThat(row.getClaimToken()).isNotBlank();
    }

    @Test
    void recordAndDispatchWritesShouldNotRewriteOutboxIdentity() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/plugin/event/PluginOutboxService.java"));

        assertThat(source)
                .contains("payload_json = case when ((user_id is null and values(user_id) is null and user_uuid is null)")
                .doesNotContain("user_uuid = values(user_uuid),")
                .contains("and event_type = ? and event_key = ? and status = ? and claim_token = ?")
                .contains("((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))");
    }

    @Test
    void replayShouldNotDispatchWhenResetBoundaryMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxRow row = outboxRow(12L, "DEAD_LETTER", 8);
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L)
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isFalse();
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void replayShouldRejectInvalidIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PluginOutboxDispatcher dispatcher = mock(PluginOutboxDispatcher.class);
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThat(service.replay(0L, dispatcher)).isFalse();

        verify(jdbcTemplate, never()).queryForObject(anyString(), any(BeanPropertyRowMapper.class), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void backlogMetrics_shouldReuseAggregatedSnapshot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from plugin_event_outbox"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(java.util.Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        assertThat(service.pendingBacklog()).isEqualTo(3L);
        assertThat(service.failedBacklog()).isEqualTo(2L);
        assertThat(service.deadLetterCount()).isEqualTo(1L);
        assertThat(service.dispatchableBacklog()).isEqualTo(4L);
        verify(jdbcTemplate, times(1)).queryForList(contains("from plugin_event_outbox"), org.mockito.ArgumentMatchers.<Object[]>any());
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void snapshotShouldReadOutboxMetricsTogether() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from plugin_event_outbox"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(java.util.Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        PluginOutboxService.OutboxMetricsSnapshot snapshot = service.snapshot();

        assertThat(snapshot.pendingBacklog()).isEqualTo(3L);
        assertThat(snapshot.failedBacklog()).isEqualTo(2L);
        assertThat(snapshot.deadLetterCount()).isEqualTo(1L);
        assertThat(snapshot.dispatchableBacklog()).isEqualTo(4L);
    }

    @Test
    void snapshotShouldReuseCachedValueWithinTtl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from plugin_event_outbox"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(java.util.Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PluginOutboxService service = new PluginOutboxService(jdbcTemplate, new ObjectMapper());

        var first = service.snapshot();
        var second = service.snapshot();

        assertThat(second).isSameAs(first);
        verify(jdbcTemplate, times(1)).queryForList(contains("from plugin_event_outbox"), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    private PluginOutboxRow outboxRow(Long id, String status, int retryCount) {
        PluginOutboxRow row = new PluginOutboxRow();
        row.setId(id);
        row.setUserId(9L);
        row.setUserUuid("user-uuid-9");
        row.setEventType("plugin.enabled");
        row.setEventKey("plugin:sms");
        row.setPayloadJson("{\"userUuid\":\"user-uuid-9\"}");
        row.setStatus(status);
        row.setRetryCount(retryCount);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(9L);
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        row.setDeleted(0);
        return row;
    }

    private PluginOutboxDispatcher failedDispatcher() {
        return row -> {
            throw new RuntimeException("boom");
        };
    }
}
