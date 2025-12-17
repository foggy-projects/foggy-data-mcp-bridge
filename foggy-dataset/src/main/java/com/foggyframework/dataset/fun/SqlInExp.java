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
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Collection;

/**
 * 
 * 
 * sqlexp(xx," and t.xx in ")
 * 
 * @author Foggy
 * 
 */

public class SqlInExp  implements FunDef {

	@Override
	public Object execute(ExpEvaluator evaluator, Exp[] args) {
		if (!(evaluator instanceof QueryExpEvaluator)) {
			throw new UnsupportedOperationException();
		}
		QueryExpEvaluator queryExpEvaluator = (QueryExpEvaluator) evaluator;

		Exp valueExp = args[0];
		Exp sqlFrameExp = args[1];
		boolean force = false;
		if (args.length > 2) {
			force = (boolean) args[2].evalResult(evaluator);
		}

		Object value = valueExp.evalResult(evaluator);
		if (StringUtils.isEmpty(value) && !force) {
			return "";
		}

		Object[] sqlArgs = null;

		if (value instanceof Collection) {
			sqlArgs = ((Collection) value).toArray();
		} else if (value instanceof Object[]) {
			sqlArgs = (Object[]) value;
		} else {
			sqlArgs = new Object[] { value };
		}

		if (sqlArgs.length == 0) {
			if(force) {
				return sqlFrameExp.evalResult(evaluator) + " (1) and 1=2";        //呃，确实应该是没有数据
			}else {
				return "";
			}

		} else {
			StringBuilder sb = new StringBuilder("(");
			for (Object obj : sqlArgs) {
				queryExpEvaluator.addArg(obj);
				sb.append("?").append(",");
				
			}
			sb.replace(sb.length() - 1, sb.length(), ")");
			String sqlFrame = (String) sqlFrameExp.evalResult(evaluator) + sb.toString();

			return sqlFrame;
		}

	}

	@Override
	public String getName() {
		return "sqlInExp";
	}

}
