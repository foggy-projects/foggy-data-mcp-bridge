package com.foggyframework.dataset.db.model.impl.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Slf4j
public abstract class TableModelSupport extends DbObjectSupport implements TableModel {

    String idColumn;

    String tableName;

    QueryObject queryObject;

    List<DbDimension> dimensions = new ArrayList<>();

    List<DbProperty> properties = new ArrayList<>();

    List<DbMeasure> measures = new ArrayList<>();

    List<DbColumn> columns = new ArrayList<>();

    DbModelType modelType;

    Map<String, DbColumn> name2JdbcColumn = new HashMap<>();

    //呃，用于存放startTeam.startTeamId -> startTeamId的
    //但又会引起其他问题，比如维度caption重复的情况下
    @Deprecated
    Map<String, DbColumn> field2JdbcColumn = new HashMap<>();

    List<DbDefSupport> deprecatedList = new ArrayList<>();

    /**
     * JOIN 依赖图
     * <p>在模型初始化时构建，包含所有维度和主表之间的关联关系</p>
     */
    JoinGraph joinGraph;

//    MongoTemplate mongoTemplate;

    @Override
    public String getAlias() {
        return queryObject.getAlias();
    }

    @Override
    public DbDimension findJdbcDimensionByName(String name) {
        for (DbDimension dimension : dimensions) {
            if (StringUtils.equals(dimension.getName(), name) || StringUtils.equals(dimension.getAlias(), name)) {
                return dimension;
            }
        }
        return null;
    }

    @Override
    public DbProperty findJdbcPropertyByName(String name) {
        for (DbProperty property : properties) {
            if (StringUtils.equals(property.getName(), name)) {
                return property;
            }
        }
        return null;
    }

    @Override
    public DbMeasure findJdbcMeasureByName(String name) {
        for (DbMeasure measure : measures) {
            if (StringUtils.equals(measure.getName(), name)) {
                return measure;
            }
        }
        return null;
    }

    @Override
    public DbDimension addDimension(DbDimension dimension) {
        dimension.getDecorate(DbDimensionSupport.class).setJdbcModel(this);
        dimensions.add(dimension);
        return dimension;
    }

    @Override
    public DbProperty addJdbcProperty(DbProperty property) {
        /**
         * 检查数据
         */
        if (findJdbcPropertyByName(property.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateProperty(property.getName()));
        }

        property.getDecorate(DbPropertyImpl.class).setTableModel(this);
        properties.add(property);
        return property;
    }

    @Override
    public DbMeasure addMeasure(DbMeasure measure) {
        measures.add(measure);
        return measure;
    }

    @Override
    public List<DbColumn> getVisibleSelectColumns() {
        List<DbColumn> visibleSelectColumns = new ArrayList<>();

        for (DbDimension dimension : dimensions) {
            visibleSelectColumns.addAll(dimension.getVisibleSelectColumns());
        }

        for (DbMeasure measure : measures) {
            visibleSelectColumns.add(measure.getJdbcColumn());
        }

        return visibleSelectColumns;
    }

    @Override
    public DbColumn findJdbcColumnByName(String jdbcColumName) {

        return name2JdbcColumn.get(jdbcColumName);
    }

    public void init() {
        //这里的代码后续需要移出，由专门的build来构建 它
        this.queryObject = new ModelQueryObject(queryObject);

        /**
         * 建立 name2JdbcColumn映射关系
         */
        for (DbDimension dimension : dimensions) {
            List<DbColumn> ll = dimension.getAllDbColumns();
            for (DbColumn jdbcColumn : ll) {
                addJdbcColumn(jdbcColumn);
            }
        }
        for (DbMeasure measure : measures) {
            addJdbcColumn(measure.getJdbcColumn());
        }
        if (properties != null) {
            for (DbProperty property : properties) {
                addJdbcColumn(property.getPropertyDbColumn());
            }
        }

        // 构建 JOIN 依赖图
        buildJoinGraph();

        if (log.isDebugEnabled()) {
            log.debug(String.format("模型%s包含如下列", name));
            for (DbColumn jdbcColumn : columns) {
                log.debug(String.format("name[%s],caption:[%s]", jdbcColumn.getName(), jdbcColumn.getCaption()));
            }
        }

    }

    /**
     * 构建 JOIN 依赖图
     * <p>
     * 遍历所有维度，建立主表与维表之间的关联关系。
     * 支持嵌套维度（雪花结构）。
     * </p>
     */
    private void buildJoinGraph() {
        this.joinGraph = new JoinGraph(queryObject);

        for (DbDimension dimension : dimensions) {
            addDimensionToGraph(dimension);
        }

        // 验证图的有效性
        joinGraph.validate();

        if (log.isDebugEnabled()) {
            log.debug("模型 {} 的 JoinGraph: {}", name, joinGraph);
        }
    }

    /**
     * 将维度添加到 JOIN 图
     * <p>递归处理嵌套维度</p>
     */
    private void addDimensionToGraph(DbDimension dimension) {
        QueryObject dimQueryObject = dimension.getQueryObject();
        if (dimQueryObject == null) {
            // 没有维表的维度（如时间维度的某些属性）跳过
            return;
        }

        // 确定 LEFT 表
        QueryObject leftTable;
        if (dimension.isNestedDimension()) {
            // 嵌套维度：LEFT 表是父维度的表
            DbDimension parentDim = dimension.getParentDimension();
            if (parentDim != null && parentDim.getQueryObject() != null) {
                leftTable = parentDim.getQueryObject();
            } else {
                // 如果没有父维度的 QueryObject，使用主表
                leftTable = queryObject;
            }
        } else {
            // 普通维度：LEFT 表是主表
            leftTable = queryObject;
        }

        String foreignKey = dimension.getForeignKey();

        // 获取 onBuilder（需要通过 decorate 访问）
        DbDimensionSupport dimSupport = dimension.getDecorate(DbDimensionSupport.class);
        FsscriptFunction onBuilder = dimSupport != null ? dimSupport.getOnBuilder() : null;

        // 如果有 onBuilder，使用 onBuilder
        if (onBuilder != null) {
            joinGraph.addEdge(leftTable, dimQueryObject, onBuilder, null);
        } else if (StringUtils.isNotEmpty(foreignKey)) {
            // 使用 foreignKey
            joinGraph.addEdge(leftTable, dimQueryObject, foreignKey);
        }

        // 递归处理子维度
        if (dimension.getChildDimensions() != null) {
            for (DbDimension child : dimension.getChildDimensions()) {
                addDimensionToGraph(child);
            }
        }
    }

    private void addJdbcColumn(DbColumn jdbcColumn) {
        if (name2JdbcColumn.containsKey(jdbcColumn.getName())) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateColumn(jdbcColumn.getName()));
        }
        columns.add(jdbcColumn);
        name2JdbcColumn.put(jdbcColumn.getName(), jdbcColumn);
//        field2JdbcColumn.put(jdbcColumn.getField(), jdbcColumn);
    }

    public class ModelQueryObject extends QueryObjectDelegate {

        public ModelQueryObject(QueryObject delegate) {
            super(delegate);
        }

        @Override
        public String getForeignKey(QueryObject joinObject) {

            for (DbDimension dimension : dimensions) {
                if (dimension.isQueryObject(joinObject)) {
                    // 嵌套维度的外键不在事实表上，应该返回 null
                    // 让 JdbcQuery.join 从已加入的表中查找外键
                    if (dimension.isNestedDimension()) {
                        return null;
                    }
                    return dimension.getForeignKey();
                }
            }

            return super.getForeignKey(joinObject);
        }

        @Override
        public String getPrimaryKey() {
            return idColumn;
        }


    }


    @Override
    public void addDeprecated(DbDefSupport def) {
        if (deprecatedList == null) {
            deprecatedList = new ArrayList<>();
        }
        deprecatedList.add(def);
    }

    @Override
    public boolean isDeprecated(String jdbcColumName) {
        if (deprecatedList == null) {
            return false;
        }
        for (DbDefSupport def : deprecatedList) {
            if (def instanceof DbMeasureDef) {
                if (StringUtils.equalsIgnoreCase(((DbMeasureDef) def).getColumn(), jdbcColumName)) {
                    return true;
                }
                if (StringUtils.equalsIgnoreCase(((DbMeasureDef) def).getColumn(), StringUtils.to_sm_string(jdbcColumName))) {
                    return true;
                }
            }
            if(def instanceof DbPropertyDef){
                if (StringUtils.equalsIgnoreCase(((DbPropertyDef) def).getColumn(), jdbcColumName)) {
                    return true;
                }
                if (StringUtils.equalsIgnoreCase(((DbPropertyDef) def).getColumn(), StringUtils.to_sm_string(jdbcColumName))) {
                    return true;
                }
            }

        }
        return false;
    }

    //    @Override
//    public JdbcColumn findJdbcColumnByName(String name) {
//        /**
//         * 从维表里找
//         */
////        JdbcDimension dimension = getJdbcDimensionByName(name);
////        if(dimension!=null){
////            return dimension.get
////        }
//
//        /**
//         * 从度量里找
//         */
//        return null;
//    }
}
