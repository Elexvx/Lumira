package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.api.event.EventCatalogItem;
import com.lumira.api.event.EventCatalogPage;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.saas.modules.activity.vo.ActivityPageResponse;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicActivityControllerTest {

    @Test
    void activitiesReadsPublishedActivityCardsWithoutCurrentUser() {
        EventCatalogQueryPort eventCatalogQueryPort = mock(EventCatalogQueryPort.class);
        EventCatalogItem item = new EventCatalogItem(
                21L, "ACTIVITY", 9L, "act-9", "zh", "Roadshow", "Subtitle", "Description", "published",
                null, null, "2026-08-08", null, "09:00", "Shanghai", "/activity.png", "ai",
                "Apply", "/apply", true, 20, 7L, null
        );
        EventCatalogPage page = new EventCatalogPage(java.util.List.of(item), 1L, 1L, 12L, false);
        PublicActivityController controller = new PublicActivityController(eventCatalogQueryPort);

        when(eventCatalogQueryPort.listPublished("roadshow", "ACTIVITY", "zh", true, 1L, 12L)).thenReturn(page);

        ApiResponse<ActivityPageResponse<ActivityVO.PublicActivity>> response = controller.activities("roadshow", "zh", true, 1L, 12L);

        assertThat(response.getData().getRecords()).singleElement().satisfies(activity -> {
            assertThat(activity.getId()).isEqualTo(9L);
            assertThat(activity.getTitle()).isEqualTo("Roadshow");
            assertThat(activity.getActivityDate()).isEqualTo("2026-08-08");
        });
        verify(eventCatalogQueryPort).listPublished("roadshow", "ACTIVITY", "zh", true, 1L, 12L);
    }
}
