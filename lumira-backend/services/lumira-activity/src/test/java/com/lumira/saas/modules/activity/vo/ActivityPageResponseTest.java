package com.lumira.saas.modules.activity.vo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityPageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preservesTheHistoricalSystemPagePayloadShape() throws Exception {
        ActivityPageResponse<String> page = new ActivityPageResponse<>();
        page.setRecords(List.of("activity"));
        page.setTotal(1);
        page.setPageNo(1);
        page.setPageSize(10);

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(page));
        List<String> fields = new ArrayList<>();
        payload.fieldNames().forEachRemaining(fields::add);

        assertThat(fields)
                .containsExactly("records", "total", "pageNo", "pageSize", "hasMore", "nextCursorId", "nextCursorCreatedAt");
        assertThat(payload.path("records").get(0).asText()).isEqualTo("activity");
        assertThat(payload.path("hasMore").isNull()).isTrue();
        assertThat(payload.path("nextCursorId").isNull()).isTrue();
        assertThat(payload.path("nextCursorCreatedAt").isNull()).isTrue();
    }
}
