package com.foggyframework.dataset.db.model.impl.measure;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnSupport;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.SemanticScaleSqlSupport;
import com.foggyframework.dataset.db.model.impl.column.InvalidDbColumn;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.utils.JdbcModelNamedUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.util.Map;

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

    BigDecimal semanticScaleFactor;

    String semanticUnit;

    String semanticUnitLabel;

    public void init(TableModel jdbcModel, DbMeasureDef measureDef) {
        this.jdbcModel = jdbcModel;
        if (StringUtils.isEmpty(column) && StringUtils.equalsIgnoreCase(measureDef.getAggregation(), "COUNT")) {
            //呃，COUNT下可以不用指定列名，但简单起见，我们使用id吧
            column = jdbcModel.getIdColumn();
        }
        RX.hasText(column, "列名不得为空:" + this.caption + "," + this.alias + ",model:" + jdbcModel.getName());
        validateSemanticScaleContract(measureDef);
        if (StringUtils.isEmpty(alias)) {
            alias = JdbcModelNamedUtils.toAliasName(column);
        }

        if (StringUtils.isEmpty(name)) {
            name = JdbcModelNamedUtils.toAliasName(column);
        }

        // 使用安全方式获取 SqlColumn，支持错误收集
        SqlColumn sqlColumn = initSqlColumn();
        jdbcColumn = new MeasureDbColumn(sqlColumn);
    }

    private void validateSemanticScaleContract(DbMeasureDef measureDef) {
        boolean hasFormula = measureDef != null
                && (measureDef.getFormulaDef() != null || hasDialectFormula(measureDef.getDialectFormulaDef()));
        SemanticScaleSqlSupport.validate(semanticScaleFactor, column, hasFormula,
                StringUtils.isEmpty(name) ? column : name);
    }

    private boolean hasDialectFormula(Map<String, DbFormulaDef> dialectFormulaDef) {
        return dialectFormulaDef != null && !dialectFormulaDef.isEmpty();
    }

    /**
     * 初始化 SqlColumn，使用错误收集机制
     */
    private SqlColumn initSqlColumn() {
        try {
            // 使用安全方式获取 SqlColumn
            SqlColumn sqlColumn = jdbcModel.getQueryObject().getSqlColumn(column, false);

            if (sqlColumn == null) {
                // 字段不存在，记录错误
                handleColumnNotFound();
                // 返回占位 SqlColumn
                return new SqlColumn(column, "DECIMAL", java.sql.Types.DECIMAL, 18);
            }

            return sqlColumn;

        } catch (Exception e) {
            // 捕获其他异常
            handleInitError(e);
            return new SqlColumn(column, "DECIMAL", java.sql.Types.DECIMAL, 18);
        }
    }

    /**
     * 处理字段不存在的情况
     */
    private void handleColumnNotFound() {
        String location = String.format("measure.%s", name);
        String tableName = jdbcModel.getQueryObject().getName();

        ModelLoadError error = ModelLoadError.builder()
                .errorType(ModelLoadError.ErrorType.COLUMN_NOT_FOUND)
                .errorLevel(ModelLoadError.ErrorLevel.ERROR)
                .location(location)
                .message(String.format("度量字段 [%s] 在表 [%s] 中不存在", column, tableName))
                .details(String.format("Measure '%s' references column '%s' which does not exist in table '%s'",
                        name, column, tableName))
                .build();

        // 添加错误到模型
        if (jdbcModel instanceof TableModelSupport) {
            ((TableModelSupport) jdbcModel).addLoadError(error);
        }

        // 标记 jdbcColumn 为无效
        this.jdbcColumn = new InvalidDbColumn(
                column,
                "Column not found in table " + tableName,
                jdbcModel.getQueryObject(),
                type
        );
    }

    /**
     * 处理初始化异常
     */
    private void handleInitError(Exception e) {
        String location = String.format("measure.%s", name);

        ModelLoadError error = ModelLoadError.builder()
                .errorType(ModelLoadError.ErrorType.OTHER)
                .errorLevel(ModelLoadError.ErrorLevel.ERROR)
                .location(location)
                .message(String.format("度量初始化失败: %s", e.getMessage()))
                .details(String.format("Measure '%s' (column '%s') initialization failed",
                        name, column))
                .cause(e)
                .build();

        // 添加错误到模型
        if (jdbcModel instanceof TableModelSupport) {
            ((TableModelSupport) jdbcModel).addLoadError(error);
        }

        // 标记 jdbcColumn 为无效
        this.jdbcColumn = new InvalidDbColumn(
                column,
                "Initialization failed: " + e.getMessage(),
                null,
                type
        );
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
            return SemanticScaleSqlSupport.scaledDeclare(
                    getQueryObject().getAlias() + "." + column,
                    semanticScaleFactor);
        }

        @Override
        public String getDeclare(ApplicationContext appCtx, String alias) {
            if (formulaBuilder == null) {
                String baseDeclare = (StringUtils.isEmpty(alias) ? getQueryObject().getAlias() : alias)
                        + "." + getSqlColumnName();
                return SemanticScaleSqlSupport.scaledDeclare(baseDeclare, semanticScaleFactor);
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
            return getDeclare(appCtx, alias);
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
