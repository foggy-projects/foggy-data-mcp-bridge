-- ============================================
-- Foggy Dataset Model 预聚合测试数据
-- 文件: 11-preagg-testdata.sql
-- 说明: 生成测试数据用于验证预聚合功能
-- ============================================

SET NAMES utf8mb4;

-- ==========================================
-- 1. 生成日期维度数据（2024年1月-3月）
-- ==========================================
INSERT IGNORE INTO `dim_date` (`date_key`, `full_date`, `year`, `quarter`, `month`, `month_name`,
    `week_of_year`, `day_of_month`, `day_of_week`, `day_name`, `is_weekend`, `is_holiday`)
SELECT
    DATE_FORMAT(d, '%Y%m%d') AS date_key,
    DATE_FORMAT(d, '%Y-%m-%d') AS full_date,
    YEAR(d) AS `year`,
    QUARTER(d) AS `quarter`,
    MONTH(d) AS `month`,
    CASE MONTH(d)
        WHEN 1 THEN '一月' WHEN 2 THEN '二月' WHEN 3 THEN '三月'
        WHEN 4 THEN '四月' WHEN 5 THEN '五月' WHEN 6 THEN '六月'
        WHEN 7 THEN '七月' WHEN 8 THEN '八月' WHEN 9 THEN '九月'
        WHEN 10 THEN '十月' WHEN 11 THEN '十一月' WHEN 12 THEN '十二月'
    END AS month_name,
    WEEK(d) AS week_of_year,
    DAY(d) AS day_of_month,
    WEEKDAY(d) + 1 AS day_of_week,
    CASE WEEKDAY(d) + 1
        WHEN 1 THEN '周一' WHEN 2 THEN '周二' WHEN 3 THEN '周三'
        WHEN 4 THEN '周四' WHEN 5 THEN '周五' WHEN 6 THEN '周六'
        WHEN 7 THEN '周日'
    END AS day_name,
    IF(WEEKDAY(d) + 1 IN (6, 7), 1, 0) AS is_weekend,
    0 AS is_holiday
FROM (
    SELECT DATE('2024-01-01') + INTERVAL seq DAY AS d
    FROM (
        SELECT @rownum := @rownum + 1 AS seq
        FROM information_schema.COLUMNS a, information_schema.COLUMNS b,
             (SELECT @rownum := -1) r
        LIMIT 120
    ) t
) dates;

-- ==========================================
-- 2. 生成商品维度数据
-- ==========================================
INSERT IGNORE INTO `dim_product` (`product_key`, `product_id`, `product_name`, `category_id`, `category_name`,
    `sub_category_id`, `sub_category_name`, `brand`, `unit_price`, `unit_cost`)
VALUES
(1, 'P001', 'iPhone 15 Pro', 'C01', '电子产品', 'C0101', '手机', 'Apple', 9999.00, 7000.00),
(2, 'P002', 'MacBook Pro 14', 'C01', '电子产品', 'C0102', '电脑', 'Apple', 14999.00, 10000.00),
(3, 'P003', 'AirPods Pro', 'C01', '电子产品', 'C0103', '耳机', 'Apple', 1999.00, 1200.00),
(4, 'P004', '小米14', 'C01', '电子产品', 'C0101', '手机', '小米', 4999.00, 3500.00),
(5, 'P005', 'ThinkPad X1', 'C01', '电子产品', 'C0102', '电脑', 'Lenovo', 12999.00, 9000.00),
(6, 'P006', '纯棉T恤', 'C02', '服装', 'C0201', '上衣', '优衣库', 99.00, 40.00),
(7, 'P007', '休闲牛仔裤', 'C02', '服装', 'C0202', '裤子', 'Levis', 399.00, 150.00),
(8, 'P008', '运动鞋', 'C02', '服装', 'C0203', '鞋类', 'Nike', 899.00, 400.00),
(9, 'P009', '保温杯', 'C03', '家居', 'C0301', '厨具', '膳魔师', 299.00, 120.00),
(10, 'P010', '床上四件套', 'C03', '家居', 'C0302', '床品', '罗莱', 599.00, 250.00);

-- ==========================================
-- 3. 生成客户维度数据
-- ==========================================
INSERT IGNORE INTO `dim_customer` (`customer_key`, `customer_id`, `customer_name`, `customer_type`,
    `gender`, `age_group`, `province`, `city`, `member_level`)
VALUES
(1, 'CU001', '张三', 'VIP', '男', '26-35', '广东省', '深圳市', '金卡'),
(2, 'CU002', '李四', 'NORMAL', '女', '18-25', '上海市', '上海市', '普通'),
(3, 'CU003', '王五', 'VIP', '男', '36-45', '北京市', '北京市', '钻石'),
(4, 'CU004', '赵六', 'NORMAL', '女', '26-35', '浙江省', '杭州市', '银卡'),
(5, 'CU005', '孙七', 'NEW', '男', '18-25', '江苏省', '南京市', '普通'),
(6, 'CU006', '周八', 'VIP', '女', '36-45', '广东省', '广州市', '金卡'),
(7, 'CU007', '吴九', 'NORMAL', '男', '46+', '四川省', '成都市', '银卡'),
(8, 'CU008', '郑十', 'NEW', '女', '18-25', '湖北省', '武汉市', '普通');

-- ==========================================
-- 4. 生成门店维度数据
-- ==========================================
INSERT IGNORE INTO `dim_store` (`store_key`, `store_id`, `store_name`, `store_type`, `province`, `city`)
VALUES
(1, 'S001', '深圳福田旗舰店', '旗舰店', '广东省', '深圳市'),
(2, 'S002', '上海南京路店', '直营店', '上海市', '上海市'),
(3, 'S003', '北京王府井店', '旗舰店', '北京市', '北京市'),
(4, 'S004', '杭州西湖店', '加盟店', '浙江省', '杭州市');

-- ==========================================
-- 5. 生成渠道维度数据
-- ==========================================
INSERT IGNORE INTO `dim_channel` (`channel_key`, `channel_id`, `channel_name`, `channel_type`, `platform`)
VALUES
(1, 'CH001', '天猫旗舰店', 'ONLINE', '天猫'),
(2, 'CH002', '京东自营店', 'ONLINE', '京东'),
(3, 'CH003', '线下门店', 'OFFLINE', '线下门店'),
(4, 'CH004', '微信小程序', 'ONLINE', '微信');

-- ==========================================
-- 6. 生成促销维度数据
-- ==========================================
INSERT IGNORE INTO `dim_promotion` (`promotion_key`, `promotion_id`, `promotion_name`, `promotion_type`, `discount_rate`)
VALUES
(1, 'PM001', '新年特惠', '折扣', 0.85),
(2, 'PM002', '满1000减100', '满减', NULL),
(3, 'PM003', '买一送一', '赠品', NULL),
(4, 'PM004', '无促销', '无', 1.00);

-- ==========================================
-- 7. 生成销售事实数据（2024年1月-3月，每天约20-50条）
-- ==========================================
-- 清空现有数据
TRUNCATE TABLE `fact_sales`;

-- 使用存储过程生成测试数据
DROP PROCEDURE IF EXISTS `sp_generate_sales_data`;
DELIMITER //
CREATE PROCEDURE `sp_generate_sales_data`()
BEGIN
    DECLARE v_date INT;
    DECLARE v_date_end INT;
    DECLARE v_order_seq INT DEFAULT 1;
    DECLARE v_product_key INT;
    DECLARE v_customer_key INT;
    DECLARE v_store_key INT;
    DECLARE v_channel_key INT;
    DECLARE v_promotion_key INT;
    DECLARE v_quantity INT;
    DECLARE v_unit_price DECIMAL(18,2);
    DECLARE v_unit_cost DECIMAL(18,2);
    DECLARE v_sales_amount DECIMAL(18,2);
    DECLARE v_cost_amount DECIMAL(18,2);
    DECLARE v_profit_amount DECIMAL(18,2);
    DECLARE v_daily_count INT;
    DECLARE i INT;

    SET v_date = 20240101;
    SET v_date_end = 20240331;

    WHILE v_date <= v_date_end DO
        -- 每天生成20-50条销售记录
        SET v_daily_count = FLOOR(20 + RAND() * 30);
        SET i = 1;

        WHILE i <= v_daily_count DO
            -- 随机选择维度
            SET v_product_key = FLOOR(1 + RAND() * 10);
            SET v_customer_key = FLOOR(1 + RAND() * 8);
            SET v_store_key = FLOOR(1 + RAND() * 4);
            SET v_channel_key = FLOOR(1 + RAND() * 4);
            SET v_promotion_key = FLOOR(1 + RAND() * 4);
            SET v_quantity = FLOOR(1 + RAND() * 5);

            -- 获取商品价格
            SELECT unit_price, unit_cost INTO v_unit_price, v_unit_cost
            FROM dim_product WHERE product_key = v_product_key;

            -- 计算金额
            SET v_sales_amount = v_unit_price * v_quantity;
            SET v_cost_amount = IFNULL(v_unit_cost, v_unit_price * 0.6) * v_quantity;
            SET v_profit_amount = v_sales_amount - v_cost_amount;

            -- 插入销售记录
            INSERT INTO `fact_sales` (
                `order_id`, `order_line_no`, `date_key`, `product_key`, `customer_key`,
                `store_key`, `channel_key`, `promotion_key`, `quantity`, `unit_price`,
                `unit_cost`, `discount_amount`, `sales_amount`, `cost_amount`,
                `profit_amount`, `tax_amount`, `order_status`, `payment_method`, `created_at`
            ) VALUES (
                CONCAT('ORD', LPAD(v_order_seq, 8, '0')),
                1,
                v_date,
                v_product_key,
                v_customer_key,
                v_store_key,
                v_channel_key,
                v_promotion_key,
                v_quantity,
                v_unit_price,
                v_unit_cost,
                v_sales_amount * 0.05,
                v_sales_amount,
                v_cost_amount,
                v_profit_amount,
                v_sales_amount * 0.06,
                ELT(FLOOR(1 + RAND() * 4), 'COMPLETED', 'PENDING', 'SHIPPED', 'PAID'),
                ELT(FLOOR(1 + RAND() * 4), 'ALIPAY', 'WECHAT', 'CARD', 'CASH'),
                STR_TO_DATE(CONCAT(v_date, ' ', LPAD(FLOOR(8 + RAND() * 12), 2, '0'), ':',
                    LPAD(FLOOR(RAND() * 60), 2, '0'), ':00'), '%Y%m%d %H:%i:%s')
            );

            SET v_order_seq = v_order_seq + 1;
            SET i = i + 1;
        END WHILE;

        -- 下一天
        SET v_date = DATE_FORMAT(DATE_ADD(STR_TO_DATE(v_date, '%Y%m%d'), INTERVAL 1 DAY), '%Y%m%d');
    END WHILE;
END //
DELIMITER ;

-- 执行存储过程
CALL sp_generate_sales_data();
DROP PROCEDURE IF EXISTS `sp_generate_sales_data`;

-- ==========================================
-- 8. 生成退货事实数据（约10%的销售会退货）
-- ==========================================
TRUNCATE TABLE `fact_return`;

-- MySQL 5.7 兼容：用用户变量替代 ROW_NUMBER() 窗口函数
SET @ret_row := 0;
INSERT INTO `fact_return` (
    `return_id`, `order_id`, `order_line_no`, `date_key`, `product_key`, `customer_key`,
    `store_key`, `return_quantity`, `return_amount`, `return_reason`, `return_type`,
    `return_status`, `return_time`, `created_at`
)
SELECT
    CONCAT('RET', LPAD(@ret_row := @ret_row + 1, 8, '0')) AS return_id,
    s.order_id,
    s.order_line_no,
    s.date_key + FLOOR(1 + RAND() * 7) AS date_key,  -- 退货日期在购买后1-7天
    s.product_key,
    s.customer_key,
    s.store_key,
    FLOOR(1 + RAND() * s.quantity) AS return_quantity,
    s.sales_amount * (0.5 + RAND() * 0.5) AS return_amount,
    ELT(FLOOR(1 + RAND() * 4), '质量问题', '尺寸不合适', '不喜欢', '其他') AS return_reason,
    ELT(FLOOR(1 + RAND() * 2), 'REFUND_ONLY', 'RETURN_REFUND') AS return_type,
    ELT(FLOOR(1 + RAND() * 3), 'COMPLETED', 'PENDING', 'APPROVED') AS return_status,
    DATE_ADD(s.created_at, INTERVAL FLOOR(1 + RAND() * 7) DAY) AS return_time,
    NOW() AS created_at
FROM fact_sales s
WHERE RAND() < 0.1  -- 约10%的销售会退货
ORDER BY s.sales_key;

-- ==========================================
-- 9. 填充预聚合表数据（模拟已完成的预聚合）
-- ==========================================

-- 9.1 填充日+商品预聚合表（只填充到2024-03-20，留出10天用于测试混合查询）
TRUNCATE TABLE `preagg_daily_product_sales`;
INSERT INTO `preagg_daily_product_sales` (
    `date_key`, `product_key`, `product_category_name`, `product_brand`,
    `quantity_sum`, `sales_amount_sum`, `cost_amount_sum`, `profit_amount_sum`,
    `order_count`, `_preagg_row_count`
)
SELECT
    fs.date_key,
    fs.product_key,
    p.category_name,
    p.brand,
    SUM(fs.quantity) AS quantity_sum,
    SUM(fs.sales_amount) AS sales_amount_sum,
    SUM(fs.cost_amount) AS cost_amount_sum,
    SUM(fs.profit_amount) AS profit_amount_sum,
    COUNT(*) AS order_count,
    COUNT(*) AS _preagg_row_count
FROM fact_sales fs
JOIN dim_product p ON fs.product_key = p.product_key
WHERE fs.date_key <= 20240320  -- 只到3月20日，留出空间测试混合查询
GROUP BY fs.date_key, fs.product_key, p.category_name, p.brand;

-- 9.2 填充月+品类预聚合表
TRUNCATE TABLE `preagg_monthly_category_sales`;
INSERT INTO `preagg_monthly_category_sales` (
    `year_month`, `category_name`,
    `quantity_sum`, `sales_amount_sum`, `cost_amount_sum`, `profit_amount_sum`,
    `order_count`, `_preagg_row_count`
)
SELECT
    FLOOR(fs.date_key / 100) AS `year_month`,
    p.category_name,
    SUM(fs.quantity) AS quantity_sum,
    SUM(fs.sales_amount) AS sales_amount_sum,
    SUM(fs.cost_amount) AS cost_amount_sum,
    SUM(fs.profit_amount) AS profit_amount_sum,
    COUNT(*) AS order_count,
    COUNT(*) AS _preagg_row_count
FROM fact_sales fs
JOIN dim_product p ON fs.product_key = p.product_key
WHERE fs.date_key <= 20240320
GROUP BY FLOOR(fs.date_key / 100), p.category_name;

-- 9.3 填充日+客户+渠道预聚合表
TRUNCATE TABLE `preagg_daily_customer_channel_sales`;
INSERT INTO `preagg_daily_customer_channel_sales` (
    `date_key`, `customer_key`, `channel_key`,
    `customer_province`, `customer_city`, `channel_type`,
    `quantity_sum`, `sales_amount_sum`, `order_count`, `_preagg_row_count`
)
SELECT
    fs.date_key,
    fs.customer_key,
    fs.channel_key,
    c.province,
    c.city,
    ch.channel_type,
    SUM(fs.quantity) AS quantity_sum,
    SUM(fs.sales_amount) AS sales_amount_sum,
    COUNT(*) AS order_count,
    COUNT(*) AS _preagg_row_count
FROM fact_sales fs
JOIN dim_customer c ON fs.customer_key = c.customer_key
JOIN dim_channel ch ON fs.channel_key = ch.channel_key
WHERE fs.date_key <= 20240320
GROUP BY fs.date_key, fs.customer_key, fs.channel_key, c.province, c.city, ch.channel_type;

-- 9.4 填充退货预聚合表
TRUNCATE TABLE `preagg_daily_return`;
INSERT INTO `preagg_daily_return` (
    `date_key`, `product_key`, `product_category_name`,
    `return_quantity_sum`, `return_amount_sum`, `return_count`, `_preagg_row_count`
)
SELECT
    fr.date_key,
    fr.product_key,
    p.category_name,
    SUM(fr.return_quantity) AS return_quantity_sum,
    SUM(fr.return_amount) AS return_amount_sum,
    COUNT(*) AS return_count,
    COUNT(*) AS _preagg_row_count
FROM fact_return fr
JOIN dim_product p ON fr.product_key = p.product_key
WHERE fr.date_key <= 20240320
GROUP BY fr.date_key, fr.product_key, p.category_name;

-- ==========================================
-- 10. 初始化水位线记录
-- ==========================================
TRUNCATE TABLE `preagg_watermark`;
INSERT INTO `preagg_watermark` (`preagg_name`, `model_name`, `last_refresh_time`, `watermark_value`,
    `watermark_type`, `refresh_strategy`, `row_count`)
VALUES
('daily_product_sales', 'FactSalesPreAggModel', NOW() - INTERVAL 1 DAY, '20240320', 'DATE', 'INCREMENTAL',
    (SELECT COUNT(*) FROM preagg_daily_product_sales)),
('monthly_category_sales', 'FactSalesPreAggModel', NOW() - INTERVAL 1 DAY, '202403', 'MONTH', 'FULL',
    (SELECT COUNT(*) FROM preagg_monthly_category_sales)),
('daily_customer_channel_sales', 'FactSalesPreAggModel', NOW() - INTERVAL 1 DAY, '20240320', 'DATE', 'INCREMENTAL',
    (SELECT COUNT(*) FROM preagg_daily_customer_channel_sales)),
('daily_return', 'FactReturnPreAggModel', NOW() - INTERVAL 1 DAY, '20240320', 'DATE', 'INCREMENTAL',
    (SELECT COUNT(*) FROM preagg_daily_return));

-- ==========================================
-- 11. 验证数据
-- ==========================================
SELECT 'Test data generated successfully!' AS message;

SELECT 'fact_sales count' AS table_name, COUNT(*) AS row_count FROM fact_sales
UNION ALL
SELECT 'fact_return count', COUNT(*) FROM fact_return
UNION ALL
SELECT 'preagg_daily_product_sales count', COUNT(*) FROM preagg_daily_product_sales
UNION ALL
SELECT 'preagg_monthly_category_sales count', COUNT(*) FROM preagg_monthly_category_sales
UNION ALL
SELECT 'preagg_daily_customer_channel_sales count', COUNT(*) FROM preagg_daily_customer_channel_sales
UNION ALL
SELECT 'preagg_daily_return count', COUNT(*) FROM preagg_daily_return;
