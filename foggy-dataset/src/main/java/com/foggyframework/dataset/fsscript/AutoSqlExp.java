/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.dataset.fsscript;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.fsscript.fun.Iif;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * sqlexp(${xx}," and t.xx=?")
 *
 * @author Foggy
 */

public class AutoSqlExp implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args)
            throws IllegalArgumentException {
        if (!(evaluator instanceof QueryExpEvaluator)) {
            throw new UnsupportedOperationException();
        }
        QueryExpEvaluator queryExpEvaluator = (QueryExpEvaluator) evaluator;

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

        String sqlFrame = (String) sqlFrameExp.evalResult(evaluator);

        int l = StringUtils.countOfChar(sqlFrame, '?');
        for (int i = 0; i < l; i++) {
            queryExpEvaluator.addArg(value);
        }
        return sqlFrame;
    }

    @Override
    public String getName() {
        return "autoSqlexp";
    }


}
