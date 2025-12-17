package com.foggyframework.dataset.jdbc.model.impl.dimension;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.impl.JdbcColumnSupport;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.impl.property.JdbcPropertyImpl;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.support.JdbcDataProviderDelegate;
import com.foggyframework.dataset.jdbc.model.utils.JdbcModelNamedUtils;
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
public abstract class JdbcDimensionSupport extends JdbcObjectSupport implements JdbcDimension, JdbcDataProvider {

    JdbcModel jdbcModel;

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
    JdbcDimensionType type;

    JdbcColumn primaryKeyJdbcColumn;


    JdbcColumn foreignKeyJdbcColumn;


    JdbcColumn captionJdbcColumn;

    Map<String, Object> extData;

    List<JdbcProperty> jdbcProperties = new ArrayList<>();

    List<DimensionPropertyJdbcColumn> propertyJdbcColumns = new ArrayList<>();

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
    JdbcDimension parentDimension;

    /**
     * 子维度列表
     */
    List<JdbcDimension> childDimensions = new ArrayList<>();

    String keyCaption;

    /**
     * 维表主键字段的 description，用于描述 $id 字段的详细说明
     */
    String keyDescription;

    @Override
    public void addChildDimension(JdbcDimension child) {
        if (child instanceof JdbcDimensionSupport) {
            ((JdbcDimensionSupport) child).setParentDimension(this);
        }
        childDimensions.add(child);
    }

    @Override
    public JdbcProperty addJdbcProperty(JdbcProperty property) {
        /**
         * 检查数据
         */
        if (findJdbcPropertyByName(property.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateProperty(property.getName()));
        }

        property.getDecorate(JdbcPropertyImpl.class).setJdbcDimension(this);
        jdbcProperties.add(property);
        return property;
    }

    @Override
    public JdbcProperty findJdbcPropertyByName(String name) {
        for (JdbcProperty property : jdbcProperties) {
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
            captionJdbcColumn = new DimensionCaptionJdbcColumn(queryObject, queryObject.getSqlColumn(captionColumn, true));
            RX.hasText(primaryKey, String.format("维度%s没有定义主键", name));
            primaryKeyJdbcColumn = new DimensionPrimaryKeyJdbcColumn(queryObject.getSqlColumn(primaryKey, true));
            if (StringUtils.isEmpty(type)) {
                type = JdbcDimensionType.NORMAL;
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
                captionJdbcColumn = new DimensionCaptionJdbcColumn(jdbcModel.getQueryObject(), jdbcModel.getQueryObject().getSqlColumn(captionColumn, true));
            }
        }


        // 处理嵌套维度：如果有父维度，自动设置 joinTo 并配置 JOIN 关系
        if (parentDimension != null && StringUtils.isEmpty(joinTo)) {
            // 自动设置 joinTo 为父维度名称
            this.joinTo = parentDimension.getName();
        }

        if (StringUtils.isEmpty(joinTo)) {
            // 顶层维度：外键在主表上
            foreignKeyJdbcColumn = new DimensionJdbcColumn(jdbcModel.getQueryObject().getSqlColumn(foreignKey, true));

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
            foreignKeyJdbcColumn = new JoinToDimensionJdbcColumn(parentQueryObject, parentQueryObject.getSqlColumn(foreignKey, true));
        }


        for (JdbcProperty property : jdbcProperties) {
            SqlColumn sqlColumn = property.getPropertyJdbcColumn().getSqlColumn();
            DimensionPropertyJdbcColumn pc = new DimensionPropertyJdbcColumn(sqlColumn, property);
            propertyJdbcColumns.add(pc);
        }

    }

    public class DimensionPropertyJdbcColumn extends DimensionJdbcColumnSupport implements JdbcPropertyColumn, JdbcProperty {

        String alias;

        JdbcProperty property;

        FsscriptFunction formulaBuilder;

        public DimensionPropertyJdbcColumn(SqlColumn sqlColumn, JdbcProperty property) {
            super(sqlColumn);
            this.property = property;
        }

        @Override
        public String getCaption() {
            return property.getCaption();
        }

        @Override
        public QueryObject getQueryObject() {
            return JdbcDimensionSupport.this.queryObject;
        }

        @Override
        public String getDescription() {
            return property.getDescription();
        }

        @Override
        public String getAlias() {
            if (alias == null) {
                // 使用维度的有效名称（优先别名）来构建属性列名
                String effectiveDimName = getEffectiveName();
                alias = effectiveDimName + "$" + property.getPropertyJdbcColumn().getAlias();
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
        public JdbcColumnType getType() {
            return property.getType();
        }

        @Override
        public JdbcProperty getJdbcProperty() {
            return this;
        }

        /**
         * impl JdbcProperty
         *
         * @return
         */
        @Override
        public JdbcColumn getPropertyJdbcColumn() {
            return this;
        }

        @Override
        public JdbcModel getJdbcModel() {
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
        public JdbcDataProvider getDataProvider() {
            return new JdbcDataProviderDelegate(property.getDataProvider()) {
                @Override
                public String getName() {
                    return DimensionPropertyJdbcColumn.this.getName();
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
    public List<JdbcColumn> getVisibleSelectColumns() {
        if (captionJdbcColumn == null) {
            return Collections.EMPTY_LIST;
        }
        return Arrays.asList(captionJdbcColumn);
    }

    public class DimensionPrimaryKeyJdbcColumn extends DimensionJdbcColumnSupport implements JdbcColumn {

        String keyAlias;
//        String keyName;

        public DimensionPrimaryKeyJdbcColumn(SqlColumn sqlColumn) {
            super(sqlColumn);
        }

        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }




        @Override
        public String getAlias() {
            if (keyAlias == null) {
                // 使用维度的有效名称（优先别名）
                keyAlias = getEffectiveName() + "$id";
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
    public abstract class DimensionJdbcColumnSupport extends JdbcColumnSupport implements JdbcColumn, JdbcDimensionColumn {
        public DimensionJdbcColumnSupport(SqlColumn sqlColumn) {
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
        public JdbcColumnType getType() {
            return JdbcColumnType.fromJdbcType(sqlColumn.getJdbcType());
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
        public JdbcDimensionSupport getJdbcDimension() {
            return JdbcDimensionSupport.this;
        }

        @Override
        public boolean isCaptionColumn() {
            return false;
        }

        public boolean isDimension() {
            return true;
        }
    }

    public class JoinToDimensionJdbcColumn extends DimensionJdbcColumn implements JdbcColumn {
        String aliasName;
        QueryObject queryObject;

        public JoinToDimensionJdbcColumn(QueryObject queryObject, SqlColumn sqlColumn) {
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
                // 使用维度的有效名称（优先别名）
                aliasName = getEffectiveName() + "$id";
            }
            return aliasName;
        }

        @Override
        public String getAlias() {
            return getName();
        }
    }

    public class DimensionJdbcColumn extends DimensionJdbcColumnSupport implements JdbcColumn {
        String aliasName;

        public DimensionJdbcColumn(SqlColumn sqlColumn) {
            super(sqlColumn);

        }


        @Override
        public QueryObject getQueryObject() {
            return jdbcModel.getQueryObject();
        }

        @Override
        public String getName() {
            if (aliasName == null) {
                // 使用维度的有效名称（优先别名）
                aliasName = getEffectiveName() + "$id";
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
    public class DimensionCaptionJdbcColumn extends DimensionJdbcColumnSupport implements JdbcColumn {

        QueryObject queryObject;
        String captionAlias;

        public DimensionCaptionJdbcColumn(QueryObject queryObject, SqlColumn sqlColumn) {
            super(sqlColumn);
            this.queryObject = queryObject;
        }

        @Override
        public String getName() {
            if (captionAlias == null) {
                // 使用维度的有效名称（优先别名）
                captionAlias = getEffectiveName() + "$caption";
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
    public List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, JdbcDimension jdbcDimension, String hierarchy) {

        //生成维表的查询语句

        if (dimensionDataSql != null) {
            QueryExpEvaluator qee = QueryExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
            Object sql = dimensionDataSql.autoApply(qee);
            if (sql instanceof String) {
                List<JdbcDataItem> ll = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().query((String) sql, qee.getArgs().toArray(), RowMapperUtils.getRowMapper(JdbcDataItem.class));
                return ll;
            } else {
                return (List<JdbcDataItem>) sql;
//                throw new UnsupportedOperationException();
            }
        }

        JdbcQuery query = new JdbcQuery();
        query.from(queryObject);
        query.select(primaryKeyJdbcColumn.getSqlColumn().getName(), "id");
        query.select(captionJdbcColumn.getSqlColumn().getName(), "caption");
        SimpleSqlJdbcQueryVisitor visitor = new SimpleSqlJdbcQueryVisitor();
        query.accept(visitor);
        String sql = visitor.getSql();

        List<JdbcDataItem> ll = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().query(sql, visitor.getValues().toArray(new Object[0]), RowMapperUtils.getRowMapper(JdbcDataItem.class));

        return ll;
    }

    @Override
    public List<JdbcColumn> getAllJdbcColumns() {
        List<JdbcColumn> ll = new ArrayList<>();
        ll.add(this.foreignKeyJdbcColumn);

        if (this.captionJdbcColumn != null && this.foreignKeyJdbcColumn.getSqlColumn() != this.captionJdbcColumn.getSqlColumn()) {
            ll.add(this.captionJdbcColumn);
        }

        ll.addAll(propertyJdbcColumns);
        return ll;
    }

    @Override
    public JdbcDataProvider getDataProvider() {
        return this;
    }

    @Override
    public JdbcDimensionType getDimensionType() {
        return type;
    }


}
