package com.lumira.file.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformEventOutboxServiceTest {

    @Test
    void recordShouldPersistSerializedPayload() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                1001L,
                2001L,
                "FILE_OBJECT_UPLOADED:1001:file.object:3001",
                Map.of("aggregateId", 3001L)
        );

        ArgumentCaptor<PlatformEventOutboxEntity> captor = ArgumentCaptor.forClass(PlatformEventOutboxEntity.class);
        verify(mapper).insert(captor.capture());
        PlatformEventOutboxEntity entity = captor.getValue();
        assertEquals(1001L, entity.getTenantId());
        assertEquals(2001L, entity.getUserId());
        assertEquals(FilePlatformEventTypes.SOURCE_FILE, entity.getSourceType());
        assertEquals(FilePlatformEventTypes.FILE_OBJECT_UPLOADED, entity.getEventType());
        assertEquals("FILE_OBJECT_UPLOADED:1001:file.object:3001", entity.getEventKey());
        assertEquals(PlatformEventOutboxService.STATUS_RECORDED, entity.getDispatchStatus());
        assertTrue(entity.getPayloadJson().contains("\"aggregateId\":3001"));
    }
}
