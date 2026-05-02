-- ============================================
-- Foggy Dataset Model 测试数据生成
-- 脚本: 04-seed-2025-sales.sql
-- 说明: 生成 2025 年销售事实数据，用于 timeWindow yoy 在 MySQL 上的实库验证。
-- 运行命令: docker exec -i foggy-demo-mysql8 mysql -ufoggy -pfoggy_test_123 foggy_test < D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge\foggy-dataset-demo\docker\mysql\init\04-seed-2025-sales.sql
-- 数据范围: 复制 2024 年的数据到 2025 年。将 20240229 映射至 20250228。
--          数量增加1，销售额上浮约 10%，成本上浮约 5%，确保利润正向。
-- ============================================

SET NAMES utf8mb4;

-- 1. 清理旧的本脚本生成数据，保证幂等
DELETE FROM fact_sales WHERE order_id LIKE 'TW2025%';

-- 2. 插入 2025 数据，基于 2024 数据复制
INSERT INTO fact_sales (
    order_id, 
    order_line_no, 
    date_key, 
    product_key, 
    customer_key,
    store_key, 
    channel_key, 
    promotion_key, 
    quantity, 
    unit_price,
    unit_cost, 
    discount_amount, 
    sales_amount, 
    cost_amount, 
    profit_amount,
    tax_amount,
    order_status, 
    payment_method
)
SELECT 
    CONCAT('TW2025_', RIGHT(order_id, 8)),
    order_line_no,
    CASE 
        WHEN date_key = 20240229 THEN 20250228 
        ELSE date_key + 10000 
    END,
    product_key,
    customer_key,
    store_key,
    channel_key,
    promotion_key,
    quantity + 1,
    unit_price,
    unit_cost,
    discount_amount,
    (sales_amount / quantity) * (quantity + 1) * 1.1,
    (cost_amount / quantity) * (quantity + 1) * 1.05,
    ((sales_amount / quantity) * (quantity + 1) * 1.1) - ((cost_amount / quantity) * (quantity + 1) * 1.05),
    tax_amount,
    order_status,
    payment_method
FROM fact_sales
WHERE date_key BETWEEN 20240101 AND 20241231
  AND order_id NOT LIKE 'TW2025%';

SELECT CONCAT('Inserted ', ROW_COUNT(), ' rows for 2025 fact_sales') AS result;
