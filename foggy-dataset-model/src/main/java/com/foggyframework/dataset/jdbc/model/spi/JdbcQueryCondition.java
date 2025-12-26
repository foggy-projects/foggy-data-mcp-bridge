package com.foggyframework.dataset.jdbc.model.spi;

public interface JdbcQueryCondition extends DbObject {

    String getField();

    JdbcQueryCondType getType();

    String getFormat();

    String getValueFormat();

    String getQueryType();

    JdbcDimension getDimension();

    JdbcProperty getProperty();

    JdbcColumn getJdbcColumn();

    default JdbcDataProvider getDataProvider() {
        if (getDimension() != null) {
            return getDimension().getDataProvider();
        }
        if (getProperty() != null) {
            return getProperty().getDataProvider();
        }
        return null;
    }

    default String getEditField(){
        if (getDimension() != null) {
            return getDimension().getForeignKeyJdbcColumn().getField();
        }
        return getField();
    }
}
