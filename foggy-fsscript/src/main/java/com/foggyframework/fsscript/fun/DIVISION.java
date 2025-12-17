package com.foggyframework.fsscript.fun;



import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;


/**
 * 注意，它永远返回double 型，如果有需要返回int,请自行调用int等函数
 */
public class DIVISION  implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		Number n = (Number) args[0].evalResult(ee);
		Number n1 = (Number) args[1].evalResult(ee);
		if (n == null) {
			return 0.0;
		}
		if (n1 == null) {
			return Double.NaN;
		}

		return n.doubleValue() / n1.doubleValue();
	}

	@Override
	public String getName() {
		return "/";
	}

}
