package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.core.Decorate;
import com.foggyframework.dataset.db.model.impl.AiObject;

public interface DbObject extends Decorate {
    String getCaption();

    String getName();

//    String getAlias();

    String getDescription();

    long FLAG_DEPRECATED = 0x01;

    boolean _isDeprecated();

    Object getExtData();

    AiObject getAi();

}
