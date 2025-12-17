package com.foggyframework.fsscript.fun;


import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.exp.PropertyExp;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.util.Assert;

/**
 * i++
 *
 * @author fengjianguang
 */

public class P_IJJ extends AbstractFunDef implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {


        if (args[0] instanceof IdExp) {
            String varName = null;
            varName = ((IdExp) args[0]).getValue();
            Number i = ((Number) args[0].evalResult(ee));
            Assert.notNull(i, "i++表达式中，i不得为空");
            Number n = null;
            // 变化varName,但返回的是未变化前的值
            if (i instanceof Integer) {
                n = 1 + i.intValue();
            } else {
                n = 1 + i.doubleValue();
            }
            ee.setParentVarFirst(varName, n);
            return i;
        } else if (args[0] instanceof PropertyExp) {
            PropertyExp pp = ((PropertyExp) args[0]);
            Object v = pp.getExp().evalResult(ee);
            if (v == null) {
                throw RX.throwB("++或--不能有空值");
            }
            Number n = (Number) pp.getPropertyValue(v);
            Number before = n;
            if (n == null) {
                n = 0;
            }
            if (n instanceof Integer) {
                n = 1 + n.intValue();
            } else {
                n = 1 + n.doubleValue();
            }
            pp.setPropertyValue(v, n);
            return before;

        } else {
            throw RX.throwB("++或--必须作用在ID变量上");
        }
    }

    @Override
    public String getName() {
        return "P_IJJ";
    }

}
