package com.aisocialgame.dto;

import java.util.List;

public class PagedResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    public PagedResponse(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
