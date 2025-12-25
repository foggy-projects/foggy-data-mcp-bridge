package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.PagingResult;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class PagingReturnConverter<T extends PagingResult> extends AbstractReturnConverter<T> implements ReturnConverter {
    Class<T> pagingResultClazz;

    @Override
    public PagingResult convertPagingResult(PagingResultImpl pagingResult) {
        if (pagingResultClazz.isInstance(pagingResult)) {
            return pagingResult;
        }
        PagingResult result = convertList(pagingResult.getStart(), pagingResult.getLimit(), pagingResult.getItems());
        result.setTotal(pagingResult.getTotal());

        return result;
    }

    @Override
    public PagingResult convertList(int start, int limit, List items) {
        PagingResult pagingResult = null;
        try {
            pagingResult = pagingResultClazz.newInstance();
            pagingResult.setItems(items);
            pagingResult.setLimit(limit);
            pagingResult.setStart(start);
            return pagingResult;
        } catch (InstantiationException e) {
            throw RX.throwB(e);
        } catch (IllegalAccessException e) {
            throw RX.throwB(e);
        }
    }

    @Override
    public boolean getDefaultReturnTotal() {
        return true;
    }

    @Override
    public int getDefaultMaxLimit() {
        return 10;
    }
}
