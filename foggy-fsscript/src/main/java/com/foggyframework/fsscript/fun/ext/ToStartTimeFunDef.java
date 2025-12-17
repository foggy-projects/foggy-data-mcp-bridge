/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun.ext;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.DateUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * sqlexp(${xx}," and t.xx=?")
 *
 * @author Foggy
 */
public final class ToStartTimeFunDef extends AbstractFunDef implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object str = args[0].evalResult(evaluator);
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        if (str instanceof Date) {
            return DateUtils.toStartTime((Date) str);
        }
        Date d = null;
        if (str.toString().length() > 14) {
            d = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(str.toString());
            d = DateUtils.toStartTime(d);
        } else {
            d = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.formatHMS(str + " 00:00:00");
        }

        return d;
    }

    @Override
    public String getName() {
        return "toStartTime";
    }

}
