package com.foggyframework.dataset.jdbc.model.impl.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.JdbcDefSupport;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcMeasureDef;
import com.foggyframework.dataset.jdbc.model.def.property.JdbcPropertyDef;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcDimensionSupport;
import com.foggyframework.dataset.jdbc.model.impl.property.JdbcPropertyImpl;
import com.foggyframework.dataset.jdbc.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.jdbc.model.spi.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Slf4j
public abstract class JdbcModelSupport extends JdbcObjectSupport implements JdbcModel {

    String idColumn;

    String tableName;

    QueryObject queryObject;

    List<JdbcDimension> dimensions = new ArrayList<>();

    List<JdbcProperty> properties = new ArrayList<>();

    List<JdbcMeasure> measures = new ArrayList<>();

    List<JdbcColumn> jdbcColumns = new ArrayList<>();

    JdbcModelType modelType;

    Map<String, JdbcColumn> name2JdbcColumn = new HashMap<>();

    //呃，用于存放startTeam.startTeamId -> startTeamId的
    //但又会引起其他问题，比如维度caption重复的情况下
    @Deprecated
    Map<String, JdbcColumn> field2JdbcColumn = new HashMap<>();

    List<JdbcDefSupport> deprecatedList = new ArrayList<>();

    MongoTemplate mongoTemplate;

    @Override
    public String getAlias() {
        return queryObject.getAlias();
    }

    @Override
    public JdbcDimension findJdbcDimensionByName(String name) {
        for (JdbcDimension dimension : dimensions) {
            if (StringUtils.equals(dimension.getName(), name) || StringUtils.equals(dimension.getAlias(), name)) {
                return dimension;
            }
        }
        return null;
    }

    @Override
    public JdbcProperty findJdbcPropertyByName(String name) {
        for (JdbcProperty property : properties) {
            if (StringUtils.equals(property.getName(), name)) {
                return property;
            }
        }
        return null;
    }

    @Override
    public JdbcMeasure findJdbcMeasureByName(String name) {
        for (JdbcMeasure measure : measures) {
            if (StringUtils.equals(measure.getName(), name)) {
                return measure;
            }
        }
        return null;
    }

    @Override
    public JdbcDimension addDimension(JdbcDimension dimension) {
        dimension.getDecorate(JdbcDimensionSupport.class).setJdbcModel(this);
        dimensions.add(dimension);
        return dimension;
    }

    @Override
    public JdbcProperty addJdbcProperty(JdbcProperty property) {
        /**
         * 检查数据
         */
        if (findJdbcPropertyByName(property.getName()) != null) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateProperty(property.getName()));
        }

        property.getDecorate(JdbcPropertyImpl.class).setJdbcModel(this);
        properties.add(property);
        return property;
    }

    @Override
    public JdbcMeasure addMeasure(JdbcMeasure measure) {
        measures.add(measure);
        return measure;
    }

    @Override
    public List<JdbcColumn> getVisibleSelectColumns() {
        List<JdbcColumn> visibleSelectColumns = new ArrayList<>();

        for (JdbcDimension dimension : dimensions) {
            visibleSelectColumns.addAll(dimension.getVisibleSelectColumns());
        }

        for (JdbcMeasure measure : measures) {
            visibleSelectColumns.add(measure.getJdbcColumn());
        }

        return visibleSelectColumns;
    }

    @Override
    public JdbcColumn findJdbcColumnByName(String jdbcColumName) {

        return name2JdbcColumn.get(jdbcColumName);
    }

    public void init() {
        //这里的代码后续需要移出，由专门的build来构建 它
        this.queryObject = new ModelQueryObject(queryObject);

        /**
         * 建立 name2JdbcColumn映射关系
         */
        for (JdbcDimension dimension : dimensions) {
            List<JdbcColumn> ll = dimension.getAllJdbcColumns();
            for (JdbcColumn jdbcColumn : ll) {
                addJdbcColumn(jdbcColumn);
            }
        }
        for (JdbcMeasure measure : measures) {
            addJdbcColumn(measure.getJdbcColumn());
        }
        if (properties != null) {
            for (JdbcProperty property : properties) {
                addJdbcColumn(property.getPropertyJdbcColumn());
            }
        }


        if (log.isDebugEnabled()) {
            log.debug(String.format("模型%s包含如下列", name));
            for (JdbcColumn jdbcColumn : jdbcColumns) {
                log.debug(String.format("name[%s],caption:[%s]", jdbcColumn.getName(), jdbcColumn.getCaption()));
            }
        }

    }

    private void addJdbcColumn(JdbcColumn jdbcColumn) {
        if (name2JdbcColumn.containsKey(jdbcColumn.getName())) {
            throw RX.throwAUserTip(DatasetMessages.modelDuplicateColumn(jdbcColumn.getName()));
        }
        jdbcColumns.add(jdbcColumn);
        name2JdbcColumn.put(jdbcColumn.getName(), jdbcColumn);
//        field2JdbcColumn.put(jdbcColumn.getField(), jdbcColumn);
    }

    public class ModelQueryObject extends QueryObjectDelegate {

        public ModelQueryObject(QueryObject delegate) {
            super(delegate);
        }

        @Override
        public String getForeignKey(QueryObject joinObject) {

            for (JdbcDimension dimension : dimensions) {
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
    public void addDeprecated(JdbcDefSupport def) {
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
        for (JdbcDefSupport def : deprecatedList) {
            if (def instanceof JdbcMeasureDef) {
                if (StringUtils.equalsIgnoreCase(((JdbcMeasureDef) def).getColumn(), jdbcColumName)) {
                    return true;
                }
                if (StringUtils.equalsIgnoreCase(((JdbcMeasureDef) def).getColumn(), StringUtils.to_sm_string(jdbcColumName))) {
                    return true;
                }
            }
            if(def instanceof JdbcPropertyDef){
                if (StringUtils.equalsIgnoreCase(((JdbcPropertyDef) def).getColumn(), jdbcColumName)) {
                    return true;
                }
                if (StringUtils.equalsIgnoreCase(((JdbcPropertyDef) def).getColumn(), StringUtils.to_sm_string(jdbcColumName))) {
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
