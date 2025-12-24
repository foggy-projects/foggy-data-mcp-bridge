package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

public class ListBooleanReturnConverter extends AbstractListC1ReturnConverter<Boolean> {
    @Override
    protected ObjectTransFormatter<Boolean> getFormat() {
        return ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE;
    }
}
