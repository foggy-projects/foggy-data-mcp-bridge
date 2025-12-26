package com.foggyframework.dataset.jdbc.model.impl.property;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.impl.JdbcColumnSupport;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.utils.JdbcModelNamedUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@Getter
@Setter
public class DbPropertyImpl extends JdbcObjectSupport implements DbProperty, DbDataProvider {

    TableModel jdbcModel;

    DbDimension dbDimension;

    String alias;

    String aggregationFormula;


    DbColumnType type;

    String format;

    DbColumn jdbcColumn;

    Map<String, Object> extData;

    String column;

    PropertyDbColumn propertyJdbcColumn;

    boolean bit;

    FsscriptFunction formulaBuilder;

    /**
     * 字典引用ID，引用通过 registerDict 注册的字典
     */
    String dictRef;

    @Override
    public <T> T getExtDataValue(String key) {
        return extData == null ? null : (T) extData.get(key);
    }

    @Override
    public DbDataProvider getDataProvider() {
        return this;
    }

    @Override
    public DbDimensionType getDimensionType() {
        return DbDimensionType.fromColumnType(type);
    }

    @Override
    public boolean isBit() {
        return bit;
    }

    
    @Override
    public boolean isDict() {
        return type == DbColumnType.DICT || StringUtils.isNotEmpty(dictRef);
    }

    @Override
    public String getDictRef() {
        return dictRef;
    }

    @Override
    public void setFormulaBuilder(FsscriptFunction builder) {
        this.formulaBuilder = builder;
    }

    public void init() {
        RX.hasText(column, "属性的column不能为空," + ("模型：" + jdbcModel));

        if (StringUtils.isEmpty(alias)) {
            alias = JdbcModelNamedUtils.toAliasName(column);
        }
        if (StringUtils.isEmpty(name)) {
            name = alias;
        }

        propertyJdbcColumn = new PropertyDbColumn();
        if (extData != null && extData.get("bit") instanceof Boolean) {
            bit = (Boolean) extData.get("bit");
        }
    }

//    @Override
//    public List<JdbcColumn> getVisibleSelectColumns() {
//
//        return Arrays.asList(propertyJdbcColumn);
//    }

    public class PropertyDbColumn extends JdbcColumnSupport implements DbPropertyColumn {
        public PropertyDbColumn() {
            super(dbDimension == null ?
                    jdbcModel.getQueryObject().getSqlColumn(column, true)
                    : dbDimension.getQueryObject().getSqlColumn(column, true));
        }
        @Override
        public Object getExtData() {
            return extData;
        }

        @Override
        public AiObject getAi() {
            return ai;
        }

        
        @Override
        public String getDeclare(ApplicationContext appCtx, String alias) {
            if (formulaBuilder == null) {
                return super.getDeclare(appCtx, alias);
            } else {
                DefaultExpEvaluator expEvaluator = DefaultExpEvaluator.newInstance(appCtx);
                expEvaluator.setVar("alias", alias);
                expEvaluator.setVar("def", this);
                return (String) formulaBuilder.autoApply(expEvaluator);
            }
        }

        @Override
        public String getAggregationFormula() {
            return aggregationFormula;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }

        @Override
        public DbColumnType getType() {
            return type;
        }

        @Override
        public String getCaption() {
            return caption;
        }

        @Override
        public String getName() {
            return name;
        }


        public boolean isDimension() {
            return false;
        }

        public boolean isProperty() {
            return true;
        }

        @Override
        public DbProperty getProperty() {
            return DbPropertyImpl.this;
        }
    }


}
