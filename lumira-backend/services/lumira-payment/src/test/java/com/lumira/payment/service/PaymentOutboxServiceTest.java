package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxServiceTest {

    @Test
    void recordShouldRejectNonPaymentSourceType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "plugin", "payment.order.created", "order:ORD-1", Map.of("userUuid", "user-uuid-9"))
        );
    }

    @Test
    void recordShouldRejectInvalidUserIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(0L, "payment", "payment.order.created", "order:ORD-1", List.of())
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectBlankEventTypeBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", " ", "order:ORD-1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectOversizedEventKeyBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "k".repeat(129), Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectUntrustedEventTypeBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created\nspoofed", "order:ORD-1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectOversizedPayloadBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "order:ORD-1", Map.of("body", "x".repeat(256 * 1024)))
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldNotInventAuditUserForAnonymousPaymentEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxService service = service(jdbcTemplate);

        service.record(null, "payment", "payment.order.created", "order:ORD-1", List.of());

        verify(jdbcTemplate).update(
                anyString(),
                eq(null),
                eq(null),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalStateException.class, () ->
                service.record(null, "payment", "payment.order.created", "order:ORD-1", List.of())
        );
    }

    @Test
    void recordShouldRejectMissingUserUuidWhenUserIdIsPresent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "order:ORD-1", List.of())
        );

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectUserUuidMismatchWhenDatabaseCanResolveUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = trustedSystemApi();
        PaymentOutboxService service = service(jdbcTemplate, systemInternalApi);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "order:ORD-1", Map.of("userUuid", "user-uuid-other"))
        );

        verify(systemInternalApi).findTargetUserUuidById(9L);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectUserUuidWhenDatabaseCannotVerifyUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PaymentOutboxService service = service(jdbcTemplate, systemInternalApi);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "order:ORD-1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(systemInternalApi).findTargetUserUuidById(9L);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordShouldRejectDisabledUserEvenWhenUserUuidMatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PaymentOutboxService service = service(jdbcTemplate, systemInternalApi);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(9L, "payment", "payment.order.created", "order:ORD-1", Map.of("userUuid", "user-uuid-9"))
        );

        verify(systemInternalApi).findTargetUserUuidById(9L);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void dispatchPendingClaimsAndMarksDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow row = outboxRow(10L, "PENDING", 0);
        row.setClaimToken("claim-10");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isEqualTo(1);
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(contains("set t.status = ?,"), eq("payment"), eq("PENDING"), eq("FAILED"), any(), eq("DISPATCHING"), any(), eq(50), eq("DISPATCHING"), anyString(), anyString(), any(), any(), eq("payment"));
        verify(jdbcTemplate).update(contains("claim_token = ?"), eq("DELIVERED"), any(), eq(9L), eq("user-uuid-9"), eq(10L), eq("payment"),
                eq("payment.order.created"), eq("order:ORD-1"), eq("DISPATCHING"), anyString(), eq(9L), eq(9L), eq("user-uuid-9"),
                eq(0), eq(0));
    }

    @Test
    void dispatchPendingShouldRejectDeliveredWhenClaimWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow row = outboxRow(17L, "PENDING", 0);
        row.setClaimToken("claim-17");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0, 1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Payment outbox changed, please retry"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(17L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () -> service.dispatchPending(dispatcher, 201));

        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow row = outboxRow(11L, "FAILED", 7);
        row.setClaimToken("claim-11");
        row.setStatus("DISPATCHING");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(rowToFail -> {
            throw new RuntimeException("boom");
        }, 50);

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
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxRow row = outboxRow(13L, "DISPATCHING", 0);
        row.setClaimToken("claim-13");
        row.setPayloadJson("x".repeat(256 * 1024 + 1));
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Payment outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(13L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxRow row = outboxRow(14L, "DISPATCHING", 0);
        row.setClaimToken("claim-14");
        row.setPayloadJson("{}");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Payment outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(14L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxRow row = outboxRow(15L, "DISPATCHING", 0);
        row.setClaimToken("claim-15");
        row.setPayloadJson("{\"userUuid\":\"user-uuid-other\"}");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Payment outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(15L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
        PaymentOutboxRow row = outboxRow(16L, "DISPATCHING", 0);
        row.setClaimToken("claim-16");
        row.setUserUuid("user-uuid-other");
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq("payment"),
                anyString()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq("FAILED"),
                eq(1),
                any(),
                eq("Payment outbox row is invalid"),
                any(),
                eq(9L),
                eq("user-uuid-other"),
                eq(16L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
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
    void dispatchStateWritesShouldBindEventAndUserIdentity() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/payment/service/PaymentOutboxService.java"));

        assertThat(source)
                .contains("and event_type = ?")
                .contains("and event_key = ?")
                .contains("((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))")
                .contains("normalizeUserUuidOrNull(row.getUserUuid())");
    }


    @Test
    void replayResetsDeadLetterBeforeDispatchingAgain() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow deadLetter = outboxRow(12L, "DEAD_LETTER", 8);
        doReturn(deadLetter).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L),
                eq("payment")
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        verify(jdbcTemplate).update(
                contains("event_key = ?"),
                eq("PENDING"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(12L),
                eq("payment"),
                eq("payment.order.created"),
                eq("order:ORD-1"),
                eq("DEAD_LETTER"),
                eq(8),
                eq(8),
                eq(9L),
                eq(9L),
                eq("user-uuid-9")
        );
        verify(dispatcher).dispatch(deadLetter);
        assertThat(deadLetter.getStatus()).isEqualTo("DISPATCHING");
        assertThat(deadLetter.getClaimToken()).isNotBlank();
    }

    @Test
    void replayShouldNotDispatchWhenResetBoundaryMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow deadLetter = outboxRow(12L, "DEAD_LETTER", 8);
        doReturn(deadLetter).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L),
                eq("payment")
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isFalse();
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void replayShouldRejectInvalidIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(0L, dispatcher);

        assertThat(replayed).isFalse();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(BeanPropertyRowMapper.class), any(), any());
        verify(dispatcher, never()).dispatch(any());
    }


    @Test
    void backlogMetrics_shouldReuseAggregatedSnapshot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PaymentOutboxService service = service(jdbcTemplate);

        assertThat(service.pendingBacklog()).isEqualTo(3L);
        assertThat(service.failedBacklog()).isEqualTo(2L);
        assertThat(service.deadLetterCount()).isEqualTo(1L);
        assertThat(service.dispatchableBacklog()).isEqualTo(4L);
        verify(jdbcTemplate, times(1)).queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any());
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void snapshotShouldReadOutboxMetricsTogether() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PaymentOutboxService service = service(jdbcTemplate);

        PaymentOutboxService.OutboxMetricsSnapshot snapshot = service.snapshot();

        assertThat(snapshot.pendingBacklog()).isEqualTo(3L);
        assertThat(snapshot.failedBacklog()).isEqualTo(2L);
        assertThat(snapshot.deadLetterCount()).isEqualTo(1L);
        assertThat(snapshot.dispatchableBacklog()).isEqualTo(4L);
    }

    @Test
    void snapshotShouldReuseCachedValueWithinTtl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 3L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L,
                        "dispatchable_backlog", 4L
                )));
        PaymentOutboxService service = service(jdbcTemplate);

        var first = service.snapshot();
        var second = service.snapshot();

        assertThat(second).isSameAs(first);
        verify(jdbcTemplate).queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any());
    }

    private PaymentOutboxRow outboxRow(Long id, String status, int retryCount) {
        PaymentOutboxRow row = new PaymentOutboxRow();
        row.setId(id);
        row.setUserId(9L);
        row.setUserUuid("user-uuid-9");
        row.setSourceType("payment");
        row.setEventType("payment.order.created");
        row.setEventKey("order:ORD-1");
        row.setPayloadJson("{\"userUuid\":\"user-uuid-9\"}");
        row.setStatus(status);
        row.setRetryCount(retryCount);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(9L);
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        row.setDeleted(0);
        return row;
    }

    private PaymentOutboxService service(JdbcTemplate jdbcTemplate) {
        return service(jdbcTemplate, trustedSystemApi());
    }

    private PaymentOutboxService service(JdbcTemplate jdbcTemplate, SystemInternalApi systemInternalApi) {
        return new PaymentOutboxService(jdbcTemplate, new ObjectMapper(), systemInternalApi);
    }

    private SystemInternalApi trustedSystemApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findTargetUserUuidById(9L)).thenReturn("user-uuid-9");
        when(systemInternalApi.findUserIdentityById(9L)).thenReturn(new SystemUserSnapshotDTO(
                9L,
                "user-uuid-9",
                "payment-test-user",
                null,
                "ENABLED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return systemInternalApi;
    }
}
