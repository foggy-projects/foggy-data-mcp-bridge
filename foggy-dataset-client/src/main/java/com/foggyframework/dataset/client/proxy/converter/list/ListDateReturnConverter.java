package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

import java.util.Date;

public class ListDateReturnConverter extends AbstractListC1ReturnConverter<Date> {
    @Override
    protected ObjectTransFormatter<Date> getFormat() {
        return ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE;
    }
}
