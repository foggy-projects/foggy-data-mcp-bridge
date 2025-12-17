package com.foggyframework.fsscript.fun;

import com.foggyframework.core.trans.IntegerTransFormatter;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


public class Ints   implements FunDef {

	IntegerTransFormatter f = ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE;

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args) {
		int[] ii = new int[args.length];
		int i = 0;
		for (Exp e : args) {
			Object o = e.evalResult(ee);
			ii[i] = (int) (o == null ? 0 : o);

			i++;
		}

		return ii;
	}

	@Override
	public String getName() {
		return "ints";
	}

}
