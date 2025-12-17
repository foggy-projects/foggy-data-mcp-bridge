package com.foggyframework.fsscript.fun;


import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.stereotype.Component;

public class SleepFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args) {

		try {
			long sleep = 500;
			if (args.length > 0) {
				Object obj = args[0].evalResult(ee);
				if (obj instanceof Number) {
					sleep = ((Number) obj).longValue();
				} else {
					throw new RuntimeException("sleep方法的参数必须是数字");
				}
			}

			Thread.sleep(sleep);
			return null;
		} catch (InterruptedException | IllegalArgumentException  e) {
			throw ErrorUtils.toRuntimeException(e);// .printStackTrace();
		}

	}

	@Override
	public String getName() {
		return "sleep";
	}

}
