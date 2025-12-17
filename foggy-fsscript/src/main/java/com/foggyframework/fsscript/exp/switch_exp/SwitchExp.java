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
public class SwitchExp implements Exp {
    /**
     * switch (test) 中的test表达式～
     */
    Exp e;

    List<AbstractCaseExp> list;

    @Override
    public Object evalValue(ExpEvaluator ee) {

        if (list == null) {
            return null;
        }
        Object condValue = e.evalValue(ee);
        Object obj = null;

        boolean hasBreak = true;
        for (AbstractCaseExp caseExp : list) {
            if (hasBreak) {
                if (caseExp.match(condValue, ee)) {
                    obj = caseExp.evalValue(ee);
                    if (obj == BreakExp.BREAK) {
                        hasBreak = true;
                        break;
                    } else if (obj instanceof ReturnExpObject) {
                        return obj;
                    } else {
                        hasBreak = false;
                    }
                }
            } else {
                //当没有break时，不用判断
                obj = caseExp.evalValue(ee);
                if (obj == BreakExp.BREAK) {
                    hasBreak = true;
                    break;
                } else if (obj instanceof ReturnExpObject) {
                    return obj;
                } else {
                    hasBreak = false;
                }
            }
        }

        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return null;
    }
}
