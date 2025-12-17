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
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.fun.AbstractFunDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.*;

/**
 * sqlexp(${xx}," and t.xx=?")
 *
 * @author Foggy
 */

public final class ClearEmptyDef extends AbstractFunDef implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object str = args[0].evalResult(evaluator);
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        if (str instanceof Map) {
            List<Object> keys = new ArrayList<>();
            Map<Object, Object> map = ((Map<Object, Object>) str);
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                if (StringUtils.isEmpty(e.getValue())) {
                    keys.add(e.getKey());
                }
            }
            for (Object key : keys) {
                map.remove(key);
            }
            return map;
        } else {
            BeanInfoHelper b = BeanInfoHelper.getClassHelper(str.getClass());
            Map<Object, Object> map = new HashMap<>();
            for (BeanProperty readMethod : b.getReadMethods()) {
                Object v = readMethod.getBeanValue(str);
                if (StringUtils.isNotEmpty(v)) {
                    map.put(readMethod.getName(), v);
                }
            }

            return map;
        }


    }

    @Override
    public String getName() {
        return "clearEmpty";
    }

}
