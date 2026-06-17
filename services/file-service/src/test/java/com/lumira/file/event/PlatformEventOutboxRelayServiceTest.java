package com.lumira.file.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEventOutboxRelayServiceTest {

    @Test
    void dispatchPendingShouldUseBoundedQueueQueryAndFileOwnerConstraints() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
        PlatformEventOutboxEntity row = outboxRow(15L, PlatformEventOutboxService.STATUS_RECORDED, 0);
        when(jdbcTemplate.query(
                querySql.capture(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(row));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 500);

        assertThat(delivered).isEqualTo(1);
        assertThat(querySql.getValue().toLowerCase())
                .contains("from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)")
                .contains("where deleted = 0")
                .contains("source_type = ?")
                .contains("dispatch_status = ?")
                .contains("next_retry_at is null or next_retry_at <= ?")
                .contains("order by created_at asc, id asc");
        verify(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED),
                any(LocalDateTime.class),
                eq(200)
        );
    }

    @Test
    void dispatchPendingClaimsAndMarksDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity row = outboxRow(10L, PlatformEventOutboxService.STATUS_RECORDED, 0);
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
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isEqualTo(1);
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(
                contains("and dispatch_status = ?"),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                any(),
                eq(9L),
                eq(10L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_RECORDED)
        );
        verify(jdbcTemplate).update(
                contains("last_error = null"),
                eq(PlatformEventOutboxService.STATUS_DELIVERED),
                any(),
                any(),
                eq(9L),
                eq(10L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity row = outboxRow(11L, PlatformEventOutboxService.STATUS_FAILED, 7);
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
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(failingDispatcher(), 50);

        assertThat(delivered).isZero();
        verify(jdbcTemplate).update(
                contains("and dispatch_status = ?"),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                any(),
                eq(9L),
                eq(11L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_FAILED)
        );
        verify(jdbcTemplate).update(
                contains("retry_count = ?"),
                eq(PlatformEventOutboxService.STATUS_DEAD_LETTER),
                eq(8),
                eq(null),
                eq("boom"),
                any(),
                eq(9L),
                eq(11L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
    }

    @Test
    void replayResetsEventBeforeDispatchingAgain() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity deadLetter = outboxRow(12L, PlatformEventOutboxService.STATUS_DEAD_LETTER, 8);
        doReturn(deadLetter).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        doReturn(List.of(outboxRow(99L, PlatformEventOutboxService.STATUS_RECORDED, 0), outboxRow(12L, PlatformEventOutboxService.STATUS_RECORDED, 0))).when(jdbcTemplate).query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        verify(jdbcTemplate).update(contains("retry_count = 0"), eq(PlatformEventOutboxService.STATUS_RECORDED), any(), eq(9L), eq(12L), eq(FilePlatformEventTypes.SOURCE_FILE));
        verify(dispatcher).dispatch(deadLetter);
    }

    @Test
    void replayShouldReadBySourceAndDeletedConstraint() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity deadLetter = outboxRow(33L, PlatformEventOutboxService.STATUS_DEAD_LETTER, 3);
        ArgumentCaptor<String> queryForObjectSql = ArgumentCaptor.forClass(String.class);

        when(jdbcTemplate.queryForObject(
                queryForObjectSql.capture(),
                any(BeanPropertyRowMapper.class),
                eq(33L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        )).thenReturn(deadLetter);
        when(jdbcTemplate.query(
                anyString(),
                any(BeanPropertyRowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(outboxRow(33L, PlatformEventOutboxService.STATUS_RECORDED, 0)));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);
        service.replay(33L, dispatcher);

        assertThat(queryForObjectSql.getValue().toLowerCase())
                .contains("from platform_event_outbox")
                .contains("where id = ? and deleted = 0 and source_type = ?")
                .contains("limit 1");
    }

    private PlatformEventOutboxService service(JdbcTemplate jdbcTemplate) {
        return new PlatformEventOutboxService(new ObjectMapper(), mock(FilePlatformEventOutboxMapper.class), jdbcTemplate);
    }

    private PlatformEventOutboxEntity outboxRow(Long id, String status, int retryCount) {
        PlatformEventOutboxEntity row = new PlatformEventOutboxEntity();
        row.setId(id);
        row.setTenantId(1001L);
        row.setUserId(9L);
        row.setSourceType(FilePlatformEventTypes.SOURCE_FILE);
        row.setEventType(FilePlatformEventTypes.FILE_OBJECT_UPLOADED);
        row.setEventKey("FILE_OBJECT_UPLOADED:1001:file.object:3001");
        row.setPayloadJson("{}");
        row.setDispatchStatus(status);
        row.setRetryCount(retryCount);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(9L);
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        row.setDeleted(0);
        return row;
    }

    private FileOutboxDispatcher failingDispatcher() {
        return row -> {
            throw new RuntimeException("boom");
        };
    }
}
