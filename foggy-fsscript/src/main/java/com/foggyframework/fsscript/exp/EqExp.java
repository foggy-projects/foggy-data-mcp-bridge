package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.VarDef;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EqExp extends AbstractExp<String> {


    private Exp exp;

    public EqExp(String value, Exp exp) {
        super(value);
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        Object x = exp.evalResult(evaluator);
        VarDef varDef = evaluator.getVarDef(value);
        if (varDef == null) {
            evaluator.setVar(value, x);
        } else {
            varDef.setValue(x);
        }

        return x;

    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return null;
    }

    @Override
    public String toString() {
        return "[var " + value + " = " + exp + " ]";
    }

}