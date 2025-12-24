package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

public class ListDoubleReturnConverter extends AbstractListC1ReturnConverter<Double> {
    @Override
    protected ObjectTransFormatter<Double> getFormat() {
        return ObjectTransFormatter.DOUBLE_TRANSFORMATTERINSTANCE;
    }
}
