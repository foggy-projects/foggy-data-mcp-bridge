package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

public abstract class SqlFormulaSupport implements SqlFormula {

    protected ApplicationContext appCtx;

    public SqlFormulaSupport(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    @Override
    public Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {
//        String name = sqlColumn.getName();
        if (StringUtils.isEmpty(value)) {
            return buildAndAddEmptyToJdbcCond(listCond, type, sqlColumn, alias, value, link);
        }
        if (value instanceof List) {
            List<?> original = (List<?>) value;
            List<Object> v = new ArrayList<>(original.size());
            for (Object item : original) {
                v.add(sqlColumn.getFormatter(true).format(item));
            }
            return buildAndAddListSqlToJdbcCond(listCond, type, sqlColumn, alias, v, link);
        } else {
            return buildAndAddObjectToJdbcCond(listCond, type, sqlColumn, alias, sqlColumn.isCalculatedField() ? value : sqlColumn.getFormatter(true).format(value), link);
        }
    }

    protected abstract Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, List<Object> values, int link);

    protected abstract Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link);

    protected abstract Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link);

    protected void throwOnlySupportListError() {
        throw RX.throwAUserTip(DatasetMessages.formulaListRequired());
    }

    protected void throwOnlySupportObjectError() {
        throw RX.throwAUserTip(DatasetMessages.formulaObjectRequired());
    }
}
