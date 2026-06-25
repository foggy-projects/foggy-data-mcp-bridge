package com.foggyframework.fsscript.support;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsscriptRuntimeFailureTest {

    @Test
    void evalShouldNotWrapSeriousErrors() {
        AssertionError seriousError = new AssertionError("serious script failure");
        Exp exp = mock(Exp.class);
        when(exp.evalValue(any())).thenThrow(seriousError);

        FsscriptImpl fsscript = new FsscriptImpl(mock(FsscriptClosureDefinition.class), exp);

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> fsscript.eval(mock(ExpEvaluator.class)));

        assertSame(seriousError, thrown);
    }
}
