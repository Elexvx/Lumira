package com.lumira.saas.modules.eventcatalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lumira.saas.modules.eventcatalog.repository.EventCatalogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcEventCatalogRepositoryTest {

    @Test
    void staleOrEqualDeliveryCannotOverwriteProjectionAtRebuildWatermark() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcEventCatalogRepository repository = new JdbcEventCatalogRepository(jdbcTemplate);

        repository.apply(new EventCatalogRepository.CatalogWrite(
                "ACTIVITY", 9L, "act-9", "zh", "Roadshow", null, "Description", "published",
                null, null, "2026-08-08", null, "10:00", "Shanghai", null, null, null, null,
                false, 100, 87L, LocalDateTime.of(2026, 8, 8, 10, 0)
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("if(values(last_event_id) > last_event_id, values(status), status)")
                .contains("last_event_id = greatest(last_event_id, values(last_event_id))");
    }
}
