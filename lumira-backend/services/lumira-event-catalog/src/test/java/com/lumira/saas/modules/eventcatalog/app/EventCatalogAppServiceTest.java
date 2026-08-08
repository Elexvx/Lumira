package com.lumira.saas.modules.eventcatalog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventCatalogEventWatermarkPort;
import com.lumira.api.event.EventCatalogProjectionEvent;
import com.lumira.api.event.EventCatalogSourceSnapshot;
import com.lumira.api.event.EventCatalogSourceSnapshotPort;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.saas.modules.eventcatalog.repository.EventCatalogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class EventCatalogAppServiceTest {

    @Test
    void appliesDurableCatalogEventOnceThroughReceiptAndProjectionRepository() {
        EventCatalogRepository repository = mock(EventCatalogRepository.class);
        EventConsumptionPort receipts = mock(EventConsumptionPort.class);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        }).when(receipts).executeOnce(any(), any());
        EventCatalogAppService service = service(repository, receipts, () -> 0L, List.of());

        boolean applied = service.handle(new EventCatalogProjectionEvent(
                41L,
                "EVENT_CATALOG_ITEM_UPSERTED",
                payload("ACTIVITY", 9L, "published")
        ));

        assertThat(applied).isTrue();
        ArgumentCaptor<EventConsumptionPort.EventIdentity> identity = ArgumentCaptor.forClass(EventConsumptionPort.EventIdentity.class);
        verify(receipts).executeOnce(identity.capture(), any());
        assertThat(identity.getValue().consumerName()).isEqualTo(EventCatalogAppService.CONSUMER_NAME);
        assertThat(identity.getValue().eventId()).isEqualTo("41");
        ArgumentCaptor<EventCatalogRepository.CatalogWrite> write = ArgumentCaptor.forClass(EventCatalogRepository.CatalogWrite.class);
        verify(repository).apply(write.capture());
        assertThat(write.getValue().sourceType()).isEqualTo("ACTIVITY");
        assertThat(write.getValue().sourceId()).isEqualTo(9L);
        assertThat(write.getValue().status()).isEqualTo("published");
        assertThat(write.getValue().outboxSequence()).isEqualTo(41L);
    }

    @Test
    void projectionEnvelopeRequiresPositiveMonotonicOutboxSequenceRatherThanDomainEventIdentity() {
        assertThatThrownBy(() -> new EventCatalogProjectionEvent(
                0L,
                "EVENT_CATALOG_ITEM_UPSERTED",
                "{\"attributes\":{}}"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outboxSequence must be positive");
    }

    @Test
    void rebuildReadsWatermarkBeforeOwnerSnapshotAndPersistsItAsStaleEventCutoff() {
        EventCatalogRepository repository = mock(EventCatalogRepository.class);
        EventConsumptionPort receipts = mock(EventConsumptionPort.class);
        EventCatalogEventWatermarkPort watermark = mock(EventCatalogEventWatermarkPort.class);
        EventCatalogSourceSnapshotPort source = mock(EventCatalogSourceSnapshotPort.class);
        when(source.sourceType()).thenReturn("ACTIVITY");
        when(watermark.currentWatermark()).thenReturn(88L);
        EventCatalogSourceSnapshot snapshot = new EventCatalogSourceSnapshot(
                "ACTIVITY", 9L, "act-9", "zh", "Roadshow", "Subtitle", "Description", "published",
                null, null, "2026-08-08", null, "10:00", "Shanghai", null, null, null, null,
                false, 100, LocalDateTime.of(2026, 8, 8, 10, 0)
        );
        when(source.loadCatalogSnapshots(0L, 500)).thenReturn(List.of(snapshot));

        EventCatalogAppService service = service(repository, receipts, watermark, List.of(source));
        clearInvocations(watermark, source);
        int rebuilt = service.rebuildSource("activity");

        assertThat(rebuilt).isEqualTo(1);
        ArgumentCaptor<List<EventCatalogSourceSnapshot>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceSource(anyString(), snapshots.capture(), org.mockito.Mockito.eq(88L));
        assertThat(snapshots.getValue()).containsExactly(snapshot);
        verify(watermark).currentWatermark();
        verify(source).loadCatalogSnapshots(0L, 500);
        InOrder rebuildOrder = inOrder(watermark, source);
        rebuildOrder.verify(watermark).currentWatermark();
        rebuildOrder.verify(source).loadCatalogSnapshots(0L, 500);
    }

    private EventCatalogAppService service(
            EventCatalogRepository repository,
            EventConsumptionPort receipts,
            EventCatalogEventWatermarkPort watermark,
            List<EventCatalogSourceSnapshotPort> sourcePorts
    ) {
        return new EventCatalogAppService(
                new ObjectMapper(),
                repository,
                receipts,
                watermark,
                sourcePorts,
                new SimpleMeterRegistry()
        );
    }

    private String payload(String sourceType, long sourceId, String status) {
        return """
                {
                  "attributes": {
                    "sourceType": "%s",
                    "sourceId": %d,
                    "sourceUuid": "source-%d",
                    "locale": "zh",
                    "title": "Catalog title",
                    "subtitle": "Catalog subtitle",
                    "summary": "Catalog summary",
                    "status": "%s",
                    "eventStart": "2026-08-08",
                    "eventTime": "10:00",
                    "location": "Shanghai",
                    "featured": true,
                    "sort": 20,
                    "sourceUpdatedAt": "2026-08-08T10:00:00"
                  }
                }
                """.formatted(sourceType, sourceId, sourceId, status);
    }
}
