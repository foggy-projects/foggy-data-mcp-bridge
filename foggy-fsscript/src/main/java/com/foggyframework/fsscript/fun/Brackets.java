package com.foggyframework.fsscript.fun;


import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


/**
 * 似乎没有必要存在,可以通过在编译时直接返回对应的args[0]
 *
 * @author foggy
 */

public class Brackets implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {

        return args[0].evalResult(ee);
    }

    @Override
    public String getName() {
        return "()";
    }

}
