package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.db.dialect.FDialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalendarRangeCapabilityTest {
    @Test void versionCapabilitiesDoNotChangeOrdinaryWindowSupport() {
        assertTrue(FDialect.SQLSERVER_DIALECT.supportsWindowFunctions());
        assertFalse(FDialect.SQLSERVER_DIALECT.supportsNumericOffsetRange(16,0));
        assertFalse(FDialect.MYSQL_DIALECT.supportsNumericOffsetRange(5,7));
        assertTrue(FDialect.MYSQL8_DIALECT.supportsNumericOffsetRange(8,0));
        assertFalse(FDialect.POSTGRES_DIALECT.supportsNumericOffsetRange(10,9));
        assertTrue(FDialect.POSTGRES_DIALECT.supportsNumericOffsetRange(11,0));
        assertFalse(FDialect.SQLITE_DIALECT.supportsNumericOffsetRange(3,27));
        assertTrue(FDialect.SQLITE_DIALECT.supportsNumericOffsetRange(3,28));
        // Checking a newer connection must never change the capabilities of an older one.
        assertFalse(FDialect.POSTGRES_DIALECT.supportsNumericOffsetRange(10,9));
    }

    @Test void offsetRangeValidationRetainsRowsAndUnboundedRange() {
        var range=new WindowFrame(WindowFrame.Unit.RANGE,new WindowFrame.Bound.Preceding(6),new WindowFrame.Bound.CurrentRow());
        assertThrows(IllegalArgumentException.class,()->range.validateOffsetRange("mssql",1));
        assertThrows(IllegalArgumentException.class,()->range.validateOffsetRange("mysql57",1));
        assertThrows(IllegalArgumentException.class,()->range.validateOffsetRange("postgres",2));
        assertThrows(IllegalArgumentException.class,()->range.validateOffsetRange("sqlite",0));
        assertDoesNotThrow(()->range.validateOffsetRange("mysql8",1));
        assertDoesNotThrow(()->WindowFrame.rollingRows(7).validateOffsetRange("mssql",1));
        assertDoesNotThrow(()->new WindowFrame(WindowFrame.Unit.RANGE,new WindowFrame.Bound.UnboundedPreceding(),
                new WindowFrame.Bound.CurrentRow()).validateOffsetRange("mssql",1));
    }
}
