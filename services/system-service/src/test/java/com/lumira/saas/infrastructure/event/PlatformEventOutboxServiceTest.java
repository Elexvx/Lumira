package com.lumira.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

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
                service.record("MESSAGE", "NOTICE_CREATED", 1001L, 2001L, "event-key", "{}"));
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
                .contains(PlatformEventOutboxService.STATUS_DEAD_LETTER, 8, "broker unavailable", event.getUpdatedBy(), null);
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
        assertThat(listSql).contains("from platform_event_outbox force index (idx_platform_event_outbox_owner_queue)");
        assertThat(listSql).contains("where deleted = 0");
        assertThat(listSql).contains("source_type = ?");
        assertThat(listSql).contains("dispatch_status = ?");
        assertThat(listSql).contains("next_retry_at is null or next_retry_at <= ?");
        assertThat(listSql).contains("order by created_at asc, id asc");
        assertThat(listArgs[0]).isEqualTo(PlatformEventTypes.SOURCE_SYSTEM);
        assertThat(listArgs[1]).isEqualTo(PlatformEventOutboxService.STATUS_RECORDED);
        assertThat(listArgs[2]).isEqualTo(PlatformEventOutboxService.STATUS_FAILED);
        assertThat(listArgs[4]).isEqualTo(200);

        assertThat(queryOperations.updates).hasSize(2);
        assertThat(queryOperations.updates.get(0).sql.toLowerCase(Locale.ROOT))
                .contains("where deleted = 0 and source_type = ? and id = ? and dispatch_status = ?");
        assertThat(queryOperations.updates.get(1).sql.toLowerCase(Locale.ROOT))
                .contains("where deleted = 0 and source_type = ? and id = ?");
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
                .anyMatch(record -> record.sql.toLowerCase(Locale.ROOT)
                        .contains("where deleted = 0 and source_type = ? and id = ?"));
    }

    private PlatformEventOutboxEntity buildEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(10001L);
        event.setTenantId(1001L);
        event.setUserId(2001L);
        event.setSourceType(PlatformEventTypes.SOURCE_SYSTEM);
        event.setEventType("NOTICE_CREATED");
        event.setEventKey("NOTICE_CREATED:1001:9001:tenant:none");
        event.setPayloadJson("{}");
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        event.setRetryCount(0);
        event.setUpdatedBy(2001L);
        event.setDeleted(0);
        return event;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final List<RecordedSql> queries = new ArrayList<>();
        private final List<RecordedSql> updates = new ArrayList<>();
        private List<PlatformEventOutboxEntity> listRows = new ArrayList<>();
        private PlatformEventOutboxEntity byIdRow;

        private RecordingQueryOperations() {
            super();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries.add(new RecordedSql(sql, args));
            if (sql.toLowerCase(Locale.ROOT).contains("force index")) {
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

        @Override
        public int update(String sql, Object... args) {
            updates.add(new RecordedSql(sql, args));
            return 1;
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
