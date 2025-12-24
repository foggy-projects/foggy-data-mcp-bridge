package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.dataset.model.PagingResultImpl;

import java.util.List;

public class ListReturnConverter<T extends List> extends AbstractReturnConverter<T> implements ReturnConverter {

    @Override
    public List convertPagingResult(PagingResultImpl pagingResult) {
        return pagingResult.getItems();
    }

    @Override
    public List convertList(int start, int limit, List items) {
        return items;
    }

    @Override
    public int getDefaultMaxLimit() {
        return 99999;
    }
}
