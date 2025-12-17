package com.foggyframework.fsscript.fun;


import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


public class BitwiseAnd extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		// Boolean v1 = ((Boolean) args[0].evalResult(ee));
		// Boolean v2 = ((Boolean) args[1].evalResult(ee));
		// boolean i = v1 == null ? false : v1.booleanValue();
		// boolean i2 = v2 == null ? false : v2.booleanValue();
		// return i && i2;

		Number v1 = (Number) args[0].evalResult(ee);
		Number v2 = (Number) args[1].evalResult(ee);
		if(v1 == null || v2 == null){
			return 0;
		}
		return v1.longValue() & v2.longValue();
	}

	@Override
	public String getName() {
		return "&";
	}

}
