package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

public class ListLongReturnConverter extends AbstractListC1ReturnConverter<Long> {
    @Override
    protected ObjectTransFormatter<Long> getFormat() {
        return ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE;
    }
}
