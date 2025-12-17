/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun.ext;

import com.foggyframework.core.trans.DateTransFormatter;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.DateUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * sqlexp(${xx}," and t.xx=?")
 *
 * @author Foggy
 */

public final class ToDateFunDef extends AbstractFunDef implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object str = args[0].evalResult(evaluator);
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        if(str instanceof Date){
            return str;
        }
        if(args.length>1){
            String format = (String) args[1].evalResult(evaluator);
            if(StringUtils.isNotEmpty(format)&&str instanceof String){
                try {
                    return new SimpleDateFormat(format).parse((String) str);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        return DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(str);
    }

    @Override
    public String getName() {
        return "toDate";
    }

}
