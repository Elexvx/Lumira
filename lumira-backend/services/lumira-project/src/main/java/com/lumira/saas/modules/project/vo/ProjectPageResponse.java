package com.lumira.saas.modules.project.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;

/**
 * Compatibility page contract for Project endpoints.
 *
 * <p>The historical system-local page type always serialized its continuation
 * fields. Keeping that behavior here preserves the public HTTP payload while
 * the shared base type remains the cross-service pagination primitive.</p>
 */
@JsonPropertyOrder({"records", "total", "pageNo", "pageSize", "hasMore", "nextCursorId", "nextCursorCreatedAt"})
public class ProjectPageResponse<T> extends com.lumira.common.vo.PageResponse<T> {

    public ProjectPageResponse() {
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
