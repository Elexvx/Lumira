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

    @Test
    void recordShouldPersistUnifiedEventPayload() {
        when(outboxMapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity entity = service.record(event);

        assertThat(entity.getTenantId()).isEqualTo(1001L);
        assertThat(entity.getUserId()).isEqualTo(2001L);
        assertThat(entity.getEventType()).isEqualTo("NOTICE_CREATED");
        assertThat(entity.getEventKey()).contains("NOTICE_CREATED", "1001", "9001");
        assertThat(entity.getPayloadJson()).contains("\"eventCategory\":\"BUSINESS\"");
    }

    @Test
    void recordShouldRejectNonMessageSourceType() {
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());
        MessageEventDTO event = buildEvent();
        event.setSourceType("PLUGIN");

        assertThrows(IllegalArgumentException.class, () -> service.record(event));
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
        assertThat(sql).contains("source_type = #{sourcetype}");
        assertThat(sql).contains("dispatch_status = #{recordedstatus}");
        assertThat(sql).contains("dispatch_status = #{failedstatus}");
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
                .contains(PlatformEventOutboxService.STATUS_DEAD_LETTER, 8, "broker unavailable", stored.getUpdatedBy(), null);
        wrapperCaptor.getAllValues().forEach(wrapper ->
                assertThat(wrapper.getSqlSegment()).contains("source_type", "deleted")
        );
    }

    private MessageEventDTO buildEvent() {
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setId(9001L);
        notice.setTenantId(1001L);
        notice.setTargetScope("TENANT");
        notice.setTitle("系统提醒");
        notice.setContent("内容");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");

        MessageEventDTO event = new MessageEventDTO();
        event.setEventCategory("BUSINESS");
        event.setSourceType("MESSAGE");
        event.setEventType("NOTICE_CREATED");
        event.setTenantId(1001L);
        event.setUserId(2001L);
        event.setVersion(9001L);
        event.setEventKey("NOTICE_CREATED:1001:9001:2001:9001");
        event.setNotice(notice);
        event.setMessage("消息已发布");
        return event;
    }

    private PlatformEventOutboxEntity buildStoredEntity(MessageEventDTO event) throws Exception {
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setId(1L);
        entity.setTenantId(event.getTenantId());
        entity.setUserId(event.getUserId());
        entity.setSourceType(event.getSourceType());
        entity.setEventType(event.getEventType());
        entity.setEventKey("NOTICE_CREATED:1001:9001:2001:9001");
        entity.setPayloadJson(objectMapper.writeValueAsString(event));
        entity.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setUpdatedBy(2001L);
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
