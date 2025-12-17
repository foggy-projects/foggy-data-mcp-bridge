package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 *
 */
public class LengthPropertyExp extends PropertyExp {
    public LengthPropertyExp(Exp exp, String name) {
        super(exp, name);
    }

    @Override
    public Object getPropertyValue(Object obj) {
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).size();
        }if (obj!=null && obj.getClass().isArray()) {
            return ((Object[])obj).length;
        }if (obj instanceof String) {
            return ((String)obj).length();
        }
        return super.getPropertyValue(obj);
    }
}
