package com.lumira.saas.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;

/**
 * Service-local type retained for existing imports and JSON contracts.
 *
 * <p>New cross-service code should use {@link com.lumira.common.vo.PageResponse}
 * directly. This adapter preserves the system endpoint's historical empty-list
 * default and nullable continuation fields.</p>
 */
@Deprecated(since = "0.1.0", forRemoval = false)
@JsonPropertyOrder({"records", "total", "pageNo", "pageSize", "hasMore", "nextCursorId", "nextCursorCreatedAt"})
public class PageResponse<T> extends com.lumira.common.vo.PageResponse<T> {

    public PageResponse() {
        setRecords(Collections.emptyList());
    }

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Boolean getHasMore() {
        return super.getHasMore();
    }

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Long getNextCursorId() {
        return super.getNextCursorId();
    }

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getNextCursorCreatedAt() {
        return super.getNextCursorCreatedAt();
    }
}
