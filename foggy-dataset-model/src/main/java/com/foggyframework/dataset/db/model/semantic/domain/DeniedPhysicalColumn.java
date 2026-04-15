package com.foggyframework.dataset.db.model.semantic.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受限物理列定义（黑名单条目）
 * <p>
 * 表示一个不允许当前用户访问的数据库物理列。
 * 由上游（如 Odoo bridge）根据 field.groups 构建并传入引擎。
 * </p>
 *
 * @since 8.2.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeniedPhysicalColumn {

    /**
     * 数据库 schema（如 "public"）。
     * 可为 null，null 时匹配任意 schema。
     */
    private String schema;

    /**
     * 物理表名（如 "fact_sales"）。必填。
     */
    private String table;

    /**
     * 物理列名（如 "profit_amount"）。必填。
     */
    private String column;
}
