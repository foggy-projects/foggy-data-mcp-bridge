package com.foggyframework.fsscript.exp.switch_exp;

import com.foggyframework.fsscript.exp.BreakExp;
import com.foggyframework.fsscript.exp.ReturnExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractCaseExp implements Exp {

    List<Exp> xx;

    @Override
    public Object evalValue(ExpEvaluator ee) {
        if (xx != null) {
            for (Exp exp : xx) {
                Object v = exp.evalValue(ee);
                if(v instanceof ReturnExpObject){
                    return v;
                }
                if (v == BreakExp.BREAK) {
                    return BreakExp.BREAK;
                }
            }
        }

        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return null;
    }

    public abstract boolean match(Object condValue, ExpEvaluator ee);
}