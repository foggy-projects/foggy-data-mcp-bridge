package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.common.result.DbDataItem;

import javax.sql.DataSource;
import java.util.List;

public interface DbDimension extends DbObject {

    List<DbColumn> getVisibleSelectColumns();

    QueryObject getQueryObject();

    boolean isQueryObject(QueryObject joinObject);

    String getForeignKey();

    DbColumn getPrimaryKeyDbColumn();

    DbColumn getCaptionDbColumn();

    DbColumn getForeignKeyDbColumn();

    List<DbDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbDimension dbDimension, String hierarchy);

    List<DbColumn> getAllDbColumns();

    DbDimensionType getType();

    DbProperty addProperty(DbProperty property);

    DbProperty findPropertyByName(String name);

    <T>T getExtDataValue(String key);

    DbDataProvider getDataProvider();

    // ========== 嵌套维度支持 ==========

    /**
     * 获取维度的别名
     * @return 别名，如果未设置则返回null
     */
    String getAlias();

    /**
     * 获取维表主键字段的描述（$id字段的description）
     * @return 主键字段描述，用于说明格式、取值范围等
     */
    String getKeyDescription();

    /**
     * 获取有效名称（优先返回alias，否则返回name）
     * @return 有效名称
     */
    default String getEffectiveName() {
        String alias = getAlias();
        return alias != null && !alias.isEmpty() ? alias : getName();
    }

    /**
     * 获取父维度（如果是嵌套维度）
     * @return 父维度，如果是顶层维度则返回null
     */
    DbDimension getParentDimension();

    /**
     * 获取子维度列表
     * @return 子维度列表，可能为空列表
     */
    List<DbDimension> getChildDimensions();

    /**
     * 添加子维度
     * @param child 子维度
     */
    void addChildDimension(DbDimension child);

    /**
     * 获取完整路径名（如 product.category.group）
     * @return 完整路径名
     */
    default String getFullPath() {
        DbDimension parent = getParentDimension();
        if (parent == null) {
            return getName();
        }
        return parent.getFullPath() + "." + getName();
    }

    /**
     * 判断是否是嵌套维度（有父维度）
     * @return 是否是嵌套维度
     */
    default boolean isNestedDimension() {
        return getParentDimension() != null;
    }

    /**
     * 判断是否有子维度
     * @return 是否有子维度
     */
    default boolean hasChildDimensions() {
        List<DbDimension> children = getChildDimensions();
        return children != null && !children.isEmpty();
    }
}
