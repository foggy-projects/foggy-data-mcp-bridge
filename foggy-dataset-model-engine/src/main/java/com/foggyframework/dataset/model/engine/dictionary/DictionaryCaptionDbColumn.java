package com.foggyframework.dataset.model.engine.dictionary;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.model.impl.AiObject;
import com.foggyframework.dataset.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbProperty;

/**
 * Synthetic public {@code $caption} column for a static {@code dictRef} property.
 *
 * <p>Its SQL declaration is delegated to the raw source column. The query value
 * formatter translates labels back to typed codes before SQL binding, while the
 * matching query column translates result codes to labels after JDBC execution.</p>
 */
public final class DictionaryCaptionDbColumn extends DbColumnDelegate {

    private final String publicName;
    private final DbProperty property;
    private final DictionaryBinding binding;

    public DictionaryCaptionDbColumn(DbColumn sourceColumn,
                                     String publicName,
                                     DbProperty property,
                                     DictionaryBinding binding) {
        super(sourceColumn);
        this.publicName = publicName;
        this.property = property;
        this.binding = binding;
    }

    @Override
    public String getName() {
        return publicName;
    }

    @Override
    public String getAlias() {
        return publicName;
    }

    @Override
    public String getField() {
        return publicName;
    }

    @Override
    public String getCaption() {
        String caption = property == null ? null : property.getCaption();
        return caption == null ? publicName : caption + "(名称)";
    }

    @Override
    public String getDescription() {
        return property == null ? super.getDescription() : property.getDescription();
    }

    @Override
    public DbColumnType getType() {
        return DbColumnType.STRING;
    }

    @Override
    public ObjectTransFormatter<?> getFormatter() {
        return binding.getLabelToValueFormatter();
    }

    @Override
    public ObjectTransFormatter<?> getFormatter(boolean errorIfNull) {
        return binding.getLabelToValueFormatter();
    }

    public DbProperty getProperty() {
        return property;
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
