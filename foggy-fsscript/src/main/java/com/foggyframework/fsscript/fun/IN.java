package com.foggyframework.fsscript.fun;

import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class IN implements FunDef {

    /**
     * (item, index) in state.roleList
     *
     * @param ee
     * @param args
     * @return
     */
    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        Assert.isTrue(args
                .length == 2, "in 表达式的参数必须是2个");
        UnresolvedFunCall unresolvedFunCall = (UnresolvedFunCall) args[0];
        //(item, index)
        List<Exp> ll = unresolvedFunCall.getArgs();

        String itemName = ((IdExp) ll.get(0)).value;
        String indexName = ((IdExp) ll.get(1)).value;
        Object obj = args[1].evalResult(ee);

        return new InResult(itemName, indexName, obj);
    }

    @Override
    public String getName() {
        return "in";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InResult {
        String itemName;
        String indexName;
        Object inValue;

        public void forEach(ExpEvaluator ee, BiConsumer<Object, Integer> consumer) {
            Iterator iterator;
            if (inValue instanceof Collection) {
                iterator = ((Collection<?>) inValue).iterator();
            } else if (inValue instanceof Iterator) {
                iterator = (Iterator) inValue;
            } else if (inValue instanceof Integer) {
                int length = (int) inValue;
                for (int i = 0; i < length; i++) {
                    ee.setVar(indexName, i);
                    ee.setVar(itemName, i);
                    consumer.accept(i, i);
                }
                return;
            } else if (inValue == null) {
                return;
            } else {
                throw new UnsupportedOperationException("不支持的inValue:" + inValue);
            }
            int idx = 0;
            while (iterator.hasNext()) {
                Object v = iterator.next();
                ee.setVar(indexName, idx);
                ee.setVar(itemName, v);
                consumer.accept(v, idx);
                idx++;
            }
        }
    }
}
