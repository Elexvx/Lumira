package com.legendary.invention.message.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.legendary.invention.api.message.MessageEventDTO;
import com.legendary.invention.api.message.MessageNoticeDTO;
import com.legendary.invention.message.mapper.PlatformEventOutboxMapper;
import com.legendary.invention.message.service.MessageEventDeliveryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformEventOutboxServiceTest {

    @Mock
    private PlatformEventOutboxMapper outboxMapper;

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
    void dispatchPendingShouldDeliverAndMarkDelivered() throws Exception {
        MessageEventDTO event = buildEvent();
        PlatformEventOutboxEntity stored = buildStoredEntity(event);
        when(outboxMapper.selectList(anyWrapper())).thenReturn(List.of(stored));
        when(outboxMapper.update(any(), anyWrapper())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(outboxMapper, objectMapper, new SimpleMeterRegistry());

        int delivered = service.dispatchPending(deliveryService, 100);

        assertThat(delivered).isEqualTo(1);
        ArgumentCaptor<MessageEventDTO> eventCaptor = ArgumentCaptor.forClass(MessageEventDTO.class);
        verify(deliveryService).deliver(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("NOTICE_CREATED");
        assertThat(eventCaptor.getValue().getNotice().getTitle()).isEqualTo("系统提醒");
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
}
