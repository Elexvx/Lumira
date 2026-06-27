package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicActivityControllerTest {

    @Test
    void activitiesReadsPublishedActivityCardsWithoutCurrentUser() {
        ActivityManagementAppService activityManagementAppService = mock(ActivityManagementAppService.class);
        PageResponse<ActivityVO.Activity> page = new PageResponse<>();
        PublicActivityController controller = new PublicActivityController(activityManagementAppService);

        when(activityManagementAppService.listPublishedActivities("roadshow", "zh", true, 1L, 12L)).thenReturn(page);

        ApiResponse<PageResponse<ActivityVO.Activity>> response = controller.activities("roadshow", "zh", true, 1L, 12L);

        assertThat(response.getData()).isSameAs(page);
        verify(activityManagementAppService).listPublishedActivities("roadshow", "zh", true, 1L, 12L);
    }
}
