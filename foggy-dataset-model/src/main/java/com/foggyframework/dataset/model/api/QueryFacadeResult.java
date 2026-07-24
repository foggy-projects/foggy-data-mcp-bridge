package com.foggyframework.dataset.model.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Stable public query result DTO.
 *
 * @since 9.3.5
 */
public final class QueryFacadeResult {

    private final long total;
    private final boolean hasNext;
    private final int start;
    private final int limit;
    private final List<Map<String, Object>> items;
    private final Object totalData;

    public QueryFacadeResult(
            long total,
            boolean hasNext,
            int start,
            int limit,
            List<Map<String, Object>> items,
            Object totalData
    ) {
        this.total = total;
        this.hasNext = hasNext;
        this.start = start;
        this.limit = limit;
        this.items = items == null ? Collections.emptyList() : List.copyOf(items);
        this.totalData = totalData;
    }

    public long getTotal() {
        return total;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public int getStart() {
        return start;
    }

    public int getLimit() {
        return limit;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public Object getTotalData() {
        return totalData;
    }
}
