package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
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
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
                service.record(1001L, 9L, "plugin", "payment.order.created", "order:1001:ORD-1", List.of())
        );
    }

    @Test
    void dispatchPendingClaimsAndMarksDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow row = outboxRow(10L, "PENDING", 0);
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isEqualTo(1);
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(contains("and source_type = ? and status = ?"), eq("DISPATCHING"), any(), eq(9L), eq(10L), eq("payment"), eq("PENDING"));
        verify(jdbcTemplate).update(contains("last_error_message = null"), eq("DELIVERED"), any(), eq(9L), eq(10L), eq("payment"));
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxRow row = outboxRow(11L, "FAILED", 7);
        doReturn(List.of(row)).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

        int delivered = service.dispatchPending(rowToFail -> {
            throw new RuntimeException("boom");
        }, 50);

        assertThat(delivered).isZero();
        verify(jdbcTemplate).update(contains("and source_type = ? and status = ?"), eq("DISPATCHING"), any(), eq(9L), eq(11L), eq("payment"), eq("FAILED"));
        verify(jdbcTemplate).update(
                contains("retry_count = ?"),
                eq("DEAD_LETTER"),
                eq(8),
                eq(null),
                eq("boom"),
                any(),
                eq(9L),
                eq(11L),
                eq("payment")
        );
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
        doReturn(List.of(outboxRow(99L, "PENDING", 0), outboxRow(12L, "PENDING", 0))).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        verify(jdbcTemplate).update(contains("source_type = ?"), eq("PENDING"), any(), eq(9L), eq(12L), eq("payment"));
        verify(dispatcher).dispatch(deadLetter);
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
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

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
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

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
        PaymentOutboxService service = new PaymentOutboxService(jdbcTemplate, new ObjectMapper());

        var first = service.snapshot();
        var second = service.snapshot();

        assertThat(second).isSameAs(first);
        verify(jdbcTemplate).queryForList(contains("from payment_event_outbox"), (Object) any(), (Object) any());
    }

    private PaymentOutboxRow outboxRow(Long id, String status, int retryCount) {
        PaymentOutboxRow row = new PaymentOutboxRow();
        row.setId(id);
        row.setTenantId(1001L);
        row.setUserId(9L);
        row.setSourceType("payment");
        row.setEventType("payment.order.created");
        row.setEventKey("order:1001:ORD-1");
        row.setPayloadJson("{}");
        row.setStatus(status);
        row.setRetryCount(retryCount);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(9L);
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        row.setDeleted(0);
        return row;
    }
}
