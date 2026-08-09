package com.lumira.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class EventCatalogRebuildJobHandlerTest {

    @Test
    void registersSourceScopedRebuildAsAStatelessControlPlaneAdapter() throws Exception {
        BackendJobClient backendJobClient = mock(BackendJobClient.class);
        when(backendJobClient.rebuildEventCatalogSource("ACTIVITY")).thenReturn(4);
        EventCatalogRebuildJobHandler handler = new EventCatalogRebuildJobHandler(backendJobClient);

        handler.execute("ACTIVITY");

        Method execute = EventCatalogRebuildJobHandler.class.getDeclaredMethod("execute");
        XxlJob annotation = execute.getAnnotation(XxlJob.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("eventCatalogRebuildJob");
        verify(backendJobClient).rebuildEventCatalogSource("ACTIVITY");
        assertThat(EventCatalogRebuildJobHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactly("BackendJobClient");
    }
}
