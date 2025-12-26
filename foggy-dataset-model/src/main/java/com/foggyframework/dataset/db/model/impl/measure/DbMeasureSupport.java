package com.foggyframework.dataset.db.model.impl.measure;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnSupport;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.db.model.utils.JdbcModelNamedUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.context.ApplicationContext;

@Data
public abstract class DbMeasureSupport extends DbObjectSupport implements DbMeasure {

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

    public void init(TableModel jdbcModel, DbMeasureDef measureDef) {
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

        jdbcColumn = new MeasureDbColumn(jdbcModel.getQueryObject().getSqlColumn(column, true));
    }

    public abstract class MeasureDbColumnSupport extends DbColumnSupport implements DbColumn, DbMeasureColumn {
        public MeasureDbColumnSupport(SqlColumn sqlColumn) {
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
            return DbMeasureSupport.this;
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

    public class MeasureDbColumn extends MeasureDbColumnSupport implements DbColumn {

        public MeasureDbColumn(SqlColumn sqlColumn) {
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
            return DbMeasureSupport.this._isDeprecated();
        }


    }

}
