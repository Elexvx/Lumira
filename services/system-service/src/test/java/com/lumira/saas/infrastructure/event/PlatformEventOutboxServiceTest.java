package com.lumira.saas.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private PlatformEventOutboxEntity buildEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(10001L);
        event.setTenantId(1001L);
        event.setUserId(2001L);
        event.setSourceType("MESSAGE");
        event.setEventType("NOTICE_CREATED");
        event.setEventKey("NOTICE_CREATED:1001:9001:tenant:none");
        event.setPayloadJson("{}");
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        event.setRetryCount(0);
        event.setUpdatedBy(2001L);
        event.setDeleted(0);
        return event;
    }
}
