package com.foggyframework.dataset.db.model.impl.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.db.model.spi.*;
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


        if (log.isDebugEnabled()) {
            log.debug(String.format("模型%s包含如下列", name));
            for (DbColumn jdbcColumn : columns) {
                log.debug(String.format("name[%s],caption:[%s]", jdbcColumn.getName(), jdbcColumn.getCaption()));
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
