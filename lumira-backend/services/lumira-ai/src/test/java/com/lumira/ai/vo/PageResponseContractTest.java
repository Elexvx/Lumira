package com.lumira.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRetainAiOffsetPaginationJsonContract() throws Exception {
        JsonNode defaults = objectMapper.readTree(objectMapper.writeValueAsString(new PageResponse<>()));

        assertThat(fieldNames(defaults))
                .containsExactly("records", "total", "pageNo", "pageSize", "hasMore");
        assertThat(defaults.path("records").isArray()).isTrue();
        assertThat(defaults.path("hasMore").isNull()).isTrue();
        assertThat(defaults.has("nextCursorId")).isFalse();
        assertThat(defaults.has("nextCursorCreatedAt")).isFalse();

        PageResponse<String> response = new PageResponse<>();
        response.setRecords(List.of("employee-1"));
        response.setTotal(11);
        response.setPageNo(2);
        response.setPageSize(10);
        response.setHasMore(true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(fieldNames(json))
                .containsExactly("records", "total", "pageNo", "pageSize", "hasMore");
        assertThat(json.path("records").get(0).asText()).isEqualTo("employee-1");
        assertThat(json.path("total").asLong()).isEqualTo(11L);
        assertThat(json.path("hasMore").asBoolean()).isTrue();
        assertThat(new PageResponse<>().getRecords()).isEmpty();
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
