package com.lumira.saas.modules.eventcatalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.event.EventCatalogPage;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

class PublicEventCatalogControllerTest {

    @Test
    void publicCatalogDelegatesOnlyToReadModelQueryPort() {
        EventCatalogQueryPort queryPort = mock(EventCatalogQueryPort.class);
        EventCatalogPage page = new EventCatalogPage(java.util.List.of(), 0L, 2L, 20L, false);
        when(queryPort.listPublished("roadshow", "ACTIVITY", "zh", true, 2L, 20L)).thenReturn(page);
        PublicEventCatalogController controller = new PublicEventCatalogController(queryPort);

        ApiResponse<EventCatalogPage> response = controller.list("roadshow", "ACTIVITY", "zh", true, 2L, 20L);

        assertThat(response.getData()).isEqualTo(page);
        verify(queryPort).listPublished("roadshow", "ACTIVITY", "zh", true, 2L, 20L);
    }
}
