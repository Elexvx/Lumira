package com.lumira.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public class PageResponse<T> {

    private long pageNo;
    private long pageSize;
    private long total;
    private List<T> records;
    private Boolean hasMore;
    private Long nextCursorId;
    private String nextCursorCreatedAt;

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    /**
     * Optional offset-pagination continuation flag. It is omitted when absent
     * so existing base-page JSON responses keep their original field set.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    /**
     * Optional cursor continuation identifier for cursor-based endpoints.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getNextCursorId() {
        return nextCursorId;
    }

    public void setNextCursorId(Long nextCursorId) {
        this.nextCursorId = nextCursorId;
    }

    /**
     * Optional creation-time component of a cursor continuation position.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getNextCursorCreatedAt() {
        return nextCursorCreatedAt;
    }

    public void setNextCursorCreatedAt(String nextCursorCreatedAt) {
        this.nextCursorCreatedAt = nextCursorCreatedAt;
    }
}
