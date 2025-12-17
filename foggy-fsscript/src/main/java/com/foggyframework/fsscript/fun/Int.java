package com.foggyframework.fsscript.fun;



import com.foggyframework.core.trans.IntegerTransFormatter;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;



public class Int   implements FunDef {

	IntegerTransFormatter f = ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE;

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		Object obj = args[0].evalResult(ee);
		if (obj == null) {
			return 0;
		}
		return f.format(obj);
	}

	@Override
	public String getName() {
		return "int";
	}

}
