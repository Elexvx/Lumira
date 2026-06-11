package com.lumira.saas.common.vo;

import java.util.Collections;
import java.util.List;

public class PageResponse<T> {

    private List<T> records = Collections.emptyList();
    private long total;
    private long pageNo;
    private long pageSize;
    private Boolean hasMore;
    private Long nextCursorId;
    private String nextCursorCreatedAt;

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    public Long getNextCursorId() {
        return nextCursorId;
    }

    public void setNextCursorId(Long nextCursorId) {
        this.nextCursorId = nextCursorId;
    }

    public String getNextCursorCreatedAt() {
        return nextCursorCreatedAt;
    }

    public void setNextCursorCreatedAt(String nextCursorCreatedAt) {
        this.nextCursorCreatedAt = nextCursorCreatedAt;
    }
}
