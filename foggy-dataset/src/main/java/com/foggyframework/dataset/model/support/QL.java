package com.foggyframework.dataset.model.support;

import com.foggyframework.fsscript.fun.Iif;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.utils.ExpUtils;
import lombok.Data;

@Data
public abstract class QL {

	protected Exp expressionExp = null;

	protected Exp ifExp = null;

	protected Exp dsExp;

	public QL() {

	}
	public QL(Exp expressionExp, Exp ifExp, Exp dsExp) {
		this.expressionExp = expressionExp;
		this.ifExp = ifExp;
		this.dsExp = dsExp;
	}

	public final Object evalResult(ExpEvaluator ee) {
		return ExpUtils.eval(ee, expressionExp);// .evalResult(ee);
	}


	public final boolean match(ExpEvaluator ee) {
		if (ifExp == null) {
			return true;
		}
		Object v = null;
		v = ExpUtils.eval(ee, ifExp);

		return Iif.check(v);
	}


}
