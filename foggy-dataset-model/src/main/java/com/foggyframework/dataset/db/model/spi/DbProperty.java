package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.fsscript.exp.FsscriptFunction;

import java.math.BigDecimal;

public interface DbProperty extends DbObject {

    DbColumn getPropertyDbColumn();

    TableModel getTableModel();

    DbColumnType getType();

    String getFormat();

    <T> T getExtDataValue(String key);

    DbDataProvider getDataProvider();

    boolean isBit();

    void setFormulaBuilder(FsscriptFunction builder);

    default boolean isDict() {
        return false;
    }

    default String getDictRef() {
        return null;
    }

    default DbDictionaryDiscoveryDef getDictionaryDiscovery() {
        return null;
    }

    /**
     * 获取时间角色语义 (e.g. business_date, event_time, system_time)
     */
    default String getTimeRole() {
        return null;
    }

    /**
     * 获取推荐用途说明
     */
    default String getRecommendedUse() {
        return null;
    }

    default BigDecimal getSemanticScaleFactor() {
        return null;
    }

    default String getSemanticUnit() {
        return null;
    }

    default String getSemanticUnitLabel() {
        return null;
    }
}
