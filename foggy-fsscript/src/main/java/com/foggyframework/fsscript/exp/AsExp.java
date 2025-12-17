package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.VarDef;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsExp extends AbstractExp<String> {

    private String asTring;

    public AsExp(String value, String asTring) {
        super(value);
        this.asTring = asTring;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        return null;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return null;
    }

    @Override
    public String toString() {
        return "[ " + value + " as " + asTring + " ]";
    }

}