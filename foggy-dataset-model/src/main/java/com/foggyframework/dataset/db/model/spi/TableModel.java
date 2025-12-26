package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.DbDefSupport;

import java.util.List;

public interface TableModel extends DbObject {

    String getIdColumn();

    DbDimension findJdbcDimensionByName(String name);

    DbMeasure findJdbcMeasureByName(String name);

    DbDimension addDimension(DbDimension dimension);

    DbMeasure addMeasure(DbMeasure measure);

    List<DbColumn> getVisibleSelectColumns();

    QueryObject getQueryObject();

    List<DbDimension> getDimensions();

    List<DbMeasure> getMeasures();


    DbColumn findJdbcColumnByName(String jdbcColumName);

    List<DbProperty> getProperties();

    DbProperty findJdbcPropertyByName(String name);

    DbProperty addJdbcProperty(DbProperty dbProperty);

    String getAlias();

    DbModelType getModelType();

    String getTableName();

    void addDeprecated(DbDefSupport def);

    boolean isDeprecated(String jdbcColumName);

//    MongoTemplate getMongoTemplate();

//    DataSource getDataSource();

//    default boolean isImportant() {
//        return false;
//    }

}
