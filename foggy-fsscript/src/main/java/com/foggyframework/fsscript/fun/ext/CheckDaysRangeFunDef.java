/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun.ext;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.DateTransFormatter;
import com.foggyframework.core.utils.DateUtils;
import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * sqlexp(${xx}," and t.xx=?")
 *
 * @author Foggy
 */

public final class CheckDaysRangeFunDef extends AbstractFunDef implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        if (args.length < 3) {
            throw RX.throwAUserTip("checkTimes函数至少需要三个参数：起始时间、终止时间、最大时间/天", "功能错误");
        }
        Object d1 = args[0].evalResult(evaluator);
        Object d2 = args[1].evalResult(evaluator);
        Integer maxTime = (Integer) args[2].evalResult(evaluator);
        RX.notNull(d1, "起始时间不能为空");
        RX.notNull(d2, "终止时间不能为空");
        RX.notNull(maxTime, "最大时间/天不能为空");

        String msg = null;
        if (args.length > 3) {
            msg = (String) args[3].evalResult(evaluator);
        }
        if (msg == null) {
            msg = "查询最大时间间隔不得超过" + maxTime + "天";
        }
        int days = DateUtils.days(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(d1), DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(d2));
        if (days > maxTime) {
            throw RX.throwAUserTip(msg);
        }
        return days;
    }

    @Override
    public String getName() {
        return "checkDaysRange";
    }

}
