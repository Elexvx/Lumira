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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEventOutboxRelayServiceTest {

    @Test
    void dispatchPendingShouldUseBatchClaimSqlAndFileOwnerConstraints() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        PlatformEventOutboxEntity row = outboxRow(15L, PlatformEventOutboxService.STATUS_RECORDED, 0);
        row.setDispatchStatus(PlatformEventOutboxService.STATUS_DISPATCHING);
        when(jdbcTemplate.query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        )).thenReturn(List.of(row));
        when(jdbcTemplate.update(updateSql.capture(), any(Object[].class))).thenReturn(1, 1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, PlatformEventOutboxService.MAX_DISPATCH_LIMIT);

        assertThat(delivered).isEqualTo(1);
        assertThat(updateSql.getAllValues().get(0).toLowerCase())
                .contains("update platform_event_outbox t")
                .contains("from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)")
                .contains("where deleted = 0")
                .contains("source_type = ?")
                .contains("dispatch_status = ?")
                .contains("next_retry_at is null or next_retry_at <= ?")
                .contains("order by created_at asc, id asc");
        verify(jdbcTemplate).update(
                contains("update platform_event_outbox t"),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED),
                any(LocalDateTime.class),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                any(LocalDateTime.class),
                eq(200),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        verify(jdbcTemplate).query(
                contains("where deleted = 0"),
                any(BeanPropertyRowMapper.class),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                anyString()
        );
    }

    @Test
    void dispatchPendingClaimsAndMarksDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity row = outboxRow(10L, PlatformEventOutboxService.STATUS_RECORDED, 0);
        row.setDispatchStatus(PlatformEventOutboxService.STATUS_DISPATCHING);
        doReturn(List.of(row)).when(jdbcTemplate).query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isEqualTo(1);
        verify(dispatcher).dispatch(row);
        verify(jdbcTemplate).update(
                contains("update platform_event_outbox t"),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED),
                any(LocalDateTime.class),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                any(LocalDateTime.class),
                eq(50),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        verify(jdbcTemplate).query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                anyString()
        );
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq(PlatformEventOutboxService.STATUS_DELIVERED),
                any(),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(10L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity row = outboxRow(11L, PlatformEventOutboxService.STATUS_FAILED, 7);
        row.setDispatchStatus(PlatformEventOutboxService.STATUS_DISPATCHING);
        doReturn(List.of(row)).when(jdbcTemplate).query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 1);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(failingDispatcher(), 50);

        assertThat(delivered).isZero();
        verify(jdbcTemplate).update(
                contains("update platform_event_outbox t"),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED),
                any(LocalDateTime.class),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                any(LocalDateTime.class),
                eq(50),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        verify(jdbcTemplate).query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                anyString()
        );
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq(PlatformEventOutboxService.STATUS_DEAD_LETTER),
                eq(8),
                eq(null),
                eq("boom"),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(11L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
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
        PlatformEventOutboxEntity row = outboxRow(13L, PlatformEventOutboxService.STATUS_DISPATCHING, 0);
        row.setPayloadJson("x".repeat(256 * 1024 + 1));
        doReturn(List.of(row)).when(jdbcTemplate).query(
                contains("claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                any(),
                any()
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        int delivered = service.dispatchPending(dispatcher, 50);

        assertThat(delivered).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(jdbcTemplate).update(
                contains("claim_token = ?"),
                eq(PlatformEventOutboxService.STATUS_FAILED),
                eq(1),
                any(LocalDateTime.class),
                eq("File outbox row is invalid"),
                any(LocalDateTime.class),
                eq(9L),
                eq("user-uuid-9"),
                eq(13L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                eq(PlatformEventOutboxService.STATUS_DISPATCHING),
                anyString(),
                eq(9L),
                eq(9L),
                eq("user-uuid-9"),
                eq(0),
                eq(0)
        );
    }

    @Test
    void dispatchStateWritesShouldBindEventAndUserIdentity() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/file/event/PlatformEventOutboxService.java"));

        assertThat(source)
                .contains("and event_type = ?")
                .contains("and event_key = ?")
                .contains("((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))")
                .contains("normalizeUserUuidOrNull(row.getUserUuid())");
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
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        verify(jdbcTemplate).update(
                contains("event_key = ?"),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(12L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                eq(PlatformEventOutboxService.STATUS_DEAD_LETTER),
                eq(9L),
                eq(9L),
                eq("user-uuid-9")
        );
        verify(dispatcher).dispatch(deadLetter);
    }

    @Test
    void replayShouldNormalizeUserUuidWhenResettingBoundary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity deadLetter = outboxRow(12L, PlatformEventOutboxService.STATUS_DEAD_LETTER, 8);
        deadLetter.setUserUuid("  user-uuid-9  ");
        doReturn(deadLetter).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isTrue();
        verify(jdbcTemplate).update(
                contains("dispatch_status = ?"),
                eq(PlatformEventOutboxService.STATUS_RECORDED),
                any(),
                eq(9L),
                eq("user-uuid-9"),
                eq(12L),
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_OBJECT_UPLOADED),
                eq("FILE_OBJECT_UPLOADED:1001:file.object:3001"),
                eq(PlatformEventOutboxService.STATUS_DEAD_LETTER),
                eq(9L),
                eq(9L),
                eq("user-uuid-9")
        );
    }

    @Test
    void replayShouldNotDispatchWhenResetBoundaryMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxEntity deadLetter = outboxRow(12L, PlatformEventOutboxService.STATUS_DEAD_LETTER, 8);
        doReturn(deadLetter).when(jdbcTemplate).queryForObject(
                anyString(),
                any(BeanPropertyRowMapper.class),
                eq(12L),
                eq(FilePlatformEventTypes.SOURCE_FILE)
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(12L, dispatcher);

        assertThat(replayed).isFalse();
        verify(dispatcher, never()).dispatch(any());
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
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);
        service.replay(33L, dispatcher);

        assertThat(queryForObjectSql.getValue().toLowerCase())
                .contains("from platform_event_outbox")
                .contains("where id = ? and deleted = 0 and source_type = ?")
                .contains("limit 1");
    }

    @Test
    void replayShouldRejectInvalidIdBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = service(jdbcTemplate);

        boolean replayed = service.replay(0L, dispatcher);

        assertThat(replayed).isFalse();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(BeanPropertyRowMapper.class), any(), any());
        verify(dispatcher, never()).dispatch(any());
    }


    private PlatformEventOutboxService service(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.queryForObject(contains("select uuid"), eq(String.class), eq(9L))).thenReturn("user-uuid-9");
        return new PlatformEventOutboxService(new ObjectMapper(), mock(FilePlatformEventOutboxMapper.class), jdbcTemplate);
    }

    private PlatformEventOutboxEntity outboxRow(Long id, String status, int retryCount) {
        PlatformEventOutboxEntity row = new PlatformEventOutboxEntity();
        row.setId(id);
        row.setUserId(9L);
        row.setUserUuid("user-uuid-9");
        row.setSourceType(FilePlatformEventTypes.SOURCE_FILE);
        row.setEventType(FilePlatformEventTypes.FILE_OBJECT_UPLOADED);
        row.setEventKey("FILE_OBJECT_UPLOADED:1001:file.object:3001");
        row.setPayloadJson("{\"userUuid\":\"user-uuid-9\"}");
        row.setDispatchStatus(status);
        row.setRetryCount(retryCount);
        row.setClaimToken("claim-" + id);
        row.setClaimExpiresAt(LocalDateTime.now().plusMinutes(15));
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(9L);
        row.setUpdatedByUuid("user-uuid-9");
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
