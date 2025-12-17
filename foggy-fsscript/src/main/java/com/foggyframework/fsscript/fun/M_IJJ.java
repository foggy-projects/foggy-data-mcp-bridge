package com.foggyframework.fsscript.fun;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * i++
 * 
 * @author fengjianguang
 *
 */

public class M_IJJ extends AbstractFunDef implements FunDef {

	@Override
	public Object execute(ExpEvaluator ee, Exp[] args){
		Number i = ((Number) args[0].evalResult(ee));

		String varName = null;
		if (args[0] instanceof IdExp) {
			varName = ((IdExp) args[0]).getValue();
		} else {
			throw RX.throwB("++或--必须作用在ID变量上");
		}

		Number n = null;
		// 变化varName,但返回的是未变化前的值
		if (i instanceof Integer) {
			n =  i.intValue()-1;
		} else {
			n =  i.doubleValue()-1;
		}
		ee.setParentVarFirst(varName, n);
		return i;
	}

	@Override
	public String getName() {
		return "M_IJJ";
	}

}
