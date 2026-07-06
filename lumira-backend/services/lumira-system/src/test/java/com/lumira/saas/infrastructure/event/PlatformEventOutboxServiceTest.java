package com.lumira.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEventOutboxServiceTest {

    @Test
    void dispatchPendingShouldClaimDispatchAndMarkDelivered() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity event = buildEvent();
        when(mapper.selectList(any())).thenReturn(List.of(event));
        when(mapper.update(any(), any())).thenReturn(1);

        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);
        AtomicInteger dispatchCount = new AtomicInteger();

        int delivered = service.dispatchPending(dispatchedEvent -> {
            assertEquals(event.getId(), dispatchedEvent.getId());
            dispatchCount.incrementAndGet();
        }, 100);

        assertEquals(1, delivered);
        assertEquals(1, dispatchCount.get());
        verify(mapper, times(1)).selectList(any());
        verify(mapper, times(1)).update(any(PlatformEventOutboxEntity.class), any());
        verify(mapper, times(1)).update(isNull(), any());
    }

    @Test
    void recordShouldRejectNonSystemSourceType() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record("MESSAGE", "NOTICE_CREATED", 2001L, "event-key", trustedPayload()));
    }

    @Test
    void recordShouldRejectInvalidUserIdBeforeInsert() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(PlatformEventTypes.SOURCE_SYSTEM, "NOTICE_CREATED", 0L, "event-key", "{}"));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectInvalidEventTypeBeforeInsert() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(PlatformEventTypes.SOURCE_SYSTEM, "notice.created", 2001L, "event-key", trustedPayload()));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectInvalidEventKeyBeforeInsert() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(PlatformEventTypes.SOURCE_SYSTEM, "NOTICE_CREATED", 2001L, "../event-key", trustedPayload()));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectOversizedPayloadBeforeInsert() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(
                        PlatformEventTypes.SOURCE_SYSTEM,
                        "NOTICE_CREATED",
                        2001L,
                        "event-key",
                        Map.of("userUuid", "user-uuid-2001", "data", "x".repeat(70_000))
                ));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectMissingUserUuidWhenUserIdPresent() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(PlatformEventTypes.SOURCE_SYSTEM, "NOTICE_CREATED", 2001L, "event-key", Map.of("noticeId", 9001L)));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectMissingUserUuidEvenWhenDatabaseCanResolveUser() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.userUuid = "user-uuid-2001";
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, queryOperations);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(
                        PlatformEventTypes.SOURCE_SYSTEM,
                        "NOTICE_CREATED",
                        2001L,
                        "event-key",
                        Map.of("noticeId", 9001L)
                ));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
        assertThat(queryOperations.queries).isEmpty();
    }

    @Test
    void recordShouldRejectPayloadUserUuidMismatchWhenDatabaseCanResolveUser() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.userUuid = "user-uuid-2001";
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, queryOperations);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(
                        PlatformEventTypes.SOURCE_SYSTEM,
                        "NOTICE_CREATED",
                        2001L,
                        "event-key",
                        Map.of("userUuid", "user-uuid-other")
                ));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectDisabledUserEvenWhenPayloadUserUuidMatches() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, queryOperations);

        assertThrows(IllegalArgumentException.class, () ->
                service.record(
                        PlatformEventTypes.SOURCE_SYSTEM,
                        "NOTICE_CREATED",
                        2001L,
                        "event-key",
                        trustedPayload()
                ));

        verify(mapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
        assertThat(queryOperations.queries)
                .anyMatch(query -> query.sql.toLowerCase(Locale.ROOT).contains("status = 'enabled'"));
    }

    @Test
    void recordShouldNotInventAuditUserForAnonymousSystemEvent() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        when(mapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        service.record(PlatformEventTypes.SOURCE_SYSTEM, "SYSTEM_HEALTH", null, "health:1", "{}");

        ArgumentCaptor<PlatformEventOutboxEntity> captor = ArgumentCaptor.forClass(PlatformEventOutboxEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getCreatedBy()).isNull();
        assertThat(captor.getValue().getUpdatedBy()).isNull();
    }

    @Test
    void recordShouldRejectWhenInsertMisses() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.userUuid = "user-uuid-2001";
        when(mapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, queryOperations);

        assertThrows(IllegalStateException.class, () ->
                service.record(PlatformEventTypes.SOURCE_SYSTEM, "NOTICE_CREATED", 2001L, "event-key", trustedPayload()));
    }

    @Test
    void dispatchPendingShouldMarkFailedWhenDispatcherThrows() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity event = buildEvent();
        when(mapper.selectList(any())).thenReturn(List.of(event));
        when(mapper.update(any(), any())).thenReturn(1);

        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        int delivered = service.dispatchPending(dispatchedEvent -> {
            throw new IllegalStateException("broker unavailable");
        }, 100);

        assertEquals(0, delivered);
        verify(mapper, times(1)).selectList(any());
        verify(mapper, times(1)).update(any(PlatformEventOutboxEntity.class), any());
        verify(mapper, times(1)).update(isNull(), any());
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity event = buildEvent();
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_FAILED);
        event.setRetryCount(7);
        when(mapper.selectList(any())).thenReturn(List.of(event));
        when(mapper.update(any(), any())).thenReturn(1);

        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        int delivered = service.dispatchPending(dispatchedEvent -> {
            throw new IllegalStateException("broker unavailable");
        }, 100);

        assertEquals(0, delivered);
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(mapper, times(2)).update(any(), wrapperCaptor.capture());
        Map<String, Object> failureParams = wrapperCaptor.getAllValues().get(1).getParamNameValuePairs();
        assertThat(new ArrayList<>(failureParams.values()))
                .contains(PlatformEventOutboxService.STATUS_DEAD_LETTER, 8, "broker unavailable", event.getUpdatedBy());
    }

    @Test
    void dispatchPendingShouldRejectUntrustedClaimedRowBeforeDispatcher() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PlatformEventOutboxEntity event = buildEvent();
        event.setEventType("notice.created");
        queryOperations.listRows = List.of(event);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );
        AtomicInteger dispatchCount = new AtomicInteger();

        int delivered = service.dispatchPending(ignored -> dispatchCount.incrementAndGet(), 10);

        assertThat(delivered).isZero();
        assertThat(dispatchCount).hasValue(0);
        assertThat(queryOperations.updates)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT).contains("set dispatch_status = ?, retry_count = ?"));
    }

    @Test
    void dispatchPendingShouldRejectHumanRowMissingUserUuidBeforeDispatcher() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PlatformEventOutboxEntity event = buildEvent();
        event.setUserUuid(null);
        queryOperations.listRows = List.of(event);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );
        AtomicInteger dispatchCount = new AtomicInteger();

        int delivered = service.dispatchPending(ignored -> dispatchCount.incrementAndGet(), 10);

        assertThat(delivered).isZero();
        assertThat(dispatchCount).hasValue(0);
        assertThat(queryOperations.updates)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT).contains("set dispatch_status = ?, retry_count = ?"));
    }

    @Test
    void dispatchPendingShouldRejectPayloadUserUuidMismatchBeforeDispatcher() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PlatformEventOutboxEntity event = buildEvent();
        event.setPayloadJson("{\"userUuid\":\"user-uuid-other\"}");
        queryOperations.listRows = List.of(event);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );
        AtomicInteger dispatchCount = new AtomicInteger();

        int delivered = service.dispatchPending(ignored -> dispatchCount.incrementAndGet(), 10);

        assertThat(delivered).isZero();
        assertThat(dispatchCount).hasValue(0);
        assertThat(queryOperations.updates)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT).contains("set dispatch_status = ?, retry_count = ?"));
    }

    @Test
    void replayByIdShouldResetAndRedispatch() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity event = buildEvent();
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_DEAD_LETTER);
        event.setRetryCount(8);
        when(mapper.selectOne(any())).thenReturn(event);
        when(mapper.update(any(), any())).thenReturn(1);

        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);
        AtomicInteger dispatchCount = new AtomicInteger();

        boolean replayed = service.replayById(event.getId(), dispatchedEvent -> dispatchCount.incrementAndGet());

        assertThat(replayed).isTrue();
        assertEquals(1, dispatchCount.get());
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(mapper, times(2)).update(isNull(), wrapperCaptor.capture());
        assertThat(new ArrayList<>(wrapperCaptor.getAllValues().get(0).getParamNameValuePairs().values()))
                .contains(PlatformEventOutboxService.STATUS_RECORDED, 0, event.getUpdatedBy());
    }

    @Test
    void dispatchPendingShouldUseOwnerBoundedQueryWhenDirectSqlEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.listRows = List.of(buildEvent());
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );

        int delivered = service.dispatchPending(event -> {
            assertThat(event.getSourceType()).isEqualTo(PlatformEventTypes.SOURCE_SYSTEM);
        }, 500);

        assertThat(delivered).isEqualTo(1);
        assertThat(queryOperations.queries).hasSize(1);
        String listSql = queryOperations.queries.get(0).sql.toLowerCase(Locale.ROOT);
        Object[] listArgs = queryOperations.queries.get(0).args;
        assertThat(listSql).contains("claim_token = ?");
        assertThat(listSql).contains("where deleted = 0");
        assertThat(listSql).contains("source_type = ?");
        assertThat(listSql).contains("order by created_at asc, id asc");
        assertThat(listArgs[0]).isEqualTo(PlatformEventTypes.SOURCE_SYSTEM);

        assertThat(queryOperations.updates).hasSize(2);
        assertThat(queryOperations.updates.get(0).sql.toLowerCase(Locale.ROOT))
                .contains("join (")
                .contains("force index (idx_platform_event_outbox_owner_queue)")
                .contains("claim_token = ?");
        assertThat(queryOperations.updates.get(1).sql.toLowerCase(Locale.ROOT))
                .contains("where deleted = 0 and source_type = ? and id = ?")
                .contains("claim_token")
                .contains("event_type = ?")
                .contains("event_key")
                .contains("user_id = ? and user_uuid = ?")
                .contains("retry_count");
        assertThat(List.of(queryOperations.updates.get(1).args))
                .contains("NOTICE_CREATED", "NOTICE_CREATED:message.notice:9001", 2001L, "user-uuid-2001", 0);
    }

    @Test
    void dispatchFailureShouldBindTrustedSnapshotWhenDirectSqlEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.listRows = List.of(buildEvent());
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );

        int delivered = service.dispatchPending(event -> {
            throw new IllegalStateException("broker unavailable");
        }, 100);

        assertThat(delivered).isZero();
        assertThat(queryOperations.updates).hasSize(2);
        String failureSql = queryOperations.updates.get(1).sql.toLowerCase(Locale.ROOT);
        assertThat(failureSql)
                .contains("set dispatch_status = ?, retry_count = ?")
                .contains("where deleted = 0 and source_type = ? and id = ?")
                .contains("claim_token")
                .contains("event_type = ?")
                .contains("event_key")
                .contains("user_id = ? and user_uuid = ?")
                .contains("retry_count");
        assertThat(List.of(queryOperations.updates.get(1).args))
                .contains("NOTICE_CREATED", "NOTICE_CREATED:message.notice:9001", 2001L, "user-uuid-2001", 0);
    }

    @Test
    void replayByIdShouldReadFromSourceConstrainedFindWhenDirectSqlEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.byIdRow = buildEvent();
        queryOperations.listRows = List.of(buildEvent());
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );

        boolean replayed = service.replayById(10001L, event -> {
            assertThat(event.getSourceType()).isEqualTo(PlatformEventTypes.SOURCE_SYSTEM);
        });

        assertThat(replayed).isTrue();
        assertThat(queryOperations.queries)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT)
                        .contains("where id = ? and deleted = 0 and source_type = ?"));
        assertThat(queryOperations.queries)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT).contains("limit 1"));
        assertThat(queryOperations.updates)
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT).contains("set dispatch_status = ?, retry_count = 0"));
        assertThat(queryOperations.updates)
                .anyMatch(record -> {
                    String sql = record.sql.toLowerCase(Locale.ROOT);
                    return sql.contains("where deleted = 0 and source_type = ? and id = ?")
                            && sql.contains("event_type = ?")
                            && sql.contains("event_key = ?")
                            && sql.contains("user_id = ? and user_uuid = ?");
                });
        assertThat(queryOperations.updates)
                .anySatisfy(record -> assertThat(List.of(record.args))
                        .contains("NOTICE_CREATED", "NOTICE_CREATED:message.notice:9001", 2001L, "user-uuid-2001"));
    }

    @Test
    void replayByIdShouldNormalizeUserUuidWhenDirectSqlEnabled() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PlatformEventOutboxEntity event = buildEvent();
        event.setUserUuid("  user-uuid-2001  ");
        queryOperations.byIdRow = event;
        queryOperations.listRows = List.of(event);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );

        boolean replayed = service.replayById(10001L, ignored -> {
        });

        assertThat(replayed).isTrue();
        assertThat(queryOperations.updates)
                .anySatisfy(record -> assertThat(List.of(record.args)).contains("user-uuid-2001"));
    }

    @Test
    void dispatchPendingShouldRejectDeliveredWhenClaimSnapshotWriteMissesInDirectSqlMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.listRows = List.of(buildEvent());
        queryOperations.updateResults.add(1);
        queryOperations.updateResults.add(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );

        int delivered = service.dispatchPending(event -> {
        }, 100);

        assertThat(delivered).isZero();
        assertThat(queryOperations.updates).hasSize(3);
        assertThat(queryOperations.updates.get(2).sql.toLowerCase(Locale.ROOT))
                .contains("set dispatch_status = ?, retry_count = ?");
    }

    @Test
    void replayByIdShouldNotDispatchWhenResetBoundaryMissesInDirectSqlMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        queryOperations.byIdRow = buildEvent();
        queryOperations.updateResult = 0;
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(),
                mock(PlatformEventOutboxMapper.class),
                queryOperations
        );
        AtomicInteger dispatchCount = new AtomicInteger();

        boolean replayed = service.replayById(10001L, event -> dispatchCount.incrementAndGet());

        assertThat(replayed).isFalse();
        assertThat(dispatchCount).hasValue(0);
    }

    @Test
    void replayByIdShouldRejectInvalidIdBeforeMapperAccess() {
        PlatformEventOutboxMapper mapper = mock(PlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        boolean replayed = service.replayById(0L, event -> {
        });

        assertThat(replayed).isFalse();
        verify(mapper, org.mockito.Mockito.never()).selectOne(any());
    }

    private PlatformEventOutboxEntity buildEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(10001L);
        event.setUserId(2001L);
        event.setUserUuid("user-uuid-2001");
        event.setSourceType(PlatformEventTypes.SOURCE_SYSTEM);
        event.setEventType("NOTICE_CREATED");
        event.setEventKey("NOTICE_CREATED:message.notice:9001");
        event.setPayloadJson("{\"userUuid\":\"user-uuid-2001\"}");
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        event.setRetryCount(0);
        event.setUpdatedBy(2001L);
        event.setDeleted(0);
        return event;
    }

    private Map<String, Object> trustedPayload() {
        return Map.of("userUuid", "user-uuid-2001");
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final List<RecordedSql> queries = new ArrayList<>();
        private final List<RecordedSql> updates = new ArrayList<>();
        private List<PlatformEventOutboxEntity> listRows = new ArrayList<>();
        private PlatformEventOutboxEntity byIdRow;
        private String userUuid;
        private int updateResult = 1;
        private final Queue<Integer> updateResults = new ArrayDeque<>();

        private RecordingQueryOperations() {
            super();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries.add(new RecordedSql(sql, args));
            if (sql.toLowerCase(Locale.ROOT).contains("claim_token = ?")) {
                for (PlatformEventOutboxEntity event : listRows) {
                    event.setDispatchStatus(PlatformEventOutboxService.STATUS_DISPATCHING);
                    event.setClaimToken(String.valueOf(args[1]));
                }
                return (List<T>) listRows;
            }
            if (sql.toLowerCase(Locale.ROOT).contains("where id = ? and deleted = 0 and source_type = ?")) {
                if (byIdRow == null) {
                    return List.of();
                }
                return (List<T>) List.of(byIdRow);
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = query(sql, rowMapper, args);
            if (rows.isEmpty()) {
                return null;
            }
            return rows.get(0);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queries.add(new RecordedSql(sql, args));
            if (String.class.equals(requiredType)) {
                return (T) userUuid;
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(new RecordedSql(sql, args));
            return updateResults.isEmpty() ? updateResult : updateResults.remove();
        }
    }

    private static final class RecordedSql {
        private final String sql;
        private final Object[] args;

        private RecordedSql(String sql, Object[] args) {
            this.sql = sql;
            this.args = args;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> updateWrapperCaptor() {
        return ArgumentCaptor.forClass((Class) UpdateWrapper.class);
    }
}
