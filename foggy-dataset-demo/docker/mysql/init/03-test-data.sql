-- ============================================
-- Foggy Dataset Model 测试数据生成
-- 文件: 03-test-data.sql
-- 说明: 生成维度表和事实表测试数据
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================
-- 1. 生成日期维度数据 (2022-2024, 3年)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_dim_date//
CREATE PROCEDURE generate_dim_date()
BEGIN
    DECLARE v_date DATE DEFAULT '2022-01-01';
    DECLARE v_end_date DATE DEFAULT '2024-12-31';
    DECLARE v_date_key INT;
    DECLARE v_day_names VARCHAR(100) DEFAULT '星期一,星期二,星期三,星期四,星期五,星期六,星期日';
    DECLARE v_month_names VARCHAR(200) DEFAULT '一月,二月,三月,四月,五月,六月,七月,八月,九月,十月,十一月,十二月';

    WHILE v_date <= v_end_date DO
        SET v_date_key = YEAR(v_date) * 10000 + MONTH(v_date) * 100 + DAY(v_date);

        INSERT INTO dim_date (
            date_key, full_date, year, quarter, month, month_name,
            week_of_year, day_of_month, day_of_week, day_name,
            is_weekend, is_holiday, fiscal_year, fiscal_quarter
        ) VALUES (
            v_date_key,
            v_date,
            YEAR(v_date),
            QUARTER(v_date),
            MONTH(v_date),
            SUBSTRING_INDEX(SUBSTRING_INDEX(v_month_names, ',', MONTH(v_date)), ',', -1),
            WEEK(v_date, 1),
            DAY(v_date),
            DAYOFWEEK(v_date),
            SUBSTRING_INDEX(SUBSTRING_INDEX(v_day_names, ',', DAYOFWEEK(v_date)), ',', -1),
            IF(DAYOFWEEK(v_date) IN (1, 7), 1, 0),
            0,  -- 节假日需要单独处理
            YEAR(v_date),
            QUARTER(v_date)
        );

        SET v_date = DATE_ADD(v_date, INTERVAL 1 DAY);
    END WHILE;
END//
DELIMITER ;

CALL generate_dim_date();
DROP PROCEDURE IF EXISTS generate_dim_date;

-- ==========================================
-- 2. 生成渠道维度数据 (10条)
-- ==========================================
INSERT INTO `dim_channel` (`channel_id`, `channel_name`, `channel_type`, `platform`, `status`) VALUES
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
INSERT INTO `dim_promotion` (`promotion_id`, `promotion_name`, `promotion_type`, `discount_rate`, `start_date`, `end_date`, `status`) VALUES
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
DELIMITER //
DROP PROCEDURE IF EXISTS generate_dim_store//
CREATE PROCEDURE generate_dim_store()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_store_type VARCHAR(20);
    DECLARE v_provinces TEXT DEFAULT '浙江省,江苏省,广东省,上海市,北京市';
    DECLARE v_cities TEXT DEFAULT '杭州市,南京市,广州市,上海市,北京市';
    DECLARE v_districts TEXT DEFAULT '西湖区,玄武区,天河区,浦东新区,朝阳区';
    DECLARE v_province VARCHAR(50);
    DECLARE v_city VARCHAR(50);
    DECLARE v_district VARCHAR(50);
    DECLARE v_idx INT;

    WHILE i <= 50 DO
        SET v_idx = (i - 1) MOD 5 + 1;
        SET v_province = SUBSTRING_INDEX(SUBSTRING_INDEX(v_provinces, ',', v_idx), ',', -1);
        SET v_city = SUBSTRING_INDEX(SUBSTRING_INDEX(v_cities, ',', v_idx), ',', -1);
        SET v_district = SUBSTRING_INDEX(SUBSTRING_INDEX(v_districts, ',', v_idx), ',', -1);
        SET v_store_type = IF(i <= 30, '直营', '加盟');

        INSERT INTO dim_store (
            store_id, store_name, store_type, province, city, district,
            address, manager_name, open_date, area_sqm, status
        ) VALUES (
            CONCAT('STR', LPAD(i, 5, '0')),
            CONCAT(v_city, v_district, '店', i),
            v_store_type,
            v_province,
            v_city,
            v_district,
            CONCAT(v_district, '某路', i, '号'),
            CONCAT('店长', i),
            DATE_ADD('2020-01-01', INTERVAL FLOOR(RAND() * 1000) DAY),
            200 + FLOOR(RAND() * 800),
            'ACTIVE'
        );

        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL generate_dim_store();
DROP PROCEDURE IF EXISTS generate_dim_store;

-- ==========================================
-- 5. 生成商品维度数据 (500条)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_dim_product//
CREATE PROCEDURE generate_dim_product()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_cat_id VARCHAR(32);
    DECLARE v_cat_name VARCHAR(100);
    DECLARE v_sub_cat_id VARCHAR(32);
    DECLARE v_sub_cat_name VARCHAR(100);
    DECLARE v_brands TEXT DEFAULT 'Apple,华为,小米,三星,OPPO,vivo,联想,戴尔,惠普,Nike,Adidas,优衣库,海尔,美的,格力';
    DECLARE v_brand VARCHAR(100);
    DECLARE v_price DECIMAL(18,2);
    DECLARE v_cost DECIMAL(18,2);

    WHILE i <= 500 DO
        -- 根据序号分配品类
        CASE
            WHEN i <= 100 THEN
                SET v_cat_id = 'CAT001', v_cat_name = '数码电器';
                IF i <= 40 THEN SET v_sub_cat_id = 'CAT001001', v_sub_cat_name = '手机通讯';
                ELSEIF i <= 70 THEN SET v_sub_cat_id = 'CAT001002', v_sub_cat_name = '电脑办公';
                ELSE SET v_sub_cat_id = 'CAT001003', v_sub_cat_name = '家用电器';
                END IF;
            WHEN i <= 200 THEN
                SET v_cat_id = 'CAT002', v_cat_name = '服装配饰';
                IF i <= 140 THEN SET v_sub_cat_id = 'CAT002001', v_sub_cat_name = '男装';
                ELSEIF i <= 180 THEN SET v_sub_cat_id = 'CAT002002', v_sub_cat_name = '女装';
                ELSE SET v_sub_cat_id = 'CAT002003', v_sub_cat_name = '鞋靴';
                END IF;
            WHEN i <= 300 THEN
                SET v_cat_id = 'CAT003', v_cat_name = '食品饮料';
                SET v_sub_cat_id = 'CAT003001', v_sub_cat_name = '休闲零食';
            WHEN i <= 400 THEN
                SET v_cat_id = 'CAT004', v_cat_name = '家居日用';
                SET v_sub_cat_id = 'CAT004001', v_sub_cat_name = '家具';
            ELSE
                SET v_cat_id = 'CAT005', v_cat_name = '美妆个护';
                SET v_sub_cat_id = 'CAT005001', v_sub_cat_name = '护肤';
        END CASE;

        -- 随机品牌
        SET v_brand = SUBSTRING_INDEX(SUBSTRING_INDEX(v_brands, ',', 1 + (i MOD 15)), ',', -1);

        -- 随机价格
        SET v_price = 50 + FLOOR(RAND() * 9950);
        SET v_cost = v_price * (0.4 + RAND() * 0.3);

        INSERT INTO dim_product (
            product_id, product_name, category_id, category_name,
            sub_category_id, sub_category_name, brand, unit_price, unit_cost, status
        ) VALUES (
            CONCAT('PRD', LPAD(i, 6, '0')),
            CONCAT(v_brand, ' ', v_sub_cat_name, '商品', i),
            v_cat_id,
            v_cat_name,
            v_sub_cat_id,
            v_sub_cat_name,
            v_brand,
            v_price,
            v_cost,
            'ACTIVE'
        );

        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL generate_dim_product();
DROP PROCEDURE IF EXISTS generate_dim_product;

-- ==========================================
-- 6. 生成客户维度数据 (1000条)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_dim_customer//
CREATE PROCEDURE generate_dim_customer()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_customer_type VARCHAR(20);
    DECLARE v_gender VARCHAR(10);
    DECLARE v_age_group VARCHAR(20);
    DECLARE v_member_level VARCHAR(20);
    DECLARE v_provinces TEXT DEFAULT '浙江省,江苏省,广东省,上海市,北京市,四川省';
    DECLARE v_cities TEXT DEFAULT '杭州市,南京市,广州市,上海市,北京市,成都市';
    DECLARE v_districts TEXT DEFAULT '西湖区,玄武区,天河区,浦东新区,朝阳区,武侯区';
    DECLARE v_idx INT;

    WHILE i <= 1000 DO
        SET v_idx = (i - 1) MOD 6 + 1;

        -- 客户类型分布: 10% VIP, 30% NORMAL, 60% NEW
        IF i <= 100 THEN SET v_customer_type = 'VIP';
        ELSEIF i <= 400 THEN SET v_customer_type = 'NORMAL';
        ELSE SET v_customer_type = 'NEW';
        END IF;

        -- 性别随机
        SET v_gender = IF(RAND() < 0.48, '男', IF(RAND() < 0.96, '女', '未知'));

        -- 年龄段分布
        CASE FLOOR(RAND() * 5)
            WHEN 0 THEN SET v_age_group = '18-24';
            WHEN 1 THEN SET v_age_group = '25-34';
            WHEN 2 THEN SET v_age_group = '35-44';
            WHEN 3 THEN SET v_age_group = '45-54';
            ELSE SET v_age_group = '55+';
        END CASE;

        -- 会员等级
        CASE FLOOR(RAND() * 5)
            WHEN 0 THEN SET v_member_level = 'DIAMOND';
            WHEN 1 THEN SET v_member_level = 'PLATINUM';
            WHEN 2 THEN SET v_member_level = 'GOLD';
            WHEN 3 THEN SET v_member_level = 'SILVER';
            ELSE SET v_member_level = 'BRONZE';
        END CASE;

        INSERT INTO dim_customer (
            customer_id, customer_name, customer_type, gender, age_group,
            province, city, district, register_date, member_level, status
        ) VALUES (
            CONCAT('CUS', LPAD(i, 6, '0')),
            CONCAT('客户', i),
            v_customer_type,
            v_gender,
            v_age_group,
            SUBSTRING_INDEX(SUBSTRING_INDEX(v_provinces, ',', v_idx), ',', -1),
            SUBSTRING_INDEX(SUBSTRING_INDEX(v_cities, ',', v_idx), ',', -1),
            SUBSTRING_INDEX(SUBSTRING_INDEX(v_districts, ',', v_idx), ',', -1),
            DATE_ADD('2020-01-01', INTERVAL FLOOR(RAND() * 1500) DAY),
            v_member_level,
            'ACTIVE'
        );

        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL generate_dim_customer();
DROP PROCEDURE IF EXISTS generate_dim_customer;

-- ==========================================
-- 7. 生成订单事实数据 (20000订单, 100000明细)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_fact_orders//
CREATE PROCEDURE generate_fact_orders()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE j INT;
    DECLARE v_order_id VARCHAR(32);
    DECLARE v_date_key INT;
    DECLARE v_customer_key INT;
    DECLARE v_store_key INT;
    DECLARE v_channel_key INT;
    DECLARE v_promotion_key INT;
    DECLARE v_product_key INT;
    DECLARE v_order_time DATETIME;
    DECLARE v_order_status VARCHAR(20);
    DECLARE v_payment_status VARCHAR(20);
    DECLARE v_pay_method VARCHAR(50);
    DECLARE v_line_count INT;
    DECLARE v_quantity INT;
    DECLARE v_unit_price DECIMAL(18,2);
    DECLARE v_unit_cost DECIMAL(18,2);
    DECLARE v_line_amount DECIMAL(18,2);
    DECLARE v_total_quantity INT;
    DECLARE v_total_amount DECIMAL(18,2);
    DECLARE v_discount_amount DECIMAL(18,2);
    DECLARE v_freight_amount DECIMAL(18,2);
    DECLARE v_pay_amount DECIMAL(18,2);
    DECLARE v_rand_date DATE;

    -- 遍历生成订单
    WHILE i <= 20000 DO
        -- 随机日期 (2022-2024)
        SET v_rand_date = DATE_ADD('2022-01-01', INTERVAL FLOOR(RAND() * 1095) DAY);
        SET v_date_key = YEAR(v_rand_date) * 10000 + MONTH(v_rand_date) * 100 + DAY(v_rand_date);
        SET v_order_time = ADDTIME(v_rand_date, SEC_TO_TIME(FLOOR(RAND() * 86400)));

        SET v_order_id = CONCAT('ORD', DATE_FORMAT(v_rand_date, '%Y%m%d'), LPAD(i, 6, '0'));
        SET v_customer_key = 1 + FLOOR(RAND() * 1000);
        SET v_store_key = 1 + FLOOR(RAND() * 50);
        SET v_channel_key = 1 + FLOOR(RAND() * 10);
        SET v_promotion_key = IF(RAND() < 0.3, 1 + FLOOR(RAND() * 30), 1); -- 30%有促销

        -- 订单状态分布
        CASE FLOOR(RAND() * 10)
            WHEN 0 THEN SET v_order_status = 'PENDING', v_payment_status = 'UNPAID';
            WHEN 1 THEN SET v_order_status = 'CANCELLED', v_payment_status = 'UNPAID';
            WHEN 2 THEN SET v_order_status = 'PAID', v_payment_status = 'PAID';
            WHEN 3 THEN SET v_order_status = 'SHIPPED', v_payment_status = 'PAID';
            ELSE SET v_order_status = 'COMPLETED', v_payment_status = 'PAID';
        END CASE;

        -- 支付方式
        CASE FLOOR(RAND() * 4)
            WHEN 0 THEN SET v_pay_method = 'ALIPAY';
            WHEN 1 THEN SET v_pay_method = 'WECHAT';
            WHEN 2 THEN SET v_pay_method = 'CARD';
            ELSE SET v_pay_method = 'CASH';
        END CASE;

        -- 订单明细行数 (1-10行)
        SET v_line_count = 1 + FLOOR(RAND() * 10);
        SET v_total_quantity = 0;
        SET v_total_amount = 0;

        -- 生成订单明细
        SET j = 1;
        WHILE j <= v_line_count DO
            SET v_product_key = 1 + FLOOR(RAND() * 500);
            SET v_quantity = 1 + FLOOR(RAND() * 5);

            -- 获取商品价格
            SELECT unit_price, unit_cost INTO v_unit_price, v_unit_cost
            FROM dim_product WHERE product_key = v_product_key;

            IF v_unit_price IS NULL THEN
                SET v_unit_price = 100 + FLOOR(RAND() * 900);
                SET v_unit_cost = v_unit_price * 0.6;
            END IF;

            SET v_line_amount = v_quantity * v_unit_price;
            SET v_total_quantity = v_total_quantity + v_quantity;
            SET v_total_amount = v_total_amount + v_line_amount;

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

            SET j = j + 1;
        END WHILE;

        -- 计算折扣和运费
        SET v_discount_amount = IF(v_promotion_key > 1, v_total_amount * (RAND() * 0.2), 0);
        SET v_freight_amount = IF(v_total_amount > 99, 0, 10);
        SET v_pay_amount = v_total_amount - v_discount_amount + v_freight_amount;

        -- 插入订单头
        INSERT INTO fact_order (
            order_id, date_key, customer_key, store_key, channel_key, promotion_key,
            total_quantity, total_amount, discount_amount, freight_amount, pay_amount,
            order_status, payment_status, order_time
        ) VALUES (
            v_order_id, v_date_key, v_customer_key, v_store_key, v_channel_key, v_promotion_key,
            v_total_quantity, v_total_amount, v_discount_amount, v_freight_amount, v_pay_amount,
            v_order_status, v_payment_status, v_order_time
        );

        SET i = i + 1;

        -- 每1000条提交一次，避免日志过大
        IF i MOD 1000 = 0 THEN
            SELECT CONCAT('Generated ', i, ' orders...') AS progress;
        END IF;
    END WHILE;
END//
DELIMITER ;

CALL generate_fact_orders();
DROP PROCEDURE IF EXISTS generate_fact_orders;

-- ==========================================
-- 8. 生成支付事实数据 (MySQL 5.7 兼容版本)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_fact_payment//
CREATE PROCEDURE generate_fact_payment()
BEGIN
    DECLARE v_row_num INT DEFAULT 0;
    DECLARE v_order_id VARCHAR(32);
    DECLARE v_date_key INT;
    DECLARE v_customer_key INT;
    DECLARE v_pay_amount DECIMAL(18,2);
    DECLARE v_payment_status VARCHAR(20);
    DECLARE v_order_time DATETIME;
    DECLARE v_done INT DEFAULT FALSE;

    DECLARE cur CURSOR FOR
        SELECT order_id, date_key, customer_key, pay_amount, payment_status, order_time
        FROM fact_order
        WHERE payment_status IN ('PAID', 'REFUNDED');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_order_id, v_date_key, v_customer_key, v_pay_amount, v_payment_status, v_order_time;
        IF v_done THEN
            LEAVE read_loop;
        END IF;

        SET v_row_num = v_row_num + 1;

        INSERT INTO fact_payment (payment_id, order_id, date_key, customer_key, pay_amount, pay_method, pay_channel, pay_status, pay_time)
        VALUES (
            CONCAT('PAY', LPAD(v_row_num, 8, '0')),
            v_order_id,
            v_date_key,
            v_customer_key,
            v_pay_amount,
            ELT(1 + FLOOR(RAND() * 4), 'ALIPAY', 'WECHAT', 'CARD', 'CASH'),
            ELT(1 + FLOOR(RAND() * 3), 'APP', 'WEB', 'POS'),
            CASE v_payment_status
                WHEN 'PAID' THEN 'SUCCESS'
                WHEN 'REFUNDED' THEN 'REFUNDED'
                ELSE 'FAILED'
            END,
            DATE_ADD(v_order_time, INTERVAL FLOOR(RAND() * 3600) SECOND)
        );
    END LOOP;

    CLOSE cur;

    SELECT CONCAT('Generated ', v_row_num, ' payment records') AS progress;
END//
DELIMITER ;

CALL generate_fact_payment();
DROP PROCEDURE IF EXISTS generate_fact_payment;

-- 10%订单有多次支付尝试 (MySQL 5.7 兼容版本)
DELIMITER //
DROP PROCEDURE IF EXISTS generate_failed_payments//
CREATE PROCEDURE generate_failed_payments()
BEGIN
    DECLARE v_row_num INT DEFAULT 22000;
    DECLARE v_order_id VARCHAR(32);
    DECLARE v_date_key INT;
    DECLARE v_customer_key INT;
    DECLARE v_pay_amount DECIMAL(18,2);
    DECLARE v_order_time DATETIME;
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_done INT DEFAULT FALSE;

    DECLARE cur CURSOR FOR
        SELECT order_id, date_key, customer_key, pay_amount, order_time
        FROM fact_order
        WHERE payment_status = 'PAID'
        ORDER BY RAND()
        LIMIT 2000;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_order_id, v_date_key, v_customer_key, v_pay_amount, v_order_time;
        IF v_done THEN
            LEAVE read_loop;
        END IF;

        SET v_row_num = v_row_num + 1;
        SET v_count = v_count + 1;

        INSERT INTO fact_payment (payment_id, order_id, date_key, customer_key, pay_amount, pay_method, pay_channel, pay_status, pay_time)
        VALUES (
            CONCAT('PAY', LPAD(v_row_num, 8, '0')),
            v_order_id,
            v_date_key,
            v_customer_key,
            v_pay_amount,
            'WECHAT',
            'APP',
            'FAILED',
            DATE_ADD(v_order_time, INTERVAL -300 SECOND)
        );
    END LOOP;

    CLOSE cur;

    SELECT CONCAT('Generated ', v_count, ' failed payment records') AS progress;
END//
DELIMITER ;

CALL generate_failed_payments();
DROP PROCEDURE IF EXISTS generate_failed_payments;

-- ==========================================
-- 9. 生成退货事实数据 (约5%退货率, MySQL 5.7 兼容版本)
-- ==========================================
DELIMITER //
DROP PROCEDURE IF EXISTS generate_fact_return//
CREATE PROCEDURE generate_fact_return()
BEGIN
    DECLARE v_row_num INT DEFAULT 0;
    DECLARE v_order_id VARCHAR(32);
    DECLARE v_order_line_no INT;
    DECLARE v_date_key INT;
    DECLARE v_product_key INT;
    DECLARE v_customer_key INT;
    DECLARE v_store_key INT;
    DECLARE v_quantity INT;
    DECLARE v_sales_amount DECIMAL(18,2);
    DECLARE v_done INT DEFAULT FALSE;

    DECLARE cur CURSOR FOR
        SELECT order_id, order_line_no, date_key, product_key, customer_key, store_key, quantity, sales_amount
        FROM fact_sales
        WHERE order_status = 'COMPLETED'
        ORDER BY RAND()
        LIMIT 5000;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_order_id, v_order_line_no, v_date_key, v_product_key, v_customer_key, v_store_key, v_quantity, v_sales_amount;
        IF v_done THEN
            LEAVE read_loop;
        END IF;

        SET v_row_num = v_row_num + 1;

        INSERT INTO fact_return (return_id, order_id, order_line_no, date_key, product_key, customer_key, store_key, return_quantity, return_amount, return_reason, return_type, return_status, return_time)
        VALUES (
            CONCAT('RTN', LPAD(v_row_num, 8, '0')),
            v_order_id,
            v_order_line_no,
            v_date_key + FLOOR(RAND() * 7),
            v_product_key,
            v_customer_key,
            v_store_key,
            GREATEST(1, CEIL(v_quantity * RAND())),
            v_sales_amount * RAND(),
            ELT(1 + FLOOR(RAND() * 5), '质量问题', '尺码不合适', '不喜欢', '发错货', '其他原因'),
            IF(RAND() < 0.3, 'REFUND_ONLY', 'RETURN_REFUND'),
            ELT(1 + FLOOR(RAND() * 4), 'PENDING', 'APPROVED', 'COMPLETED', 'REJECTED'),
            DATE_ADD(
                STR_TO_DATE(CONCAT(v_date_key, '120000'), '%Y%m%d%H%i%s'),
                INTERVAL (1 + FLOOR(RAND() * 7)) DAY
            )
        );
    END LOOP;

    CLOSE cur;

    SELECT CONCAT('Generated ', v_row_num, ' return records') AS progress;
END//
DELIMITER ;

CALL generate_fact_return();
DROP PROCEDURE IF EXISTS generate_fact_return;

-- ==========================================
-- 10. 生成库存快照数据
-- ==========================================
INSERT INTO fact_inventory_snapshot (date_key, product_key, store_key, quantity_on_hand, quantity_reserved, quantity_available, unit_cost, inventory_value)
SELECT
    20240101,  -- 使用一个固定日期作为快照
    p.product_key,
    s.store_key,
    50 + FLOOR(RAND() * 200) AS qty_on_hand,
    FLOOR(RAND() * 20) AS qty_reserved,
    50 + FLOOR(RAND() * 200) - FLOOR(RAND() * 20) AS qty_available,
    p.unit_cost,
    (50 + FLOOR(RAND() * 200)) * p.unit_cost AS inv_value
FROM dim_product p
CROSS JOIN dim_store s
WHERE p.status = 'ACTIVE' AND s.status = 'ACTIVE';

SET FOREIGN_KEY_CHECKS = 1;

-- 完成统计
SELECT '===== Test Data Generation Completed =====' AS message;
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
INSERT INTO `dim_team` (`team_id`, `team_name`, `parent_id`, `team_level`, `manager_name`, `status`) VALUES
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
INSERT INTO `team_closure` (`parent_id`, `team_id`, `distance`) VALUES
('T001', 'T001', 0), ('T001', 'T002', 1), ('T001', 'T003', 2), ('T001', 'T004', 2),
('T001', 'T005', 1), ('T001', 'T006', 3), ('T001', 'T007', 3), ('T001', 'T008', 2), ('T001', 'T009', 2),
('T002', 'T002', 0), ('T002', 'T003', 1), ('T002', 'T004', 1), ('T002', 'T006', 2), ('T002', 'T007', 2),
('T003', 'T003', 0), ('T003', 'T006', 1), ('T003', 'T007', 1),
('T004', 'T004', 0),
('T005', 'T005', 0), ('T005', 'T008', 1), ('T005', 'T009', 1),
('T006', 'T006', 0), ('T007', 'T007', 0), ('T008', 'T008', 0), ('T009', 'T009', 0);

-- 13. 团队销售事实数据
INSERT INTO `fact_team_sales` (`team_id`, `date_key`, `sales_amount`, `sales_count`) VALUES
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
INSERT INTO `dim_category_group` (`group_id`, `group_name`, `group_type`, `status`) VALUES
('GRP001', '电子产品组', '高价值', 'ACTIVE'),
('GRP002', '日用品组', '快消品', 'ACTIVE'),
('GRP003', '服装配饰组', '时尚', 'ACTIVE');

-- 15. 品类维度数据（二级维度）
INSERT INTO `dim_category_nested` (`category_id`, `category_name`, `category_level`, `group_key`, `status`) VALUES
('CAT001', '数码电器', 1, 1, 'ACTIVE'),
('CAT002', '手机通讯', 2, 1, 'ACTIVE'),
('CAT003', '电脑办公', 2, 1, 'ACTIVE'),
('CAT004', '家居用品', 1, 2, 'ACTIVE'),
('CAT005', '厨房用品', 2, 2, 'ACTIVE'),
('CAT006', '男装', 1, 3, 'ACTIVE'),
('CAT007', '女装', 1, 3, 'ACTIVE');

-- 16. 嵌套产品维度数据
INSERT INTO `dim_product_nested` (`product_id`, `product_name`, `brand`, `category_key`, `unit_price`, `status`) VALUES
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
INSERT INTO `dim_region_nested` (`region_id`, `region_name`, `province`, `city`, `status`) VALUES
('REG001', '华东区-杭州', '浙江省', '杭州市', 'ACTIVE'),
('REG002', '华东区-上海', '上海市', '上海市', 'ACTIVE'),
('REG003', '华北区-北京', '北京市', '北京市', 'ACTIVE'),
('REG004', '华南区-广州', '广东省', '广州市', 'ACTIVE'),
('REG005', '华南区-深圳', '广东省', '深圳市', 'ACTIVE');

-- 18. 嵌套门店维度数据
INSERT INTO `dim_store_nested` (`store_id`, `store_name`, `store_type`, `region_key`, `status`) VALUES
('NS001', '杭州西湖旗舰店', '旗舰店', 1, 'ACTIVE'),
('NS002', '杭州滨江店', '标准店', 1, 'ACTIVE'),
('NS003', '上海南京路店', '旗舰店', 2, 'ACTIVE'),
('NS004', '北京王府井店', '旗舰店', 3, 'ACTIVE'),
('NS005', '广州天河店', '标准店', 4, 'ACTIVE'),
('NS006', '深圳华强北店', '标准店', 5, 'ACTIVE');

-- 19. 嵌套维度销售事实数据
INSERT INTO `fact_sales_nested` (`date_key`, `product_key`, `store_key`, `quantity`, `sales_amount`, `cost_amount`) VALUES
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
SELECT '===== Parent-Child & Nested Dimension Data =====' AS message;
SELECT 'dim_team' AS table_name, COUNT(*) AS row_count FROM dim_team
UNION ALL SELECT 'team_closure', COUNT(*) FROM team_closure
UNION ALL SELECT 'fact_team_sales', COUNT(*) FROM fact_team_sales
UNION ALL SELECT 'dim_category_group', COUNT(*) FROM dim_category_group
UNION ALL SELECT 'dim_category_nested', COUNT(*) FROM dim_category_nested
UNION ALL SELECT 'dim_product_nested', COUNT(*) FROM dim_product_nested
UNION ALL SELECT 'dim_region_nested', COUNT(*) FROM dim_region_nested
UNION ALL SELECT 'dim_store_nested', COUNT(*) FROM dim_store_nested
UNION ALL SELECT 'fact_sales_nested', COUNT(*) FROM fact_sales_nested;
