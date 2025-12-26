package com.foggyframework.dataset.db.model.spi;

public interface DbQueryCondition extends DbObject {

    String getField();

    DbQueryCondType getType();

    String getFormat();

    String getValueFormat();

    String getQueryType();

    DbDimension getDimension();

    DbProperty getProperty();

    DbColumn getColumn();

    default DbDataProvider getDataProvider() {
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
            return getDimension().getForeignKeyDbColumn().getField();
        }
        return getField();
    }
}
