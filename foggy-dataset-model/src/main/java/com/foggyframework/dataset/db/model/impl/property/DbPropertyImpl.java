package com.foggyframework.dataset.db.model.impl.property;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
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
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class DbPropertyImpl extends DbObjectSupport implements DbProperty, DbDataProvider {

    TableModel tableModel;

    DbDimension dbDimension;

    String alias;

    String aggregationFormula;


    DbColumnType type;

    String format;

    DbColumn jdbcColumn;

    Map<String, Object> extData;

    String column;

    PropertyDbColumn propertyDbColumn;

    boolean bit;

    FsscriptFunction formulaBuilder;

    BigDecimal semanticScaleFactor;

    String semanticUnit;

    String semanticUnitLabel;

    /**
     * 字典引用ID，引用通过 registerDict 注册的字典
     */
    String dictRef;

    /**
     * 时间角色语义 (e.g. business_date, event_time, system_time)
     */
    String timeRole;

    /**
     * 推荐用途说明
     */
    String recommendedUse;

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
        RX.hasText(column, "属性的column不能为空," + ("模型：" + tableModel));

        if (StringUtils.isEmpty(alias)) {
            alias = JdbcModelNamedUtils.toAliasName(column);
        }
        if (StringUtils.isEmpty(name)) {
            name = alias;
        }

        // 使用安全方式创建 PropertyDbColumn，支持错误收集
        propertyDbColumn = new PropertyDbColumn();

        if (extData != null && extData.get("bit") instanceof Boolean) {
            bit = (Boolean) extData.get("bit");
        }
    }

    public void validateSemanticScaleContract(DbFormulaDef formulaDef,
                                              Map<String, DbFormulaDef> dialectFormulaDef) {
        boolean hasFormula = formulaDef != null || (dialectFormulaDef != null && !dialectFormulaDef.isEmpty());
        SemanticScaleSqlSupport.validate(semanticScaleFactor, column, hasFormula,
                StringUtils.isEmpty(name) ? column : name);
    }

//    @Override
//    public List<JdbcColumn> getVisibleSelectColumns() {
//
//        return Arrays.asList(propertyJdbcColumn);
//    }

    public class PropertyDbColumn extends DbColumnSupport implements DbPropertyColumn {
        private final QueryObject queryObjRef;

        public PropertyDbColumn() {
            super(initSqlColumn(dbDimension, tableModel, column, name, type));
            this.queryObjRef = resolveQueryObject(dbDimension, tableModel);
        }

        private static QueryObject resolveQueryObject(DbDimension dbDimension, TableModel tableModel) {
            if (dbDimension == null || dbDimension.getQueryObject() == null) {
                return tableModel.getQueryObject();
            }
            return dbDimension.getQueryObject();
        }

        /**
         * 初始化 SqlColumn，使用错误收集机制
         */
        private static SqlColumn initSqlColumn(DbDimension dbDimension, TableModel tableModel,
                                                String column, String name, DbColumnType type) {
            try {
                // 确定查询对象
                QueryObject queryObject = resolveQueryObject(dbDimension, tableModel);

                // 使用安全方式获取 SqlColumn
                SqlColumn sqlColumn = queryObject.getSqlColumn(column, false);

                if (sqlColumn == null) {
                    // 字段不存在，记录错误
                    handleColumnNotFound(queryObject, dbDimension, tableModel, column, name, type);
                    // 返回占位 SqlColumn
                    return new SqlColumn(column, "VARCHAR", java.sql.Types.VARCHAR, 255);
                }

                return sqlColumn;

            } catch (Exception e) {
                // 捕获其他异常
                handleInitError(e, tableModel, column, name, type);
                return new SqlColumn(column, "VARCHAR", java.sql.Types.VARCHAR, 255);
            }
        }

        /**
         * 处理字段不存在的情况
         */
        private static void handleColumnNotFound(QueryObject queryObject, DbDimension dbDimension,
                                                   TableModel tableModel, String column, String name, DbColumnType type) {
            String location = String.format("property.%s", name);
            String tableName = queryObject.getName();
            String dimensionInfo = dbDimension != null
                    ? String.format(" (from dimension '%s')", dbDimension.getName())
                    : "";

            ModelLoadError error = ModelLoadError.builder()
                    .errorType(ModelLoadError.ErrorType.COLUMN_NOT_FOUND)
                    .errorLevel(ModelLoadError.ErrorLevel.ERROR)
                    .location(location)
                    .message(String.format("字段 [%s] 在表 [%s] 中不存在%s",
                            column, tableName, dimensionInfo))
                    .details(String.format("Property '%s' references column '%s' which does not exist in table '%s'%s",
                            name, column, tableName, dimensionInfo))
                    .build();

            // 添加错误到模型
            if (tableModel instanceof TableModelSupport) {
                ((TableModelSupport) tableModel).addLoadError(error);
            }
        }

        /**
         * 处理初始化异常
         */
        private static void handleInitError(Exception e, TableModel tableModel,
                                              String column, String name, DbColumnType type) {
            String location = String.format("property.%s", name);

            ModelLoadError error = ModelLoadError.builder()
                    .errorType(ModelLoadError.ErrorType.OTHER)
                    .errorLevel(ModelLoadError.ErrorLevel.ERROR)
                    .location(location)
                    .message(String.format("属性初始化失败: %s", e.getMessage()))
                    .details(String.format("Property '%s' (column '%s') initialization failed",
                            name, column))
                    .cause(e)
                    .build();

            // 添加错误到模型
            if (tableModel instanceof TableModelSupport) {
                ((TableModelSupport) tableModel).addLoadError(error);
            }
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
                return SemanticScaleSqlSupport.scaledDeclare(
                        super.getDeclare(appCtx, alias),
                        semanticScaleFactor);
            } else {
                DefaultExpEvaluator expEvaluator = DefaultExpEvaluator.newInstance(appCtx);
                String effectiveAlias = StringUtils.isEmpty(alias) ? queryObjRef.getAlias() : alias;
                expEvaluator.setVar("alias", effectiveAlias);
                expEvaluator.setVar("def", this);
                return (String) formulaBuilder.autoApply(expEvaluator);
            }
        }

        @Override
        public String getDeclare() {
            if (formulaBuilder == null) {
                return SemanticScaleSqlSupport.scaledDeclare(super.getDeclare(), semanticScaleFactor);
            }
            DefaultExpEvaluator expEvaluator = DefaultExpEvaluator.newInstance(null);
            expEvaluator.setVar("alias", queryObjRef.getAlias());
            expEvaluator.setVar("def", this);
            return (String) formulaBuilder.autoApply(expEvaluator);
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
            return queryObjRef;
        }

        @Override
        public DbColumnType getType() {
            return type;
        }

        @Override
        public ObjectTransFormatter<?> getFormatter() {
            return getPropertyFormatter(super.getFormatter());
        }

        @Override
        public ObjectTransFormatter<?> getFormatter(boolean errorIfNull) {
            return getPropertyFormatter(super.getFormatter(errorIfNull));
        }

        private ObjectTransFormatter<?> getPropertyFormatter(ObjectTransFormatter<?> fallback) {
            if (type != null && type != DbColumnType.UNKNOWN) {
                return type.getFormatter();
            }
            return fallback;
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
