package com.lumira.saas.infrastructure.readmodel;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadModelVersionServiceTest {

    @Test
    void bumpShouldBindVersionIncrementToEventKeyIdempotency() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/infrastructure/readmodel/ReadModelVersionService.java"));

        assertThat(source)
                .contains("values(last_event_key) is not null and last_event_key = values(last_event_key)")
                .contains("then version")
                .contains("else version + 1")
                .contains("then last_event_key")
                .contains("then rebuilt_at");
    }

    @Test
    void bumpShouldPersistNormalizedContextScopeAndEventKey() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(7L);
        ReadModelVersionService service = new ReadModelVersionService(jdbcTemplate);

        long version = service.bump(" platform ", " public-bootstrap ", " event-1 ");

        assertThat(version).isEqualTo(7L);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("on duplicate key update"),
                eq("platform"),
                eq("public-bootstrap"),
                eq("event-1"),
                any()
        );
    }
}
