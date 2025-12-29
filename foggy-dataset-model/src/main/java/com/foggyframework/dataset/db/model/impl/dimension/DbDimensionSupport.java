package com.foggyframework.dataset.db.model.impl.dimension;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.common.result.DbDataItem;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnSupport;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.path.DimensionPath;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.DbDataProviderDelegate;
import com.foggyframework.dataset.db.model.utils.JdbcModelNamedUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.RowMapperUtils;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.sql.DataSource;
import java.util.*;

@Getter
@Setter
public abstract class DbDimensionSupport extends DbObjectSupport implements DbDimension, DbDataProvider {

    TableModel jdbcModel;

    QueryObject queryObject;
    /**
     * 主表上与该 维度对应的列
     */
    String foreignKey;

    String primaryKey;

    String parentKey;

    String captionColumn;


    String forceIndex;


    String primaryKeyAlias;

    /**
     * JdbcDimensionType
     */
    DbDimensionType type;

    DbColumn primaryKeyDbColumn;


    DbColumn foreignKeyDbColumn;


    DbColumn captionDbColumn;

    Map<String, Object> extData;

    List<DbProperty> jdbcProperties = new ArrayList<>();

    List<DimensionPropertyDbColumn> propertyDbColumns = new ArrayList<>();

    FsscriptFunction dimensionDataSql;

    FsscriptFunction onBuilder;

    String joinTo;

    // ========== 嵌套维度支持 ==========

    /**
     * 维度别名，用于在QM中简化列名访问,如果为空，会使用name的值进行初始化
     */
    String alias;

    /**
     * 父维度（如果是嵌套维度）
     */
    DbDimension parentDimension;

    /**
     * 子维度列表
     */
    List<DbDimension> childDimensions = new ArrayList<>();

    String keyCaption;

    /**
     * 维表主键字段的 description，用于描述 $id 字段的详细说明
     */
    String keyDescription;

    /**
     * 维度路径（懒加载）
     */
    private transient DimensionPath dimensionPath;

    @Override
    public DimensionPath getDimensionPath() {
        if (dimensionPath == null) {
            dimensionPath = buildDimensionPath();
        }
        return dimensionPath;
    }

    /**
     * 构建维度路径
     */
    private DimensionPath buildDimensionPath() {
        if (parentDimension == null) {
            return DimensionPath.of(name);
        }
        return parentDimension.getDimensionPath().append(name);
    }

    @Override
    public void addChildDimension(DbDimension child) {
        if (child instanceof DbDimensionSupport) {
            ((DbDimensionSupport) child).setParentDimension(this);
        }
        childDimensions.add(child);
    }

    @Override
    public DbProperty addProperty(DbProperty property) {
        /**
         * 检查数据
         */
        if (findPropertyByName(property.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateProperty(property.getName()));
        }

        property.getDecorate(DbPropertyImpl.class).setDbDimension(this);
        jdbcProperties.add(property);
        return property;
    }

    @Override
    public DbProperty findPropertyByName(String name) {
        for (DbProperty property : jdbcProperties) {
            if (StringUtils.equals(property.getName(), name)) {
                return property;
            }
        }
        return null;
    }

    @Override
    public <T> T getExtDataValue(String key) {
        return extData == null ? null : (T) extData.get(key);
    }

    public void init() {
        RX.hasText(foreignKey, "主表的foreignKey不能为空");


        if (queryObject != null) {
            //有独立的维表
            //从维表取captionJdbcColumn
            captionDbColumn = new DimensionCaptionDbColumn(queryObject, queryObject.getSqlColumn(captionColumn, true));
            RX.hasText(primaryKey, String.format("维度%s没有定义主键", name));
            primaryKeyDbColumn = new DimensionPrimaryKeyDbColumn(queryObject.getSqlColumn(primaryKey, true));
            if (StringUtils.isEmpty(type)) {
                type = DbDimensionType.NORMAL;
            }
            if (StringUtils.isEmpty(primaryKeyAlias)) {
                primaryKeyAlias = JdbcModelNamedUtils.toAliasName(primaryKey);
            }
            queryObject.setForceIndex(forceIndex);
            queryObject.setOnBuilder(onBuilder);
        } else {
            //没有独立的维表
            if (StringUtils.isEmpty(captionColumn)) {
                captionColumn = foreignKey;
            }
            if (StringUtils.isNotEmpty(captionColumn)) {
                //从主表取captionJdbcColumn
                captionDbColumn = new DimensionCaptionDbColumn(jdbcModel.getQueryObject(), jdbcModel.getQueryObject().getSqlColumn(captionColumn, true));
            }
        }


        // 处理嵌套维度：如果有父维度，自动设置 joinTo 并配置 JOIN 关系
        if (parentDimension != null && StringUtils.isEmpty(joinTo)) {
            // 自动设置 joinTo 为父维度名称
            this.joinTo = parentDimension.getName();
        }

        if (StringUtils.isEmpty(joinTo)) {
            // 顶层维度：外键在主表上
            foreignKeyDbColumn = new DimensionDbColumn(jdbcModel.getQueryObject().getSqlColumn(foreignKey, true));

        } else if (queryObject != null) {
            // 嵌套维度或 joinTo 维度：外键在父维度表上
            QueryObject parentQueryObject;
            if (parentDimension != null) {
                // 嵌套维度：从父维度获取 QueryObject
                parentQueryObject = parentDimension.getQueryObject();
            } else {
                // 兼容旧的 joinTo 方式：从模型查找维度
                parentQueryObject = jdbcModel.findJdbcDimensionByName(joinTo).getQueryObject();
            }
            queryObject.setLinkQueryObject(parentQueryObject);
            // 在父 QueryObject 上注册子 QueryObject 的外键，用于 JOIN 条件生成
            parentQueryObject.registerChildForeignKey(queryObject, foreignKey);
            foreignKeyDbColumn = new JoinToDimensionDbColumn(parentQueryObject, parentQueryObject.getSqlColumn(foreignKey, true));
        }


        for (DbProperty property : jdbcProperties) {
            SqlColumn sqlColumn = property.getPropertyDbColumn().getSqlColumn();
            DimensionPropertyDbColumn pc = new DimensionPropertyDbColumn(sqlColumn, property);
            propertyDbColumns.add(pc);
        }

    }

    public class DimensionPropertyDbColumn extends DimensionDbColumnSupport implements DbPropertyColumn, DbProperty {

        String alias;

        DbProperty property;

        FsscriptFunction formulaBuilder;

        public DimensionPropertyDbColumn(SqlColumn sqlColumn, DbProperty property) {
            super(sqlColumn);
            this.property = property;
        }

        @Override
        public String getCaption() {
            return property.getCaption();
        }

        @Override
        public QueryObject getQueryObject() {
            return DbDimensionSupport.this.queryObject;
        }

        @Override
        public String getDescription() {
            return property.getDescription();
        }

        @Override
        public String getAlias() {
            if (alias == null) {
                // 使用维度的别名路径（下划线分隔，支持嵌套维度）来构建属性列名
                String fullPathAlias = getFullPathForAlias();
                alias = fullPathAlias + "$" + property.getPropertyDbColumn().getAlias();
            }
            return alias;
        }

        @Override
        public String getName() {
            return getAlias();
        }


        @Override
        public boolean isDimension() {
            return false;
        }

        @Override
        public boolean isProperty() {
            return true;
        }

        @Override
        public DbColumnType getType() {
            return property.getType();
        }

        @Override
        public DbProperty getProperty() {
            return this;
        }

        /**
         * impl JdbcProperty
         *
         * @return
         */
        @Override
        public DbColumn getPropertyDbColumn() {
            return this;
        }

        @Override
        public TableModel getTableModel() {
            return jdbcModel;
        }

        @Override
        public String getFormat() {
            return property.getFormat();
        }

        @Override
        public <T> T getExtDataValue(String key) {
            return property.getExtDataValue(key);
        }

        @Override
        public DbDataProvider getDataProvider() {
            return new DbDataProviderDelegate(property.getDataProvider()) {
                @Override
                public String getName() {
                    return DimensionPropertyDbColumn.this.getName();
                }
            };
        }

        @Override
        public boolean isBit() {
            return false;
        }

        @Override
        public void setFormulaBuilder(FsscriptFunction builder) {
            this.formulaBuilder = builder;
        }
    }

    @Override
    public List<DbColumn> getVisibleSelectColumns() {
        if (captionDbColumn == null) {
            return Collections.EMPTY_LIST;
        }
        return Arrays.asList(captionDbColumn);
    }

    public class DimensionPrimaryKeyDbColumn extends DimensionDbColumnSupport implements DbColumn {

        String keyAlias;
//        String keyName;

        public DimensionPrimaryKeyDbColumn(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }




        @Override
        public String getAlias() {
            if (keyAlias == null) {
                // 使用维度的别名路径（下划线分隔，支持嵌套维度）
                keyAlias = getFullPathForAlias() + "$id";
            }
            return keyAlias;
        }

        @Override
        public String getCaption() {
            return keyCaption;
        }

        @Override
        public String getName() {
//            if (keyName == null) {
//                keyName = name + "." + primaryKeyAlias;
//            }
            return getAlias();//name + "." + primaryKeyAlias;
        }
    }

    @ToString
    public abstract class DimensionDbColumnSupport extends DbColumnSupport implements DbColumn, DbDimensionColumn {
        public DimensionDbColumnSupport(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public AiObject getAi() {
            return ai;
        }


        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public DbColumnType getType() {
            return DbColumnType.fromJdbcType(sqlColumn.getJdbcType());
        }
        @Override
        public Object getExtData() {
            return extData;
        }

        @Override
        public String getCaption() {
            return caption;
        }


        @Override
        public DbDimensionSupport getDimension() {
            return DbDimensionSupport.this;
        }

        @Override
        public boolean isCaptionColumn() {
            return false;
        }

        public boolean isDimension() {
            return true;
        }
    }

    public class JoinToDimensionDbColumn extends DimensionDbColumn implements DbColumn {
        String aliasName;
        QueryObject queryObject;

        public JoinToDimensionDbColumn(QueryObject queryObject, SqlColumn sqlColumn) {
            super(sqlColumn);
            this.queryObject = queryObject;

        }

        @Override
        public QueryObject getQueryObject() {
            return queryObject;
        }

        @Override
        public String getName() {
            if (aliasName == null) {
                // 使用维度的别名路径（下划线分隔，支持嵌套维度）
                aliasName = getFullPathForAlias() + "$id";
            }
            return aliasName;
        }

        @Override
        public String getAlias() {
            return getName();
        }
    }

    public class DimensionDbColumn extends DimensionDbColumnSupport implements DbColumn {
        String aliasName;

        public DimensionDbColumn(SqlColumn sqlColumn) {
            super(sqlColumn);

        }


        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }

        @Override
        public String getName() {
            if (aliasName == null) {
                // 使用维度的别名路径（下划线分隔，支持嵌套维度）
                aliasName = getFullPathForAlias() + "$id";
            }
            return aliasName;
        }

        @Override
        @Deprecated
        public String getAlias() {
            return getName();
        }
    }

    @ToString
    @Getter
    public class DimensionCaptionDbColumn extends DimensionDbColumnSupport implements DbColumn {

        QueryObject queryObject;
        String captionAlias;

        public DimensionCaptionDbColumn(QueryObject queryObject, SqlColumn sqlColumn) {
            super(sqlColumn);
            this.queryObject = queryObject;
        }

        @Override
        public String getName() {
            if (captionAlias == null) {
                // 使用维度的别名路径（下划线分隔，支持嵌套维度）
                captionAlias = getFullPathForAlias() + "$caption";
            }
            return captionAlias;
        }

        @Override
        public boolean isCaptionColumn() {
            return true;
        }

        @Override
        public String getAlias() {
            return getName();
        }

    }

    @Override
    public boolean isQueryObject(QueryObject joinObject) {
        if (queryObject == null) {
            return queryObject == joinObject;
        }
        return queryObject.isRootEqual(joinObject);
    }

    @Override
    public List<DbDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbDimension dbDimension, String hierarchy) {

        //生成维表的查询语句

        if (dimensionDataSql != null) {
            QueryExpEvaluator qee = QueryExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
            Object sql = dimensionDataSql.autoApply(qee);
            if (sql instanceof String) {
                List<DbDataItem> ll = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().query((String) sql, qee.getArgs().toArray(), RowMapperUtils.getRowMapper(DbDataItem.class));
                return ll;
            } else {
                return (List<DbDataItem>) sql;
//                throw new UnsupportedOperationException();
            }
        }

        JdbcQuery query = new JdbcQuery();
        query.from(queryObject);
        query.select(primaryKeyDbColumn.getSqlColumn().getName(), "id");
        query.select(captionDbColumn.getSqlColumn().getName(), "caption");
        SimpleSqlJdbcQueryVisitor visitor = new SimpleSqlJdbcQueryVisitor();
        query.accept(visitor);
        String sql = visitor.getSql();

        List<DbDataItem> ll = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().query(sql, visitor.getValues().toArray(new Object[0]), RowMapperUtils.getRowMapper(DbDataItem.class));

        return ll;
    }

    @Override
    public List<DbColumn> getAllDbColumns() {
        List<DbColumn> ll = new ArrayList<>();
        ll.add(this.foreignKeyDbColumn);

        if (this.captionDbColumn != null && this.foreignKeyDbColumn.getSqlColumn() != this.captionDbColumn.getSqlColumn()) {
            ll.add(this.captionDbColumn);
        }

        ll.addAll(propertyDbColumns);
        return ll;
    }

    @Override
    public DbDataProvider getDataProvider() {
        return this;
    }

    @Override
    public DbDimensionType getDimensionType() {
        return type;
    }


}
