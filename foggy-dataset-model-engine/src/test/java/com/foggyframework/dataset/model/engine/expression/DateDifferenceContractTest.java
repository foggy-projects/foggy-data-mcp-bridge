package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.db.dialect.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DateDifferenceContractTest {
    @Test void unsupportedDialectAndSqliteMicrosecondsFailClosed() {
        FDialect unknown=mock(FDialect.class);when(unknown.getProductName()).thenReturn("unknown");
        assertEquals("DATE_FUNCTION_DIALECT_UNSUPPORTED",assertThrows(DateDifferenceSql.InvalidCall.class,
                ()->DateDifferenceSql.render(unknown,"DATEDIFF",List.of("a","b"))).getCode());
        assertEquals("DATE_FUNCTION_UNIT_UNSUPPORTED",assertThrows(DateDifferenceSql.InvalidCall.class,
                ()->DateDifferenceSql.render(new SqliteDialect(),"TIMESTAMPDIFF",List.of("'MICROSECOND'","a","b"))).getCode());
    }
    @Test void totalRebindingUsesSameDialectContractAndNewReferences() {
        var expression=TotalExpressionNode.customFunction("TIMESTAMPDIFF",List.of(TotalExpressionNode.raw("'HOUR'"),
                TotalExpressionNode.reference("start","old_start"),TotalExpressionNode.reference("end","old_end")));
        for(FDialect dialect:List.of(new SqliteDialect(),new MysqlDialect(),new PostgresDialect(),new SqlServerDialect())) {
            String sql=expression.render(dialect,name->"state_"+name,leaf->"unused");
            assertFalse(sql.contains("old_"));
            assertEquals(DateDifferenceSql.render(dialect,"TIMESTAMPDIFF",List.of("'HOUR'","state_start","state_end")),sql);
        }
    }
    @Test void unitCannotInjectSqlOrAcceptAnArbitraryExpression() {
        for(String unit:List.of("'HOUR); DROP TABLE x'","NULL","'HOUR' || 'DAY'","1"))
            assertEquals("DATE_FUNCTION_UNIT_INVALID",assertThrows(DateDifferenceSql.InvalidCall.class,
                    ()->DateDifferenceSql.render(new SqliteDialect(),"TIMESTAMPDIFF",List.of(unit,"a","b"))).getCode());
    }
}
