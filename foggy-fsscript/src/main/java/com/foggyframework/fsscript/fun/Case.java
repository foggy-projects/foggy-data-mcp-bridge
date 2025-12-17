package com.foggyframework.fsscript.fun;





import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 
 * case(cell,0,'正常',1,'坏账',2,'完成',3,'延期')
 * 
 * @author Foggy
 *
 */
public class Case implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args)
			{
		if (args.length % 2 == 0) {
			throw new RuntimeException("函数 case 的参数必须是奇数");
		}

		Object v = args[0].evalResult(ee);
		Object v1 = null;
		for (int i = 1; i < args.length; i++) {
			v1 = args[i].evalResult(ee);
			i++;
			if (Equal.eq(v, v1)) {
				return args[i].evalResult(ee);
			}
		}
		return null;
	}

	@Override
	public String getName() {
		return "case";
	}

}
