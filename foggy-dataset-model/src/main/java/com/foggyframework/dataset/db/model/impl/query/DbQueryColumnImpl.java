package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.util.Map;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryColumnImpl extends DbObjectSupport implements DbQueryColumn {

    @Delegate(excludes = {DbObject.class})
    DbColumn selectColumn;

    DbQueryCondition dbQueryCondition;

    /**
     * 别名（alias）
     * <p>用户在 QM 中定义的列别名，用于避免多模型 JOIN 时重名问题。
     * <p>示例：在 QM 中定义 { ref: dc.customerType, alias: 'custType' }，
     * 则 alias 为 'custType'，用于在查询结果中重命名该字段。
     * <p>作用：
     * <ul>
     *   <li>在 SQL SELECT 子句中作为列别名（AS alias）</li>
     *   <li>在查询条件中作为字段名（WHERE alias = ?）</li>
     *   <li>在返回结果中作为字段名</li>
     * </ul>
     */
    String alias;

    boolean hasRef;

    protected ObjectTransFormatter<?> valueFormatter;

    public <C> C getDecorate(Class<C> cls) {
        if (cls.isInstance(this)) {
            return cls.cast(this);
        } else {
            return this.selectColumn != null ? this.selectColumn.getDecorate(cls) : null;
        }
    }

    public Object getRoot() {
        return this.selectColumn.getRoot();
    }

    public boolean isInDecorate(Object obj) {
        return super.isInDecorate(obj) || this.selectColumn.isInDecorate(obj);
    }

//    public JdbcQueryColumnImpl( JdbcColumn selectColumn) {
//        this.selectColumn = selectColumn;
//    }

    public DbQueryColumnImpl(DbColumn selectColumn, String name, String caption, String alias) {
        this.selectColumn = selectColumn;
        this.name = name;
        this.caption = caption;
        this.alias = alias;
    }

//    public JdbcQueryColumnImpl(JdbcColumn selectColumn, JdbcQueryCond jdbcQueryCond) {
//        this.selectColumn = selectColumn;
//        this.jdbcQueryCond = jdbcQueryCond;
//    }

    @Override
    public AiObject getAi() {
        if (ai != null) {
            return ai;
        }
        return selectColumn.getAi();
    }

    @Override
    public String getCaption() {
        if (StringUtils.isNotEmpty(caption)) {
            return caption;
        }
        return selectColumn.getCaption();
    }

    @Override
    public String getName() {
        if (StringUtils.isNotEmpty(name)) {
            return name;
        }
        return selectColumn.getName();
    }

    @Override
    public String getField() {
        if (StringUtils.isNotEmpty(alias)) {
            return alias;
        }
        return selectColumn.getField();
    }
    @Override
    public String getDescription() {
        if (StringUtils.isNotEmpty(description)) {
            return description;
        }
        return selectColumn.getDescription();
    }
    @Override
    public boolean _isDeprecated() {
        if (super._isDeprecated()) {
            return true;
        }
        return selectColumn != null && selectColumn._isDeprecated();
    }

    @Override
    public String getAlias() {
        if (StringUtils.isNotEmpty(alias)) {
            return alias;
        }
        return selectColumn.getAlias();
    }

    @Override
    public QueryObject getQueryObject() {
        return selectColumn.getQueryObject();
    }

    @Override
    public SqlColumn getSqlColumn() {
        return selectColumn.getSqlColumn();
    }

    @Override
    public DbQueryCondition getDbQueryCond() {
        return dbQueryCondition;
    }

//    @Override
//    public String getType() {
//        return selectColumn.getType();
//    }
//
//    @Override
//    public String getAggregationFormula() {
//        return selectColumn.getAggregationFormula();
//    }
//
//    @Override
//    public boolean isMeasure() {
//        return selectColumn.isMeasure();
//    }
//
//    @Override
//    public boolean isDimension() {
//        return selectColumn.isDimension();
//    }
//
//    @Override
//    public boolean isProperty() {
//        return selectColumn.isProperty();
//    }
//
//    @Override
//    public boolean isCountColumn() {
//        return selectColumn.isCountColumn();
//    }


}
