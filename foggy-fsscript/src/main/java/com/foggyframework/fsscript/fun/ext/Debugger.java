package com.foggyframework.fsscript.fun.ext;


import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.lang.reflect.InvocationTargetException;


public class Debugger implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		Object[] vv = new Object[args.length];

		for (int i = 0; i < args.length; i++) {
			vv[i] = args[i].evalResult(ee);
		}
		//System.out.println("debugger: " + Arrays.toString(vv));
		return "";
	}

	@Override
	public String getName() {
		return "debugger";
	}

}
