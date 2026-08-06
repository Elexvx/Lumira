package com.lumira.saas.common.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepCommonBasePaginationJsonFreeOfContinuationFields() throws Exception {
        com.lumira.common.vo.PageResponse<String> response = new com.lumira.common.vo.PageResponse<>();
        response.setRecords(List.of("order-1"));
        response.setTotal(1);
        response.setPageNo(1);
        response.setPageSize(20);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(fieldNames(json))
                .containsExactly("pageNo", "pageSize", "total", "records");
        assertThat(json.has("hasMore")).isFalse();
        assertThat(json.has("nextCursorId")).isFalse();
        assertThat(json.has("nextCursorCreatedAt")).isFalse();

        response.setHasMore(true);
        JsonNode withOffsetContinuation = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(withOffsetContinuation.path("hasMore").asBoolean()).isTrue();
    }

    @Test
    void shouldRetainSystemCursorPaginationJsonContract() throws Exception {
        JsonNode defaults = objectMapper.readTree(objectMapper.writeValueAsString(new PageResponse<>()));

        assertThat(fieldNames(defaults))
                .containsExactly(
                        "records", "total", "pageNo", "pageSize", "hasMore", "nextCursorId", "nextCursorCreatedAt"
                );
        assertThat(defaults.path("records").isArray()).isTrue();
        assertThat(defaults.path("hasMore").isNull()).isTrue();
        assertThat(defaults.path("nextCursorId").isNull()).isTrue();
        assertThat(defaults.path("nextCursorCreatedAt").isNull()).isTrue();

        PageResponse<String> response = new PageResponse<>();
        response.setRecords(List.of("competition-1"));
        response.setTotal(21);
        response.setPageNo(2);
        response.setPageSize(20);
        response.setHasMore(true);
        response.setNextCursorId(42L);
        response.setNextCursorCreatedAt("2026-08-06T22:00:00");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(fieldNames(json))
                .containsExactly(
                        "records", "total", "pageNo", "pageSize", "hasMore", "nextCursorId", "nextCursorCreatedAt"
                );
        assertThat(json.path("hasMore").asBoolean()).isTrue();
        assertThat(json.path("nextCursorId").asLong()).isEqualTo(42L);
        assertThat(json.path("nextCursorCreatedAt").asText()).isEqualTo("2026-08-06T22:00:00");
        assertThat(new PageResponse<>().getRecords()).isEmpty();
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
