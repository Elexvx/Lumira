package com.lumira.saas.modules.eventcatalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.eventcatalog.app.EventCatalogAppService;
import org.junit.jupiter.api.Test;

class EventCatalogInternalJobControllerTest {

    @Test
    void rebuildRequiresInternalJobTokenAndReturnsSnapshotCount() {
        EventCatalogAppService appService = mock(EventCatalogAppService.class);
        EventCatalogInternalJobController controller = new EventCatalogInternalJobController(appService, "catalog-job-token");

        assertThatThrownBy(() -> controller.rebuild("ACTIVITY", "wrong-token"))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        when(appService.rebuildSource("ACTIVITY")).thenReturn(3);
        ApiResponse<Integer> response = controller.rebuild("ACTIVITY", "catalog-job-token");

        assertThat(response.getData()).isEqualTo(3);
        verify(appService).rebuildSource("ACTIVITY");
    }
}
