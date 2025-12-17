package com.foggyframework.dataset.model.support;

import com.foggyframework.fsscript.parser.spi.Exp;

public class SQL extends QL {
    public SQL() {
    }

    public SQL(Exp expressionExp, Exp ifExp, Exp dsExp) {
        super(expressionExp, ifExp, dsExp);
    }
}
