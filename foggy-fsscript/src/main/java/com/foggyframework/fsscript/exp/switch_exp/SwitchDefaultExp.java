package com.foggyframework.fsscript.exp.switch_exp;

import com.foggyframework.fsscript.exp.BreakExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public class SwitchDefaultExp extends AbstractCaseExp {

    public SwitchDefaultExp(List xx) {
        super(xx);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return null;
    }

    @Override
    public boolean match(Object condValue, ExpEvaluator ee) {
        return true;
    }
}
