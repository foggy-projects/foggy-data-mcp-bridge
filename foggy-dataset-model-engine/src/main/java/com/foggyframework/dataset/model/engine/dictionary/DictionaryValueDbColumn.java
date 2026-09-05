package com.foggyframework.dataset.model.engine.dictionary;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.model.impl.AiObject;
import com.foggyframework.dataset.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.model.spi.DbColumn;

/**
 * Raw static-dictionary column that keeps its database type and SQL declaration
 * while accepting either a code or an exact registered label in filters.
 */
public final class DictionaryValueDbColumn extends DbColumnDelegate {

    private final DictionaryBinding binding;

    public DictionaryValueDbColumn(DbColumn sourceColumn, DictionaryBinding binding) {
        super(sourceColumn);
        this.binding = binding;
    }

    @Override
    public ObjectTransFormatter<?> getFormatter() {
        return binding.getCodeOrLabelFormatter();
    }

    @Override
    public ObjectTransFormatter<?> getFormatter(boolean errorIfNull) {
        return binding.getCodeOrLabelFormatter();
    }

    public DictionaryBinding getBinding() {
        return binding;
    }

    @Override
    public Object getExtData() {
        return delegate.getExtData();
    }

    @Override
    public AiObject getAi() {
        return delegate.getAi();
    }
}
