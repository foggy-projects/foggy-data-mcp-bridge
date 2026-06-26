package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComparisonOperatorTest {

    @Test
    void lessThanShouldCompareNonNumberValuesLikeGreaterThan() {
        assertEquals(true, eval("return '2019-05-01' < '2019-06-30';"));
        assertEquals(false, eval("return '2019-06-30' < '2019-05-01';"));
        assertEquals(false, eval("return '2019-05-01' < '2019-05-01';"));
    }

    @Test
    void lessThanOrEqualShouldCompareNonNumberValuesLikeGreaterThanOrEqual() {
        assertEquals(true, eval("return '2019-05-01' <= '2019-06-30';"));
        assertEquals(true, eval("return '2019-05-01' <= '2019-05-01';"));
        assertEquals(false, eval("return '2019-06-30' <= '2019-05-01';"));
    }

    @Test
    void greaterThanOperatorsAlreadySupportNonNumberValues() {
        assertEquals(true, eval("return '2019-06-30' > '2019-05-01';"));
        assertEquals(false, eval("return '2019-05-01' > '2019-06-30';"));
        assertEquals(true, eval("return '2019-06-30' >= '2019-05-01';"));
        assertEquals(true, eval("return '2019-06-30' >= '2019-06-30';"));
    }

    @Test
    void numericLessThanOperatorsKeepExistingBehavior() {
        assertEquals(true, eval("return 1 < 2;"));
        assertEquals(false, eval("return 2 < 1;"));
        assertEquals(true, eval("return 2 <= 2;"));
        assertEquals(false, eval("return 3 <= 2;"));
    }

    private Object eval(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        return exp.evalResult(DefaultExpEvaluator.newInstance());
    }
}
