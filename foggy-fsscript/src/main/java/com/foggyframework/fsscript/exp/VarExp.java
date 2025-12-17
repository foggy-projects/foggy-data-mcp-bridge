package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;

@Getter
public class VarExp extends AbstractExp<String> {

    /**
     *
     */
    private static final long serialVersionUID = -1085475866435712298L;
    private Exp exp;

    public VarExp(String value, Exp exp) {
        super(value);
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator){
        Object x;
        if (exp == null) {
            // 未初始化的变量声明: var a;
            x = null;
            evaluator.setVar(value, EmptyExp.EMPTY);
        } else {
            x = exp.evalResult(evaluator);
            if (x == null) {
                evaluator.setVar(value, EmptyExp.EMPTY);
            } else {
                evaluator.setVar(value, x);
            }
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