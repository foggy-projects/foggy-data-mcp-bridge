package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;

import org.springframework.context.ApplicationContext;

/**
 * select ${declare} ${alias}
 * <p>
 * eg. declare = 'tx.aaa' , alias='b'
 * <p>
 * select tx.aaa b
 */
public interface DbColumn extends DbObject {

    default String getDeclare() {
        return getQueryObject().getAlias() + "." + getSqlColumnName();
    }

    default String getDeclare(ApplicationContext appCtx,String alias) {
        return (StringUtils.isEmpty(alias) ? getQueryObject().getAlias() : alias) + "." + getSqlColumnName();
    }
    default String getDeclareOrder(ApplicationContext appCtx,String alias) {
        return getDeclare(appCtx,alias);
    }

    default String getSqlColumnName() {
        return getSqlColumn().getName();
    }

    String getAlias();

    default String getField() {
        return getAlias();
    }

    QueryObject getQueryObject();

    SqlColumn getSqlColumn();

    default ObjectTransFormatter<?> getFormatter() {
        return getSqlColumn().getFormatter();
    }
    default ObjectTransFormatter<?> getFormatter(boolean errorIfNull) {
        if (errorIfNull && getSqlColumn().getFormatter() == null) {
            throw new RuntimeException("column " + getSqlColumn().getName() + " formatter is null,可能是该字段使用了系统不支持的类型~");
        }
        return getSqlColumn().getFormatter();
    }
    default String buildSqlFragment(ApplicationContext appCtx,String alias, String s) {
//        return (StringUtils.isEmpty(alias) ? getQueryObject().getAlias() : alias) + "." + getSqlColumn().getName() + " " + s + " ";
        return getDeclare(appCtx,alias) + " " + s + " ";
    }

    default DbAggregation getAggregation() {
        return null;
    }

    /**
     * JdbcColumnType
     * @return
     */
    DbColumnType getType();

    default boolean isMeasure() {
        return false;
    }

    default boolean isDimension() {
        return false;
    }

    default boolean isProperty() {
        return false;
    }

    /**
     * 是否为计算字段
     * <p>
     * 计算字段是通过表达式动态计算得出的虚拟列，
     * 在查询请求中通过 calculatedFields 参数定义。
     * </p>
     *
     * @return true 如果是计算字段
     */
    default boolean isCalculatedField() {
        return false;
    }

    default boolean isCountColumn() {
        return false;
    }

    default String getAggregationFormula() {
        return null;
    }
}
