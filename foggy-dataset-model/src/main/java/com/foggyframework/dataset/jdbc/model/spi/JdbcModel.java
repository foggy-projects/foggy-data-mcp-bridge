package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.JdbcDefSupport;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;
import java.util.List;

public interface JdbcModel extends JdbcObject {

    String getIdColumn();

    JdbcDimension findJdbcDimensionByName(String name);

    JdbcMeasure findJdbcMeasureByName(String name);

    JdbcDimension addDimension(JdbcDimension dimension);

    JdbcMeasure addMeasure(JdbcMeasure measure);

    List<JdbcColumn> getVisibleSelectColumns();

    QueryObject getQueryObject();

    List<JdbcDimension> getDimensions();

    List<JdbcMeasure> getMeasures();


    JdbcColumn findJdbcColumnByName(String jdbcColumName);

    List<JdbcProperty> getProperties();

    JdbcProperty findJdbcPropertyByName(String name);

    JdbcProperty addJdbcProperty(JdbcProperty jdbcProperty);

    String getAlias();

    JdbcModelType getModelType();

    String getTableName();

    void addDeprecated(JdbcDefSupport def);

    boolean isDeprecated(String jdbcColumName);

    MongoTemplate getMongoTemplate();

    DataSource getDataSource();

//    default boolean isImportant() {
//        return false;
//    }

}
