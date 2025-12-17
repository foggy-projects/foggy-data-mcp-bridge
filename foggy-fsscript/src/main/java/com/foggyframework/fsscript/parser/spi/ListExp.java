package com.foggyframework.fsscript.parser.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ListExp extends ArrayList<Exp> {
    public ListExp(int initialCapacity) {
        super(initialCapacity);
    }

    public ListExp() {
    }

    public ListExp(Collection<? extends Exp> c) {
        super(c);
    }

    public List applyList(ExpEvaluator ee) {
        List xx = new ArrayList(size());
        for (Exp e : this) {
            e.apply2List(xx, ee);
        }
        return xx;
    }

    public List applyList(List xx, ExpEvaluator ee) {

        for (Exp e : this) {
            e.apply2List(xx, ee);
        }
        return xx;
    }
}
