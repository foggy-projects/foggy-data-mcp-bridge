package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

public class ListStringReturnConverter extends AbstractListC1ReturnConverter<String> {
    @Override
    protected ObjectTransFormatter<String> getFormat() {
        return ObjectTransFormatter.STRING_TRANSFORMATTERINSTANCE;
    }
}
