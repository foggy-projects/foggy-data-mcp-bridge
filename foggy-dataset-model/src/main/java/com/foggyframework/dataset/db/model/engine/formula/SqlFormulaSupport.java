package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnRenderContext;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

public abstract class SqlFormulaSupport implements SqlFormula {

    protected ApplicationContext appCtx;

    public SqlFormulaSupport(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    @Override
    public Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, String link) {
//        String name = sqlColumn.getName();
        if (StringUtils.isEmpty(value)) {
            return buildAndAddEmptyToJdbcCond(listCond, type, sqlColumn, alias, value, link);
        }
        if (value instanceof List) {
            List<?> original = (List<?>) value;
            List<Object> v = new ArrayList<>(original.size());
            for (Object item : original) {
                v.add(formatValue(sqlColumn, type, item));
            }
            return buildAndAddListSqlToJdbcCond(listCond, type, sqlColumn, alias, v, link);
        } else {
            return buildAndAddObjectToJdbcCond(listCond, type, sqlColumn, alias, sqlColumn.isCalculatedField() ? value : formatValue(sqlColumn, type, value), link);
        }
    }

    @Override
    public Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn,
                                        String alias, Object value, String link, FDialect dialect) {
        try (DbColumnRenderContext.Scope ignored = DbColumnRenderContext.useDialect(dialect)) {
            return buildAndAddToJdbcCond(listCond, type, sqlColumn, alias, value, link);
        }
    }

    private Object formatValue(DbColumn sqlColumn, String type, Object value) {
        try {
            return sqlColumn.getFormatter(true).format(value);
        } catch (NumberFormatException | ClassCastException e) {
            String actualType = value == null ? "null" : value.getClass().getSimpleName();
            throw RX.throwAUserTip(
                    DatasetMessages.validationSliceValueFormatInvalid(sqlColumn.getName(), type, actualType),
                    DatasetMessages.systemException(),
                    null,
                    e);
        }
    }

    protected abstract Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, List<Object> values, String link);

    protected abstract Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, String link);

    protected abstract Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, String link);

    protected void throwOnlySupportListError() {
        throw RX.throwAUserTip(DatasetMessages.formulaListRequired());
    }

    protected void throwOnlySupportObjectError() {
        throw RX.throwAUserTip(DatasetMessages.formulaObjectRequired());
    }
}
