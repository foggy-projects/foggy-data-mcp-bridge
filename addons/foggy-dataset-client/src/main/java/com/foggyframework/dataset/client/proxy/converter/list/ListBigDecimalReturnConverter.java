package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;

import java.math.BigDecimal;

public class ListBigDecimalReturnConverter extends AbstractListC1ReturnConverter<BigDecimal> {
    @Override
    protected ObjectTransFormatter<BigDecimal> getFormat() {
        return ObjectTransFormatter.BIGDECIMAL_TRANSFORMATTERINSTANCE;
    }
}
