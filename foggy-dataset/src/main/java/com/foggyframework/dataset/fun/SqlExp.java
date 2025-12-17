/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.fun;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.fsscript.fun.Iif;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.util.Assert;

/**
 * 
 * 
 * sqlexp(${xx}," and t.xx=?")
 * 
 * @author Foggy
 * 
 */

public class SqlExp  implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args)
			throws IllegalArgumentException {
		if (!(evaluator instanceof QueryExpEvaluator)) {
			throw new UnsupportedOperationException("需要QueryExpEvaluator");
		}
		QueryExpEvaluator queryExpEvaluator = (QueryExpEvaluator) evaluator;
		Assert.isTrue(args.length>=2,"SqlExp的参数不得小于2个");
		Exp valueExp = args[0];
		Exp sqlFrameExp = args[1];
		boolean force = false;
		if (args.length > 2) {
			force = Iif.check(args[2].evalResult(evaluator));
		}

		Object value = valueExp.evalResult(evaluator);
		if (StringUtils.isEmpty(value) && !force) {
			return "";
		}
		queryExpEvaluator.addArg(value);

		String sqlFrame = (String) sqlFrameExp.evalResult(evaluator);

		return sqlFrame;
	}

	@Override
	public String getName() {
		return "sqlexp";
	}

}
