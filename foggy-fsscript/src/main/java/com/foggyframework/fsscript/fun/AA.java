package com.foggyframework.fsscript.fun;


import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


public class AA extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		// Boolean v1 = ((Boolean) args[0].evalResult(ee));
		// Boolean v2 = ((Boolean) args[1].evalResult(ee));
		// boolean i = v1 == null ? false : v1.booleanValue();
		// boolean i2 = v2 == null ? false : v2.booleanValue();
		// return i && i2;

		Object v1 = (args[0].evalResult(ee));
		Object v2 = (args[1].evalResult(ee));

		return Iif.check(v1) && Iif.check(v2);
	}

	@Override
	public String getName() {
		return "&&";
	}

}
