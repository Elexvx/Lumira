package com.lumira.message.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.message.MessageEventDTO;
import com.lumira.api.message.MessageNoticeDTO;
import com.lumira.message.mapper.MessagePlatformEventOutboxMapper;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageEventFactory;
import org.apache.ibatis.annotations.Select;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformEventOutboxServiceTest {

    @Mock
    private MessagePlatformEventOutboxMapper outboxMapper;

    @Mock
    private MessageEventDeliveryService deliveryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpTrustedUsers() {
        lenient().when(outboxMapper.resolveUserUuid(2001L)).thenReturn("user-uuid-2001");
        lenient().when(outboxMapper.resolveActiveUserUuid(2001L)).thenReturn("user-uuid-2001");
    }

    @Test
    void recordShouldPersistUnifiedEventPayload() {
        when(outboxMapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity entity = service.record(event);

        assertThat(entity.getUserId()).isEqualTo(2001L);
        assertThat(entity.getUserUuid()).isEqualTo("user-uuid-2001");
        assertThat(entity.getCreatedBy()).isEqualTo(2001L);
        assertThat(entity.getCreatedByUuid()).isEqualTo("user-uuid-2001");
        assertThat(entity.getUpdatedBy()).isEqualTo(2001L);
        assertThat(entity.getUpdatedByUuid()).isEqualTo("user-uuid-2001");
        assertThat(entity.getEventType()).isEqualTo("NOTICE_CREATED");
        assertThat(entity.getEventKey()).contains("NOTICE_CREATED", "2001", "9001");
        assertThat(entity.getPayloadJson()).contains("\"eventCategory\":\"BUSINESS\"");
    }

    @Test
    void recordShouldRejectWhenInsertMisses() {
        when(outboxMapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class, () -> service.record(buildEvent()));
    }

    @Test
    void recordShouldRejectNonMessageSourceType() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setSourceType("PLUGIN");

        assertThrows(IllegalArgumentException.class, () -> service.record(event));
    }

    @Test
    void recordShouldRejectInvalidUserIdBeforeInsert() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setUserId(0L);

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectOversizedEventTypeBeforeInsert() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setEventType("E".repeat(129));

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectOversizedEventKeyBeforeInsert() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setEventKey("k".repeat(257));

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectUntrustedEventKeyBeforeInsert() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setEventKey("../NOTICE_CREATED");

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectOversizedPayloadBeforeInsert() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setPayload(Map.of("body", "x".repeat(256 * 1024)));

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectMissingUserUuidWhenUserIdIsPresent() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setUserUuid(null);

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectUserUuidMismatchBeforeInsert() {
        when(outboxMapper.resolveActiveUserUuid(2001L)).thenReturn("user-uuid-actual");
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setUserUuid("user-uuid-forged");

        assertThrows(IllegalArgumentException.class, () -> service.record(event));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectDisabledUserEvenWhenUserUuidMatches() {
        when(outboxMapper.resolveActiveUserUuid(2001L)).thenReturn(null);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        assertThrows(IllegalArgumentException.class, () -> service.record(buildEvent()));

        verify(outboxMapper, org.mockito.Mockito.never()).insert(any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldNotInventAuditUserForAnonymousMessageEvent() {
        when(outboxMapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setUserId(null);
        event.setEventKey("NOTICE_CREATED:9001:all:9001");

        PlatformEventOutboxEntity entity = service.record(event);

        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getUserUuid()).isNull();
        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getCreatedByUuid()).isNull();
        assertThat(entity.getUpdatedBy()).isNull();
        assertThat(entity.getUpdatedByUuid()).isNull();
    }

    @Test
    void dispatchPendingShouldDeliverAndMarkDelivered() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(any(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isEqualTo(1);
        ArgumentCaptor<MessageEventDTO> eventCaptor = ArgumentCaptor.forClass(MessageEventDTO.class);
        verify(deliveryService).deliver(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("NOTICE_CREATED");
        assertThat(eventCaptor.getValue().getNotice().getTitle()).isEqualTo("系统提醒");
        verify(outboxMapper).listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), eq(100));
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues()).hasSize(2);
        wrapperCaptor.getAllValues().forEach(wrapper -> {
            assertThat(wrapper.getSqlSegment()).contains("source_type").contains("deleted");
        });
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSet()).contains("claim_token", "claim_expires_at");
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSet()).contains("updated_by_uuid");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSet()).contains("updated_by_uuid");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSegment())
                .contains("dispatch_status", "claim_token", "user_id", "user_uuid", "retry_count");
    }

    @Test
    void dispatchPendingShouldRejectDeliveredWhenClaimWriteMisses() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(1, 0, 1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isZero();
        verify(deliveryService).deliver(any(MessageEventDTO.class));
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(3)).update(isNull(), wrapperCaptor.capture());
        assertThat(new ArrayList<>(wrapperCaptor.getAllValues().get(2).getParamNameValuePairs().values()))
                .contains(PlatformEventOutboxService.STATUS_FAILED, "Message outbox changed, please retry");
    }

    @Test
    void listDispatchableShouldRejectInvalidLimitBeforeMapperAccess() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        assertThrows(IllegalArgumentException.class, () -> service.listDispatchable(0));
        assertThrows(IllegalArgumentException.class, () -> service.listDispatchable(PlatformEventOutboxService.MAX_DISPATCH_LIMIT + 1));

        verify(outboxMapper, org.mockito.Mockito.never()).listDispatchable(any(), any(), any(), any(), anyInt());
    }

    @Test
    void dispatchPendingShouldRejectInvalidLimitBeforeDelivery() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        assertThrows(IllegalArgumentException.class, () -> service.dispatchPending(deliveryService, 0));

        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
        verify(outboxMapper, org.mockito.Mockito.never()).listDispatchable(any(), any(), any(), any(), anyInt());
    }

    @Test
    void replayByIdShouldResetAndRedispatch() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        when(outboxMapper.selectOne(anyWrapper())).thenReturn(stored);
        when(outboxMapper.update(any(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        boolean replayed = service.replayById(1L, deliveryService);

        assertThat(replayed).isTrue();
        verify(deliveryService).deliver(any(MessageEventDTO.class));

        ArgumentCaptor<Wrapper<PlatformEventOutboxEntity>> queryCaptor = wrapperCaptor();
        verify(outboxMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("source_type");

        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> updateWrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(3)).update(isNull(), updateWrapperCaptor.capture());
        updateWrapperCaptor.getAllValues().forEach(wrapper ->
                assertThat(wrapper.getSqlSegment()).contains("source_type", "deleted")
        );
        assertThat(updateWrapperCaptor.getAllValues().get(0).getSqlSegment())
                .contains("dispatch_status")
                .contains("retry_count")
                .contains("user_id")
                .contains("user_uuid")
                .contains("event_type")
                .contains("event_key");
        assertThat(updateWrapperCaptor.getAllValues().get(1).getSqlSet()).contains("claim_token", "claim_expires_at");
        assertThat(updateWrapperCaptor.getAllValues().get(1).getSqlSegment())
                .contains("user_id")
                .contains("user_uuid")
                .contains("event_type")
                .contains("event_key");
        assertThat(updateWrapperCaptor.getAllValues().get(2).getSqlSegment())
                .contains("dispatch_status", "claim_token", "event_type", "event_key", "user_id", "user_uuid", "retry_count");
    }

    @Test
    void replayByIdShouldNotDeliverWhenResetBoundaryMisses() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        stored.setDispatchStatus(PlatformEventOutboxService.STATUS_FAILED);
        when(outboxMapper.selectOne(anyWrapper())).thenReturn(stored);
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        boolean replayed = service.replayById(1L, deliveryService);

        assertThat(replayed).isFalse();
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
    }

    @Test
    void replayByIdShouldRejectInvalidIdBeforeMapperAccess() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        boolean replayed = service.replayById(0L, deliveryService);

        assertThat(replayed).isFalse();
        verify(outboxMapper, org.mockito.Mockito.never()).selectOne(anyWrapper());
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
    }

    @Test
    void listDispatchableSqlShouldBeOwnerBoundedAndIndexed() throws Exception {
        Select select = MessagePlatformEventOutboxMapper.class
                .getMethod("listDispatchable", String.class, String.class, String.class,
                        java.time.LocalDateTime.class, int.class)
                .getAnnotation(Select.class);

        assertThat(select).isNotNull();
        String sql = select.value()[0].toLowerCase();
        assertThat(sql).contains("force index (idx_platform_event_outbox_owner_queue)");
        assertThat(sql).contains("user_uuid as useruuid");
        assertThat(sql).contains("created_by_uuid as createdbyuuid");
        assertThat(sql).contains("updated_by_uuid as updatedbyuuid");
        assertThat(sql).contains("source_type = #{sourcetype}");
        assertThat(sql).contains("dispatch_status = #{recordedstatus}");
        assertThat(sql).contains("dispatch_status = #{failedstatus}");
        assertThat(sql).contains("dispatch_status = 'dispatching'");
        assertThat(sql).contains("claim_expires_at");
        assertThat(sql).contains("order by created_at asc, id asc");
        assertThat(sql).contains("limit #{limit}");
    }

    @Test
    void countDispatchableShouldOnlyCountMessageOwnerEvents() {
        when(outboxMapper.selectCount(anyWrapper())).thenReturn(3L);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        long count = service.countDispatchable();
        long cached = service.countDispatchable();

        assertThat(count).isEqualTo(3L);
        assertThat(cached).isEqualTo(3L);
        ArgumentCaptor<Wrapper<PlatformEventOutboxEntity>> queryCaptor = wrapperCaptor();
        verify(outboxMapper, times(1)).selectCount(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("source_type");
    }

    @Test
    void failedDispatchAfterMaxRetriesMovesToDeadLetter() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        stored.setDispatchStatus(PlatformEventOutboxService.STATUS_FAILED);
        stored.setRetryCount(7);
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(1);
        doThrow(new RuntimeException("broker unavailable")).when(deliveryService).deliver(any(MessageEventDTO.class));
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isZero();
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        Map<String, Object> failureParams = wrapperCaptor.getAllValues().get(1).getParamNameValuePairs();
        assertThat(new ArrayList<>(failureParams.values()))
                .contains(PlatformEventOutboxService.STATUS_DEAD_LETTER, 8, "broker unavailable", stored.getUpdatedBy(), "user-uuid-2001");
        wrapperCaptor.getAllValues().forEach(wrapper ->
                assertThat(wrapper.getSqlSegment()).contains("source_type", "deleted")
        );
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSet()).contains("claim_token", "claim_expires_at");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSegment())
                .contains("dispatch_status", "claim_token", "user_id", "user_uuid", "retry_count");
    }

    @Test
    void dispatchPendingShouldRejectUntrustedClaimedRowBeforeDelivery() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        stored.setSourceType("PLUGIN");
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isZero();
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        assertThat(new ArrayList<>(wrapperCaptor.getAllValues().get(1).getParamNameValuePairs().values()))
                .contains(PlatformEventOutboxService.STATUS_FAILED, "Message outbox row is invalid");
    }

    @Test
    void dispatchPendingShouldRejectHumanRowMissingUserUuidBeforeDelivery() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        stored.setUserUuid(null);
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isZero();
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        assertThat(new ArrayList<>(wrapperCaptor.getAllValues().get(1).getParamNameValuePairs().values()))
                .contains(PlatformEventOutboxService.STATUS_FAILED, "Message outbox row is invalid");
    }

    @Test
    void dispatchPendingShouldRejectPayloadUserUuidMismatchBeforeDelivery() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        stored.setUserUuid("user-uuid-other");
        when(outboxMapper.listDispatchable(eq(MessageEventFactory.SOURCE_MESSAGE), eq(PlatformEventOutboxService.STATUS_RECORDED),
                eq(PlatformEventOutboxService.STATUS_FAILED), any(), anyInt())).thenReturn(List.of(stored));
        when(outboxMapper.update(isNull(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isZero();
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any());
        ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> wrapperCaptor = updateWrapperCaptor();
        verify(outboxMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        assertThat(new ArrayList<>(wrapperCaptor.getAllValues().get(1).getParamNameValuePairs().values()))
                .contains(PlatformEventOutboxService.STATUS_FAILED, "Message outbox row is invalid");
    }

    private MessageEventDTO buildEvent() {
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setId(9001L);
        notice.setTargetScope("PLATFORM");
        notice.setTitle("系统提醒");
        notice.setContent("内容");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");

        MessageEventDTO event = new MessageEventDTO();
        event.setEventCategory("BUSINESS");
        event.setSourceType("MESSAGE");
        event.setEventType("NOTICE_CREATED");
        event.setUserId(2001L);
        event.setUserUuid("user-uuid-2001");
        event.setVersion(9001L);
        event.setEventKey("NOTICE_CREATED:9001:2001:9001");
        event.setNotice(notice);
        event.setMessage("消息已发布");
        return event;
    }

    private PlatformEventOutboxEntity buildStoredEntity(MessageEventDTO event) throws Exception {
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setId(1L);
        entity.setUserId(event.getUserId());
        entity.setUserUuid(event.getUserUuid());
        entity.setSourceType(event.getSourceType());
        entity.setEventType(event.getEventType());
        entity.setEventKey(event.getEventKey());
        entity.setPayloadJson(objectMapper.writeValueAsString(event));
        entity.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setUpdatedBy(2001L);
        entity.setUpdatedByUuid("user-uuid-2001");
        entity.setDeleted(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private Wrapper<PlatformEventOutboxEntity> anyWrapper() {
        return any(Wrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Wrapper<PlatformEventOutboxEntity>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class) Wrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<UpdateWrapper<PlatformEventOutboxEntity>> updateWrapperCaptor() {
        return ArgumentCaptor.forClass((Class) UpdateWrapper.class);
    }
}
