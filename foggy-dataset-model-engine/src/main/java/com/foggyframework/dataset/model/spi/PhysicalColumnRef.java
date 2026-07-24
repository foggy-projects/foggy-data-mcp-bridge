package com.foggyframework.dataset.model.spi;

/**
 * 物理列引用（table + column）
 *
 * @param table  物理表名（如 "fact_sales"、"dim_product"）
 * @param column 物理列名（如 "sales_amount"、"product_id"）
 * @since 8.2.0
 */
public record PhysicalColumnRef(String table, String column) {

    /**
     * 返回 "table.column" 格式的字符串
     */
    public String toKey() {
        return table + "." + column;
    }
}
