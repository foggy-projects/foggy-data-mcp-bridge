package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SqlFormulaSupport formatter validation tests")
class SqlFormulaSupportTest {

    @Test
    @DisplayName("formatter conversion failures are wrapped as slice.value contract errors")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void formatterConversionFailureWrappedAsContractError() {
        DbColumn column = mock(DbColumn.class);
        ObjectTransFormatter formatter = mock(ObjectTransFormatter.class);
        when(column.getFormatter(true)).thenReturn(formatter);
        when(column.getName()).thenReturn("orderDate$id");
        when(column.isCalculatedField()).thenReturn(false);
        when(formatter.format("2026-05-01")).thenThrow(new NumberFormatException("For input string"));

        SqlFormulaSupport formula = new CapturingSqlFormula();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> formula.buildAndAddToJdbcCond(null, "=", column, null, "2026-05-01", "AND"));

        assertTrue(exception.getMessage().contains("slice.value"));
        assertTrue(exception.getMessage().contains("orderDate$id"));
        assertFalse(exception.getMessage().contains("NumberFormatException"));
    }

    private static class CapturingSqlFormula extends SqlFormulaSupport {

        CapturingSqlFormula() {
            super(null);
        }

        @Override
        public String[] getNameList() {
            return new String[]{"="};
        }

        @Override
        protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond,
                                                      String type,
                                                      DbColumn sqlColumn,
                                                      String alias,
                                                      List<Object> values,
                                                      String link) {
            return values;
        }

        @Override
        protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond,
                                                    String type,
                                                    DbColumn sqlColumn,
                                                    String alias,
                                                    Object value,
                                                    String link) {
            return value;
        }

        @Override
        protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond,
                                                     String type,
                                                     DbColumn sqlColumn,
                                                     String alias,
                                                     Object value,
                                                     String link) {
            return value;
        }
    }
}
