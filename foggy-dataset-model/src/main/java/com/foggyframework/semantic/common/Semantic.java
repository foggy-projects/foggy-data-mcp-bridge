package com.foggyframework.semantic.common;

import javax.annotation.Nullable;

public interface Semantic {
    @Nullable
    TermItem getTermItemByName(String name);
}
