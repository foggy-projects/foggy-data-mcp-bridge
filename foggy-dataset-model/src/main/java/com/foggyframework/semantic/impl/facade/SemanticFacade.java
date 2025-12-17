package com.foggyframework.semantic.impl.facade;

import com.foggyframework.semantic.common.TermItem;

import javax.annotation.Nullable;

public interface SemanticFacade {
    @Nullable
    TermItem getTermItem(String packageName, String name);

    @Nullable
    default String getTermItemCaption(String packageName, String name) {
        TermItem t = getTermItem(packageName, name);
        return t == null ? null : t.getCaption();
    }
}
