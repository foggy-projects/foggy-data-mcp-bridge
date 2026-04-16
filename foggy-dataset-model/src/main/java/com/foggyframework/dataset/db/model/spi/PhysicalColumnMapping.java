package com.foggyframework.dataset.db.model.spi;

import java.util.List;
import java.util.Set;

/**
 * QM 字段名 ↔ 物理 table.column 的双向映射
 * <p>
 * 在 QM 加载时自动构建，用于：
 * <ul>
 *   <li>deniedColumns（物理列黑名单）→ denied QM 字段名集合转换</li>
 *   <li>metadata 按物理列裁剪</li>
 *   <li>审计追踪（QM 字段→物理列来源）</li>
 * </ul>
 *
 * @since 8.2.0
 */
public interface PhysicalColumnMapping {

    /**
     * QM 字段名 → 物理列列表
     *
     * @param qmFieldName QM 字段名（如 "salesAmount"、"product$id"、"product$caption"）
     * @return 物理列引用列表，不存在时返回空列表
     */
    List<PhysicalColumnRef> getPhysicalColumns(String qmFieldName);

    /**
     * 物理 table+column → QM 字段名列表
     *
     * @param table  物理表名
     * @param column 物理列名
     * @return QM 字段名列表，不存在时返回空列表
     */
    List<String> getQmFieldNames(String table, String column);

    /**
     * 将 deniedColumns 物理列黑名单转换为 denied QM 字段名集合
     *
     * @param deniedPhysicalColumns 受限物理列列表
     * @return 受限 QM 字段名集合
     */
    Set<String> toDeniedQmFields(
            List<com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn> deniedPhysicalColumns);

    /**
     * 获取所有已映射的 QM 字段名
     */
    Set<String> getAllQmFieldNames();

    /**
     * 获取所有已映射的物理表名
     */
    Set<String> getAllPhysicalTables();
}
