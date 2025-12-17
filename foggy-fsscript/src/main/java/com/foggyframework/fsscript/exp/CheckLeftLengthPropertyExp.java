package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;

public class CheckLeftLengthPropertyExp extends LengthPropertyExp{
    @Override
    protected boolean checkLeft() {
        return true;
    }

    public CheckLeftLengthPropertyExp(Exp exp, String name) {
        super(exp, name);
    }
}
