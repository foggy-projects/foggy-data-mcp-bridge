package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

public class ListIntegerReturnConverter extends AbstractListC1ReturnConverter<Integer> {
    @Override
    protected ObjectTransFormatter<Integer> getFormat() {
        return ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE;
    }
}
