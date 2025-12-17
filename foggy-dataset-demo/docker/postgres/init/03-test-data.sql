-- ============================================
-- Foggy Dataset Model PostgreSQL 测试数据生成
-- 文件: 03-test-data.sql
-- 说明: 生成维度表和事实表测试数据
-- ============================================

-- ==========================================
-- 1. 生成日期维度数据 (2022-2024, 3年)
-- ==========================================
INSERT INTO dim_date (date_key, full_date, year, quarter, month, month_name, week_of_year, day_of_month, day_of_week, day_name, is_weekend, is_holiday, fiscal_year, fiscal_quarter)
SELECT
    CAST(TO_CHAR(d, 'YYYYMMDD') AS INT) AS date_key,
    d AS full_date,
    EXTRACT(YEAR FROM d)::SMALLINT AS year,
    EXTRACT(QUARTER FROM d)::SMALLINT AS quarter,
    EXTRACT(MONTH FROM d)::SMALLINT AS month,
    CASE EXTRACT(MONTH FROM d)
        WHEN 1 THEN '一月' WHEN 2 THEN '二月' WHEN 3 THEN '三月'
        WHEN 4 THEN '四月' WHEN 5 THEN '五月' WHEN 6 THEN '六月'
        WHEN 7 THEN '七月' WHEN 8 THEN '八月' WHEN 9 THEN '九月'
        WHEN 10 THEN '十月' WHEN 11 THEN '十一月' WHEN 12 THEN '十二月'
    END AS month_name,
    EXTRACT(WEEK FROM d)::SMALLINT AS week_of_year,
    EXTRACT(DAY FROM d)::SMALLINT AS day_of_month,
    EXTRACT(ISODOW FROM d)::SMALLINT AS day_of_week,
    CASE EXTRACT(ISODOW FROM d)
        WHEN 1 THEN '星期一' WHEN 2 THEN '星期二' WHEN 3 THEN '星期三'
        WHEN 4 THEN '星期四' WHEN 5 THEN '星期五' WHEN 6 THEN '星期六'
        WHEN 7 THEN '星期日'
    END AS day_name,
    CASE WHEN EXTRACT(ISODOW FROM d) IN (6, 7) THEN 1 ELSE 0 END AS is_weekend,
    0 AS is_holiday,
    EXTRACT(YEAR FROM d)::SMALLINT AS fiscal_year,
    EXTRACT(QUARTER FROM d)::SMALLINT AS fiscal_quarter
FROM generate_series('2022-01-01'::DATE, '2024-12-31'::DATE, '1 day'::INTERVAL) AS d;

-- ==========================================
-- 2. 生成渠道维度数据 (10条)
-- ==========================================
INSERT INTO dim_channel (channel_id, channel_name, channel_type, platform, status) VALUES
('CHN001', '天猫旗舰店', 'ONLINE', '天猫', 'ACTIVE'),
('CHN002', '京东自营店', 'ONLINE', '京东', 'ACTIVE'),
('CHN003', '拼多多官方店', 'ONLINE', '拼多多', 'ACTIVE'),
('CHN004', '抖音小店', 'ONLINE', '抖音', 'ACTIVE'),
('CHN005', '小红书店铺', 'ONLINE', '小红书', 'ACTIVE'),
('CHN006', '官方商城', 'ONLINE', '自有平台', 'ACTIVE'),
('CHN007', '线下直营', 'OFFLINE', '线下门店', 'ACTIVE'),
('CHN008', '线下加盟', 'OFFLINE', '线下门店', 'ACTIVE'),
('CHN009', '企业团购', 'OFFLINE', 'B2B', 'ACTIVE'),
('CHN010', '微信小程序', 'ONLINE', '微信', 'ACTIVE');

-- ==========================================
-- 3. 生成促销活动维度数据 (30条)
-- ==========================================
INSERT INTO dim_promotion (promotion_id, promotion_name, promotion_type, discount_rate, start_date, end_date, status) VALUES
('PRM001', '无促销', NULL, 1.00, '2022-01-01', '2099-12-31', 'ACTIVE'),
('PRM002', '新年特惠-全场9折', '折扣', 0.90, '2024-01-01', '2024-01-15', 'ACTIVE'),
('PRM003', '春节满300减50', '满减', NULL, '2024-02-01', '2024-02-15', 'ACTIVE'),
('PRM004', '38女神节8折', '折扣', 0.80, '2024-03-01', '2024-03-08', 'ACTIVE'),
('PRM005', '五一劳动节满500减100', '满减', NULL, '2024-05-01', '2024-05-05', 'ACTIVE'),
('PRM006', '618年中大促', '折扣', 0.75, '2024-06-01', '2024-06-18', 'ACTIVE'),
('PRM007', '暑期清凉价', '折扣', 0.85, '2024-07-01', '2024-08-31', 'ACTIVE'),
('PRM008', '中秋好礼', '赠品', NULL, '2024-09-10', '2024-09-17', 'ACTIVE'),
('PRM009', '国庆7天乐', '折扣', 0.80, '2024-10-01', '2024-10-07', 'ACTIVE'),
('PRM010', '双11狂欢节', '满减', NULL, '2024-11-01', '2024-11-11', 'ACTIVE'),
('PRM011', '双12年终盛典', '折扣', 0.70, '2024-12-01', '2024-12-12', 'ACTIVE'),
('PRM012', '会员专享日', '折扣', 0.88, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM013', '新人首单立减20', '优惠券', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM014', '满199减30', '满减', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM015', '满399减60', '满减', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM016', '2023新年特惠', '折扣', 0.90, '2023-01-01', '2023-01-15', 'ACTIVE'),
('PRM017', '2023春节', '满减', NULL, '2023-02-01', '2023-02-15', 'ACTIVE'),
('PRM018', '2023双11', '折扣', 0.75, '2023-11-01', '2023-11-11', 'ACTIVE'),
('PRM019', '2022双11', '折扣', 0.80, '2022-11-01', '2022-11-11', 'ACTIVE'),
('PRM020', '2022双12', '满减', NULL, '2022-12-01', '2022-12-12', 'ACTIVE'),
('PRM021', '品牌日-数码专场', '折扣', 0.85, '2024-03-15', '2024-03-17', 'ACTIVE'),
('PRM022', '品牌日-服装专场', '折扣', 0.80, '2024-04-15', '2024-04-17', 'ACTIVE'),
('PRM023', '品牌日-美妆专场', '折扣', 0.75, '2024-05-15', '2024-05-17', 'ACTIVE'),
('PRM024', '限时秒杀', '折扣', 0.50, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM025', '老客回馈', '优惠券', NULL, '2024-06-01', '2024-06-30', 'ACTIVE'),
('PRM026', '积分兑换', '优惠券', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM027', '买一送一', '赠品', NULL, '2024-07-15', '2024-07-20', 'ACTIVE'),
('PRM028', '满额赠礼', '赠品', NULL, '2024-08-01', '2024-08-15', 'ACTIVE'),
('PRM029', '学生专享', '折扣', 0.90, '2024-09-01', '2024-09-30', 'ACTIVE'),
('PRM030', '企业采购优惠', '折扣', 0.85, '2024-01-01', '2024-12-31', 'ACTIVE');

-- ==========================================
-- 4. 生成门店维度数据 (50条)
-- ==========================================
INSERT INTO dim_store (store_id, store_name, store_type, province, city, district, address, manager_name, open_date, area_sqm, status)
SELECT
    'STR' || LPAD(i::TEXT, 5, '0'),
    cities[1 + (i-1) % 5] || districts[1 + (i-1) % 5] || '店' || i,
    CASE WHEN i <= 30 THEN '直营' ELSE '加盟' END,
    provinces[1 + (i-1) % 5],
    cities[1 + (i-1) % 5],
    districts[1 + (i-1) % 5],
    districts[1 + (i-1) % 5] || '某路' || i || '号',
    '店长' || i,
    '2020-01-01'::DATE + (RANDOM() * 1000)::INT,
    200 + (RANDOM() * 800)::INT,
    'ACTIVE'
FROM generate_series(1, 50) AS i,
     (SELECT ARRAY['浙江省', '江苏省', '广东省', '上海市', '北京市'] AS provinces,
             ARRAY['杭州市', '南京市', '广州市', '上海市', '北京市'] AS cities,
             ARRAY['西湖区', '玄武区', '天河区', '浦东新区', '朝阳区'] AS districts) AS t;

-- ==========================================
-- 5. 生成商品维度数据 (500条)
-- ==========================================
INSERT INTO dim_product (product_id, product_name, category_id, category_name, sub_category_id, sub_category_name, brand, unit_price, unit_cost, status)
SELECT
    'PRD' || LPAD(i::TEXT, 6, '0'),
    brands[1 + (i-1) % 15] || ' ' ||
    CASE
        WHEN i <= 100 THEN CASE
            WHEN i <= 40 THEN '手机通讯'
            WHEN i <= 70 THEN '电脑办公'
            ELSE '家用电器'
        END
        WHEN i <= 200 THEN CASE
            WHEN i <= 140 THEN '男装'
            WHEN i <= 180 THEN '女装'
            ELSE '鞋靴'
        END
        WHEN i <= 300 THEN '休闲零食'
        WHEN i <= 400 THEN '家具'
        ELSE '护肤'
    END || '商品' || i,
    CASE
        WHEN i <= 100 THEN 'CAT001'
        WHEN i <= 200 THEN 'CAT002'
        WHEN i <= 300 THEN 'CAT003'
        WHEN i <= 400 THEN 'CAT004'
        ELSE 'CAT005'
    END,
    CASE
        WHEN i <= 100 THEN '数码电器'
        WHEN i <= 200 THEN '服装配饰'
        WHEN i <= 300 THEN '食品饮料'
        WHEN i <= 400 THEN '家居日用'
        ELSE '美妆个护'
    END,
    CASE
        WHEN i <= 40 THEN 'CAT001001'
        WHEN i <= 70 THEN 'CAT001002'
        WHEN i <= 100 THEN 'CAT001003'
        WHEN i <= 140 THEN 'CAT002001'
        WHEN i <= 180 THEN 'CAT002002'
        WHEN i <= 200 THEN 'CAT002003'
        WHEN i <= 300 THEN 'CAT003001'
        WHEN i <= 400 THEN 'CAT004001'
        ELSE 'CAT005001'
    END,
    CASE
        WHEN i <= 40 THEN '手机通讯'
        WHEN i <= 70 THEN '电脑办公'
        WHEN i <= 100 THEN '家用电器'
        WHEN i <= 140 THEN '男装'
        WHEN i <= 180 THEN '女装'
        WHEN i <= 200 THEN '鞋靴'
        WHEN i <= 300 THEN '休闲零食'
        WHEN i <= 400 THEN '家具'
        ELSE '护肤'
    END,
    brands[1 + (i-1) % 15],
    (50 + RANDOM() * 9950)::DECIMAL(18,2),
    ((50 + RANDOM() * 9950) * (0.4 + RANDOM() * 0.3))::DECIMAL(18,2),
    'ACTIVE'
FROM generate_series(1, 500) AS i,
     (SELECT ARRAY['Apple', '华为', '小米', '三星', 'OPPO', 'vivo', '联想', '戴尔', '惠普', 'Nike', 'Adidas', '优衣库', '海尔', '美的', '格力'] AS brands) AS t;

-- ==========================================
-- 6. 生成客户维度数据 (1000条)
-- ==========================================
INSERT INTO dim_customer (customer_id, customer_name, customer_type, gender, age_group, province, city, district, register_date, member_level, status)
SELECT
    'CUS' || LPAD(i::TEXT, 6, '0'),
    '客户' || i,
    CASE
        WHEN i <= 100 THEN 'VIP'
        WHEN i <= 400 THEN 'NORMAL'
        ELSE 'NEW'
    END,
    CASE WHEN RANDOM() < 0.48 THEN '男' WHEN RANDOM() < 0.96 THEN '女' ELSE '未知' END,
    (ARRAY['18-24', '25-34', '35-44', '45-54', '55+'])[1 + (RANDOM() * 5)::INT % 5],
    provinces[1 + (i-1) % 6],
    cities[1 + (i-1) % 6],
    districts[1 + (i-1) % 6],
    '2020-01-01'::DATE + (RANDOM() * 1500)::INT,
    (ARRAY['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'])[1 + (RANDOM() * 5)::INT % 5],
    'ACTIVE'
FROM generate_series(1, 1000) AS i,
     (SELECT ARRAY['浙江省', '江苏省', '广东省', '上海市', '北京市', '四川省'] AS provinces,
             ARRAY['杭州市', '南京市', '广州市', '上海市', '北京市', '成都市'] AS cities,
             ARRAY['西湖区', '玄武区', '天河区', '浦东新区', '朝阳区', '武侯区'] AS districts) AS t;

-- ==========================================
-- 7. 生成订单事实数据 (5000订单 - PostgreSQL 简化版)
-- ==========================================
DO $$
DECLARE
    v_order_id VARCHAR(32);
    v_rand_date DATE;
    v_date_key INT;
    v_customer_key INT;
    v_store_key INT;
    v_channel_key INT;
    v_promotion_key INT;
    v_order_status VARCHAR(20);
    v_payment_status VARCHAR(20);
    v_pay_method VARCHAR(50);
    v_line_count INT;
    v_total_quantity INT;
    v_total_amount DECIMAL(18,2);
    v_discount_amount DECIMAL(18,2);
    v_freight_amount DECIMAL(18,2);
    v_order_time TIMESTAMP;
    v_product_key INT;
    v_quantity INT;
    v_unit_price DECIMAL(18,2);
    v_unit_cost DECIMAL(18,2);
    v_line_amount DECIMAL(18,2);
    j INT;
BEGIN
    FOR i IN 1..5000 LOOP
        -- 随机日期 (2022-2024)
        v_rand_date := '2022-01-01'::DATE + (RANDOM() * 1095)::INT;
        v_date_key := CAST(TO_CHAR(v_rand_date, 'YYYYMMDD') AS INT);
        v_order_time := v_rand_date + (RANDOM() * INTERVAL '24 hours');

        v_order_id := 'ORD' || TO_CHAR(v_rand_date, 'YYYYMMDD') || LPAD(i::TEXT, 6, '0');
        v_customer_key := 1 + (RANDOM() * 999)::INT;
        v_store_key := 1 + (RANDOM() * 49)::INT;
        v_channel_key := 1 + (RANDOM() * 9)::INT;
        v_promotion_key := CASE WHEN RANDOM() < 0.3 THEN 1 + (RANDOM() * 29)::INT ELSE 1 END;

        -- 订单状态分布
        CASE (RANDOM() * 10)::INT
            WHEN 0 THEN v_order_status := 'PENDING'; v_payment_status := 'UNPAID';
            WHEN 1 THEN v_order_status := 'CANCELLED'; v_payment_status := 'UNPAID';
            WHEN 2 THEN v_order_status := 'PAID'; v_payment_status := 'PAID';
            WHEN 3 THEN v_order_status := 'SHIPPED'; v_payment_status := 'PAID';
            ELSE v_order_status := 'COMPLETED'; v_payment_status := 'PAID';
        END CASE;

        -- 支付方式
        v_pay_method := (ARRAY['ALIPAY', 'WECHAT', 'CARD', 'CASH'])[1 + (RANDOM() * 4)::INT % 4];

        -- 订单明细行数 (1-5行)
        v_line_count := 1 + (RANDOM() * 5)::INT;
        v_total_quantity := 0;
        v_total_amount := 0;

        -- 生成订单明细
        FOR j IN 1..v_line_count LOOP
            v_product_key := 1 + (RANDOM() * 499)::INT;
            v_quantity := 1 + (RANDOM() * 3)::INT;

            -- 获取商品价格
            SELECT unit_price, COALESCE(dp.unit_cost, unit_price * 0.6)
            INTO v_unit_price, v_unit_cost
            FROM dim_product dp WHERE product_key = v_product_key;

            IF v_unit_price IS NULL THEN
                v_unit_price := 100 + (RANDOM() * 900)::DECIMAL(18,2);
                v_unit_cost := v_unit_price * 0.6;
            END IF;

            v_line_amount := v_quantity * v_unit_price;
            v_total_quantity := v_total_quantity + v_quantity;
            v_total_amount := v_total_amount + v_line_amount;

            -- 插入销售明细
            INSERT INTO fact_sales (
                order_id, order_line_no, date_key, product_key, customer_key,
                store_key, channel_key, promotion_key, quantity, unit_price,
                unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                order_status, payment_method
            ) VALUES (
                v_order_id, j, v_date_key, v_product_key, v_customer_key,
                v_store_key, v_channel_key, v_promotion_key, v_quantity, v_unit_price,
                v_unit_cost, 0, v_line_amount, v_quantity * v_unit_cost,
                v_line_amount - v_quantity * v_unit_cost,
                v_order_status, v_pay_method
            );
        END LOOP;

        -- 计算折扣和运费
        v_discount_amount := CASE WHEN v_promotion_key > 1 THEN v_total_amount * (RANDOM() * 0.2) ELSE 0 END;
        v_freight_amount := CASE WHEN v_total_amount > 99 THEN 0 ELSE 10 END;

        -- 插入订单头
        INSERT INTO fact_order (
            order_id, date_key, customer_key, store_key, channel_key, promotion_key,
            total_quantity, total_amount, discount_amount, freight_amount, pay_amount,
            order_status, payment_status, order_time
        ) VALUES (
            v_order_id, v_date_key, v_customer_key, v_store_key, v_channel_key, v_promotion_key,
            v_total_quantity, v_total_amount, v_discount_amount, v_freight_amount,
            v_total_amount - v_discount_amount + v_freight_amount,
            v_order_status, v_payment_status, v_order_time
        );

        -- 每1000条输出进度
        IF i % 1000 = 0 THEN
            RAISE NOTICE 'Generated % orders...', i;
        END IF;
    END LOOP;
END $$;

-- ==========================================
-- 8. 生成支付事实数据
-- ==========================================
INSERT INTO fact_payment (payment_id, order_id, date_key, customer_key, pay_amount, pay_method, pay_channel, pay_status, pay_time)
SELECT
    'PAY' || LPAD(ROW_NUMBER() OVER ()::TEXT, 8, '0'),
    order_id,
    date_key,
    customer_key,
    pay_amount,
    (ARRAY['ALIPAY', 'WECHAT', 'CARD', 'CASH'])[1 + (RANDOM() * 4)::INT % 4],
    (ARRAY['APP', 'WEB', 'POS'])[1 + (RANDOM() * 3)::INT % 3],
    CASE payment_status
        WHEN 'PAID' THEN 'SUCCESS'
        WHEN 'REFUNDED' THEN 'REFUNDED'
        ELSE 'FAILED'
    END,
    order_time + (RANDOM() * INTERVAL '1 hour')
FROM fact_order
WHERE payment_status IN ('PAID', 'REFUNDED');

-- ==========================================
-- 9. 生成退货事实数据 (约5%退货率)
-- ==========================================
INSERT INTO fact_return (return_id, order_id, order_line_no, date_key, product_key, customer_key, store_key, return_quantity, return_amount, return_reason, return_type, return_status, return_time)
SELECT
    'RTN' || LPAD(ROW_NUMBER() OVER ()::TEXT, 8, '0'),
    order_id,
    order_line_no,
    date_key + (RANDOM() * 7)::INT,
    product_key,
    customer_key,
    store_key,
    GREATEST(1, CEIL(quantity * RANDOM())),
    sales_amount * RANDOM(),
    (ARRAY['质量问题', '尺码不合适', '不喜欢', '发错货', '其他原因'])[1 + (RANDOM() * 5)::INT % 5],
    CASE WHEN RANDOM() < 0.3 THEN 'REFUND_ONLY' ELSE 'RETURN_REFUND' END,
    (ARRAY['PENDING', 'APPROVED', 'COMPLETED', 'REJECTED'])[1 + (RANDOM() * 4)::INT % 4],
    TO_TIMESTAMP(date_key::TEXT, 'YYYYMMDD') + (1 + (RANDOM() * 7)::INT) * INTERVAL '1 day'
FROM fact_sales
WHERE order_status = 'COMPLETED'
ORDER BY RANDOM()
LIMIT 1000;

-- ==========================================
-- 10. 生成库存快照数据 (简化版)
-- ==========================================
INSERT INTO fact_inventory_snapshot (date_key, product_key, store_key, quantity_on_hand, quantity_reserved, quantity_available, unit_cost, inventory_value)
SELECT
    20240101,
    p.product_key,
    s.store_key,
    (50 + RANDOM() * 200)::INT AS qty_on_hand,
    (RANDOM() * 20)::INT AS qty_reserved,
    (50 + RANDOM() * 200 - RANDOM() * 20)::INT AS qty_available,
    p.unit_cost,
    (50 + RANDOM() * 200) * p.unit_cost AS inv_value
FROM dim_product p
CROSS JOIN dim_store s
WHERE p.status = 'ACTIVE' AND s.status = 'ACTIVE'
LIMIT 5000;  -- 限制数量避免数据过大

-- 完成统计
DO $$
BEGIN
    RAISE NOTICE '===== Test Data Generation Completed =====';
END $$;

SELECT 'dim_date' AS table_name, COUNT(*) AS row_count FROM dim_date
UNION ALL SELECT 'dim_product', COUNT(*) FROM dim_product
UNION ALL SELECT 'dim_customer', COUNT(*) FROM dim_customer
UNION ALL SELECT 'dim_store', COUNT(*) FROM dim_store
UNION ALL SELECT 'dim_channel', COUNT(*) FROM dim_channel
UNION ALL SELECT 'dim_promotion', COUNT(*) FROM dim_promotion
UNION ALL SELECT 'fact_order', COUNT(*) FROM fact_order
UNION ALL SELECT 'fact_sales', COUNT(*) FROM fact_sales
UNION ALL SELECT 'fact_payment', COUNT(*) FROM fact_payment
UNION ALL SELECT 'fact_return', COUNT(*) FROM fact_return
UNION ALL SELECT 'fact_inventory_snapshot', COUNT(*) FROM fact_inventory_snapshot
UNION ALL SELECT 'dict_region', COUNT(*) FROM dict_region
UNION ALL SELECT 'dict_category', COUNT(*) FROM dict_category
UNION ALL SELECT 'dict_status', COUNT(*) FROM dict_status;

-- ============================================
-- 父子维度测试数据 (Parent-Child Dimension)
-- ============================================

-- 11. 团队维度数据（层级结构）
INSERT INTO dim_team (team_id, team_name, parent_id, team_level, manager_name, status) VALUES
('T001', '总公司', NULL, 1, '张总', 'ACTIVE'),
('T002', '技术部', 'T001', 2, '李经理', 'ACTIVE'),
('T003', '研发组', 'T002', 3, '王组长', 'ACTIVE'),
('T004', '测试组', 'T002', 3, '赵组长', 'ACTIVE'),
('T005', '销售部', 'T001', 2, '钱经理', 'ACTIVE'),
('T006', '前端小组', 'T003', 4, '孙组长', 'ACTIVE'),
('T007', '后端小组', 'T003', 4, '周组长', 'ACTIVE'),
('T008', '华东区', 'T005', 3, '吴经理', 'ACTIVE'),
('T009', '华北区', 'T005', 3, '郑经理', 'ACTIVE');

-- 12. 团队闭包表数据
INSERT INTO team_closure (parent_id, team_id, distance) VALUES
('T001', 'T001', 0), ('T001', 'T002', 1), ('T001', 'T003', 2), ('T001', 'T004', 2),
('T001', 'T005', 1), ('T001', 'T006', 3), ('T001', 'T007', 3), ('T001', 'T008', 2), ('T001', 'T009', 2),
('T002', 'T002', 0), ('T002', 'T003', 1), ('T002', 'T004', 1), ('T002', 'T006', 2), ('T002', 'T007', 2),
('T003', 'T003', 0), ('T003', 'T006', 1), ('T003', 'T007', 1),
('T004', 'T004', 0),
('T005', 'T005', 0), ('T005', 'T008', 1), ('T005', 'T009', 1),
('T006', 'T006', 0), ('T007', 'T007', 0), ('T008', 'T008', 0), ('T009', 'T009', 0);

-- 13. 团队销售事实数据
INSERT INTO fact_team_sales (team_id, date_key, sales_amount, sales_count) VALUES
('T001', 20240101, 50000.00, 5), ('T001', 20240102, 60000.00, 6),
('T002', 20240101, 30000.00, 3), ('T002', 20240102, 35000.00, 4),
('T003', 20240101, 10000.00, 2), ('T003', 20240102, 12000.00, 2),
('T004', 20240101, 8000.00, 1), ('T004', 20240102, 9000.00, 1),
('T005', 20240101, 100000.00, 20), ('T005', 20240102, 120000.00, 25),
('T006', 20240101, 5000.00, 1), ('T006', 20240102, 6000.00, 1),
('T007', 20240101, 7000.00, 1), ('T007', 20240102, 8000.00, 2),
('T008', 20240101, 45000.00, 10), ('T008', 20240102, 55000.00, 12),
('T009', 20240101, 40000.00, 8), ('T009', 20240102, 48000.00, 10);

-- ============================================
-- 嵌套维度测试数据 (Nested Dimension / Snowflake Schema)
-- ============================================

-- 14. 品类组维度数据（三级维度）
INSERT INTO dim_category_group (group_id, group_name, group_type, status) VALUES
('GRP001', '电子产品组', '高价值', 'ACTIVE'),
('GRP002', '日用品组', '快消品', 'ACTIVE'),
('GRP003', '服装配饰组', '时尚', 'ACTIVE');

-- 15. 品类维度数据（二级维度）
INSERT INTO dim_category_nested (category_id, category_name, category_level, group_key, status) VALUES
('CAT001', '数码电器', 1, 1, 'ACTIVE'),
('CAT002', '手机通讯', 2, 1, 'ACTIVE'),
('CAT003', '电脑办公', 2, 1, 'ACTIVE'),
('CAT004', '家居用品', 1, 2, 'ACTIVE'),
('CAT005', '厨房用品', 2, 2, 'ACTIVE'),
('CAT006', '男装', 1, 3, 'ACTIVE'),
('CAT007', '女装', 1, 3, 'ACTIVE');

-- 16. 嵌套产品维度数据
INSERT INTO dim_product_nested (product_id, product_name, brand, category_key, unit_price, status) VALUES
('NP001', 'iPhone 15 Pro', 'Apple', 2, 8999.00, 'ACTIVE'),
('NP002', '华为 Mate 60', '华为', 2, 6999.00, 'ACTIVE'),
('NP003', 'MacBook Pro 14', 'Apple', 3, 14999.00, 'ACTIVE'),
('NP004', '联想 ThinkPad', '联想', 3, 7999.00, 'ACTIVE'),
('NP005', '不锈钢炒锅', '苏泊尔', 5, 299.00, 'ACTIVE'),
('NP006', '电饭煲', '美的', 5, 499.00, 'ACTIVE'),
('NP007', '休闲T恤', '优衣库', 6, 99.00, 'ACTIVE'),
('NP008', '牛仔裤', 'Levis', 6, 599.00, 'ACTIVE'),
('NP009', '连衣裙', 'ZARA', 7, 399.00, 'ACTIVE'),
('NP010', '风衣外套', 'MAX&Co.', 7, 1299.00, 'ACTIVE');

-- 17. 区域维度数据
INSERT INTO dim_region_nested (region_id, region_name, province, city, status) VALUES
('REG001', '华东区-杭州', '浙江省', '杭州市', 'ACTIVE'),
('REG002', '华东区-上海', '上海市', '上海市', 'ACTIVE'),
('REG003', '华北区-北京', '北京市', '北京市', 'ACTIVE'),
('REG004', '华南区-广州', '广东省', '广州市', 'ACTIVE'),
('REG005', '华南区-深圳', '广东省', '深圳市', 'ACTIVE');

-- 18. 嵌套门店维度数据
INSERT INTO dim_store_nested (store_id, store_name, store_type, region_key, status) VALUES
('NS001', '杭州西湖旗舰店', '旗舰店', 1, 'ACTIVE'),
('NS002', '杭州滨江店', '标准店', 1, 'ACTIVE'),
('NS003', '上海南京路店', '旗舰店', 2, 'ACTIVE'),
('NS004', '北京王府井店', '旗舰店', 3, 'ACTIVE'),
('NS005', '广州天河店', '标准店', 4, 'ACTIVE'),
('NS006', '深圳华强北店', '标准店', 5, 'ACTIVE');

-- 19. 嵌套维度销售事实数据
INSERT INTO fact_sales_nested (date_key, product_key, store_key, quantity, sales_amount, cost_amount) VALUES
(20240101, 1, 1, 2, 17998.00, 10800.00),
(20240101, 2, 1, 1, 6999.00, 4200.00),
(20240101, 3, 3, 1, 14999.00, 9000.00),
(20240101, 5, 2, 5, 1495.00, 750.00),
(20240101, 7, 4, 10, 990.00, 400.00),
(20240101, 9, 5, 3, 1197.00, 600.00),
(20240102, 1, 3, 3, 26997.00, 16200.00),
(20240102, 4, 4, 2, 15998.00, 9600.00),
(20240102, 6, 1, 4, 1996.00, 1000.00),
(20240102, 8, 6, 5, 2995.00, 1500.00),
(20240102, 10, 5, 2, 2598.00, 1300.00),
(20240103, 2, 4, 2, 13998.00, 8400.00),
(20240103, 3, 1, 1, 14999.00, 9000.00),
(20240103, 5, 5, 8, 2392.00, 1200.00),
(20240103, 7, 6, 15, 1485.00, 600.00),
(20240103, 9, 2, 4, 1596.00, 800.00);

-- 最终统计
DO $$
BEGIN
    RAISE NOTICE '===== Parent-Child & Nested Dimension Data =====';
END $$;

SELECT 'dim_team' AS table_name, COUNT(*) AS row_count FROM dim_team
UNION ALL SELECT 'team_closure', COUNT(*) FROM team_closure
UNION ALL SELECT 'fact_team_sales', COUNT(*) FROM fact_team_sales
UNION ALL SELECT 'dim_category_group', COUNT(*) FROM dim_category_group
UNION ALL SELECT 'dim_category_nested', COUNT(*) FROM dim_category_nested
UNION ALL SELECT 'dim_product_nested', COUNT(*) FROM dim_product_nested
UNION ALL SELECT 'dim_region_nested', COUNT(*) FROM dim_region_nested
UNION ALL SELECT 'dim_store_nested', COUNT(*) FROM dim_store_nested
UNION ALL SELECT 'fact_sales_nested', COUNT(*) FROM fact_sales_nested;
