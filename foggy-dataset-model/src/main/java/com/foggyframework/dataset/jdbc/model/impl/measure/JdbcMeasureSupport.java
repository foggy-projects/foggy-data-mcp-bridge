package com.foggyframework.dataset.jdbc.model.impl.measure;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcMeasureDef;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.impl.JdbcColumnSupport;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.utils.JdbcModelNamedUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.context.ApplicationContext;

@Data
public abstract class JdbcMeasureSupport extends JdbcObjectSupport implements DbMeasure {

    String column;
    //默认聚合方式为sum
    DbAggregation aggregation;

    DbColumn jdbcColumn;

    TableModel jdbcModel;

    /**
     * JdbcColumnType
     */
    DbColumnType type;

    String alias;

    @ApiModelProperty("公式描述")
    DbFormulaDef formulaDef;

    FsscriptFunction formulaBuilder;

    public void init(TableModel jdbcModel, JdbcMeasureDef measureDef) {
        this.jdbcModel = jdbcModel;
        if (StringUtils.isEmpty(column) && StringUtils.equalsIgnoreCase(measureDef.getAggregation(), "COUNT")) {
            //呃，COUNT下可以不用指定列名，但简单起见，我们使用id吧
            column = jdbcModel.getIdColumn();
        }
        RX.hasText(column, "列名不得为空:" + this.caption + "," + this.alias + ",model:" + jdbcModel.getName());
        if (StringUtils.isEmpty(alias)) {
            alias = JdbcModelNamedUtils.toAliasName(column);
        }

        if (StringUtils.isEmpty(name)) {
            name = JdbcModelNamedUtils.toAliasName(column);
        }

        jdbcColumn = new MeasureJdbcColumn(jdbcModel.getQueryObject().getSqlColumn(column, true));
    }

    public abstract class MeasureJdbcColumnSupport extends JdbcColumnSupport implements DbColumn, DbMeasureColumn {
        public MeasureJdbcColumnSupport(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public AiObject getAi() {
            return ai;
        }

        @Override
        public DbFormulaDef getFormulaDef() {
            return formulaDef;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }

        @Override
        public DbAggregation getAggregation() {
            return aggregation;
        }

        @Override
        public DbColumnType getType() {
            return type;
        }

        @Override
        public DbMeasure getJdbcMeasure() {
            return JdbcMeasureSupport.this;
        }

        public boolean isMeasure() {
            return true;
        }

        @Override
        public boolean isCountColumn() {
            return (aggregation != null) && (aggregation == DbAggregation.COUNT);
        }

        @Override
        public String getAlias() {
            return alias;
        }
    }

    public class MeasureJdbcColumn extends MeasureJdbcColumnSupport implements DbColumn {

        public MeasureJdbcColumn(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        

        @Override
        public String getDeclare() {
            return getQueryObject().getAlias() + "." + column;
        }

        @Override
        public String getDeclare(ApplicationContext appCtx, String alias) {
            if (formulaBuilder == null) {
                return (StringUtils.isEmpty(alias) ? getQueryObject().getAlias() : alias) + "." + getSqlColumnName();
            } else {
                DefaultExpEvaluator expEvaluator = DefaultExpEvaluator.newInstance(appCtx);
                expEvaluator.setVar("alias", alias);
                expEvaluator.setVar("def", this);
                return (String) formulaBuilder.autoApply(expEvaluator);
            }
        }

        @Override
        public Object getExtData() {
            return extData;
        }

        @Override
        public String getDeclareOrder(ApplicationContext appCtx, String alias) {
            return (StringUtils.isEmpty(alias) ? getQueryObject().getAlias() : alias) + "." + getSqlColumnName();
        }

        @Override
        public String getAlias() {
            return alias;
        }


        @Override
        public String getCaption() {
            return caption;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean _isDeprecated() {
            return JdbcMeasureSupport.this._isDeprecated();
        }


    }

}
