package com.lumira.domain.query;

public record PageQuery(int page, int size) {

    public PageQuery {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 200));
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public int limitPlusOne() {
        return size + 1;
    }
}
