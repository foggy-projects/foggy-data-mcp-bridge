-- ============================================
-- Foggy Dataset Model SQL Server 测试数据生成
-- 文件: 03-test-data.sql
-- 说明: 生成维度表和事实表测试数据
-- 执行: docker exec -it foggy-sqlserver-test /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test -i /scripts/03-test-data.sql
-- ============================================

USE foggy_test;
GO

-- ==========================================
-- 1. 生成日期维度数据 (2022-2024, 3年)
-- ==========================================
DECLARE @StartDate DATE = '2022-01-01';
DECLARE @EndDate DATE = '2024-12-31';

;WITH DateCTE AS (
    SELECT @StartDate AS DateValue
    UNION ALL
    SELECT DATEADD(DAY, 1, DateValue)
    FROM DateCTE
    WHERE DateValue < @EndDate
)
INSERT INTO dim_date (date_key, full_date, [year], [quarter], [month], month_name, week_of_year, day_of_month, day_of_week, day_name, is_weekend, is_holiday, fiscal_year, fiscal_quarter)
SELECT
    CAST(FORMAT(DateValue, 'yyyyMMdd') AS INT) AS date_key,
    DateValue AS full_date,
    YEAR(DateValue) AS [year],
    DATEPART(QUARTER, DateValue) AS [quarter],
    MONTH(DateValue) AS [month],
    CASE MONTH(DateValue)
        WHEN 1 THEN N'一月' WHEN 2 THEN N'二月' WHEN 3 THEN N'三月'
        WHEN 4 THEN N'四月' WHEN 5 THEN N'五月' WHEN 6 THEN N'六月'
        WHEN 7 THEN N'七月' WHEN 8 THEN N'八月' WHEN 9 THEN N'九月'
        WHEN 10 THEN N'十月' WHEN 11 THEN N'十一月' WHEN 12 THEN N'十二月'
    END AS month_name,
    DATEPART(WEEK, DateValue) AS week_of_year,
    DAY(DateValue) AS day_of_month,
    DATEPART(WEEKDAY, DateValue) AS day_of_week,
    CASE DATEPART(WEEKDAY, DateValue)
        WHEN 1 THEN N'星期日' WHEN 2 THEN N'星期一' WHEN 3 THEN N'星期二'
        WHEN 4 THEN N'星期三' WHEN 5 THEN N'星期四' WHEN 6 THEN N'星期五'
        WHEN 7 THEN N'星期六'
    END AS day_name,
    CASE WHEN DATEPART(WEEKDAY, DateValue) IN (1, 7) THEN 1 ELSE 0 END AS is_weekend,
    0 AS is_holiday,
    YEAR(DateValue) AS fiscal_year,
    DATEPART(QUARTER, DateValue) AS fiscal_quarter
FROM DateCTE
OPTION (MAXRECURSION 1200);
GO

PRINT 'Date dimension generated.';
GO

-- ==========================================
-- 2. 生成渠道维度数据 (10条)
-- ==========================================
INSERT INTO dim_channel (channel_id, channel_name, channel_type, platform, [status]) VALUES
('CHN001', N'天猫旗舰店', 'ONLINE', N'天猫', 'ACTIVE'),
('CHN002', N'京东自营店', 'ONLINE', N'京东', 'ACTIVE'),
('CHN003', N'拼多多官方店', 'ONLINE', N'拼多多', 'ACTIVE'),
('CHN004', N'抖音小店', 'ONLINE', N'抖音', 'ACTIVE'),
('CHN005', N'小红书店铺', 'ONLINE', N'小红书', 'ACTIVE'),
('CHN006', N'官方商城', 'ONLINE', N'自有平台', 'ACTIVE'),
('CHN007', N'线下直营', 'OFFLINE', N'线下门店', 'ACTIVE'),
('CHN008', N'线下加盟', 'OFFLINE', N'线下门店', 'ACTIVE'),
('CHN009', N'企业团购', 'OFFLINE', N'B2B', 'ACTIVE'),
('CHN010', N'微信小程序', 'ONLINE', N'微信', 'ACTIVE');
GO

-- ==========================================
-- 3. 生成促销活动维度数据 (30条)
-- ==========================================
INSERT INTO dim_promotion (promotion_id, promotion_name, promotion_type, discount_rate, start_date, end_date, [status]) VALUES
('PRM001', N'无促销', NULL, 1.00, '2022-01-01', '2099-12-31', 'ACTIVE'),
('PRM002', N'新年特惠-全场9折', N'折扣', 0.90, '2024-01-01', '2024-01-15', 'ACTIVE'),
('PRM003', N'春节满300减50', N'满减', NULL, '2024-02-01', '2024-02-15', 'ACTIVE'),
('PRM004', N'38女神节8折', N'折扣', 0.80, '2024-03-01', '2024-03-08', 'ACTIVE'),
('PRM005', N'五一劳动节满500减100', N'满减', NULL, '2024-05-01', '2024-05-05', 'ACTIVE'),
('PRM006', N'618年中大促', N'折扣', 0.75, '2024-06-01', '2024-06-18', 'ACTIVE'),
('PRM007', N'暑期清凉价', N'折扣', 0.85, '2024-07-01', '2024-08-31', 'ACTIVE'),
('PRM008', N'中秋好礼', N'赠品', NULL, '2024-09-10', '2024-09-17', 'ACTIVE'),
('PRM009', N'国庆7天乐', N'折扣', 0.80, '2024-10-01', '2024-10-07', 'ACTIVE'),
('PRM010', N'双11狂欢节', N'满减', NULL, '2024-11-01', '2024-11-11', 'ACTIVE'),
('PRM011', N'双12年终盛典', N'折扣', 0.70, '2024-12-01', '2024-12-12', 'ACTIVE'),
('PRM012', N'会员专享日', N'折扣', 0.88, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM013', N'新人首单立减20', N'优惠券', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM014', N'满199减30', N'满减', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM015', N'满399减60', N'满减', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM016', N'2023新年特惠', N'折扣', 0.90, '2023-01-01', '2023-01-15', 'ACTIVE'),
('PRM017', N'2023春节', N'满减', NULL, '2023-02-01', '2023-02-15', 'ACTIVE'),
('PRM018', N'2023双11', N'折扣', 0.75, '2023-11-01', '2023-11-11', 'ACTIVE'),
('PRM019', N'2022双11', N'折扣', 0.80, '2022-11-01', '2022-11-11', 'ACTIVE'),
('PRM020', N'2022双12', N'满减', NULL, '2022-12-01', '2022-12-12', 'ACTIVE'),
('PRM021', N'品牌日-数码专场', N'折扣', 0.85, '2024-03-15', '2024-03-17', 'ACTIVE'),
('PRM022', N'品牌日-服装专场', N'折扣', 0.80, '2024-04-15', '2024-04-17', 'ACTIVE'),
('PRM023', N'品牌日-美妆专场', N'折扣', 0.75, '2024-05-15', '2024-05-17', 'ACTIVE'),
('PRM024', N'限时秒杀', N'折扣', 0.50, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM025', N'老客回馈', N'优惠券', NULL, '2024-06-01', '2024-06-30', 'ACTIVE'),
('PRM026', N'积分兑换', N'优惠券', NULL, '2024-01-01', '2024-12-31', 'ACTIVE'),
('PRM027', N'买一送一', N'赠品', NULL, '2024-07-15', '2024-07-20', 'ACTIVE'),
('PRM028', N'满额赠礼', N'赠品', NULL, '2024-08-01', '2024-08-15', 'ACTIVE'),
('PRM029', N'学生专享', N'折扣', 0.90, '2024-09-01', '2024-09-30', 'ACTIVE'),
('PRM030', N'企业采购优惠', N'折扣', 0.85, '2024-01-01', '2024-12-31', 'ACTIVE');
GO

-- ==========================================
-- 4. 生成门店维度数据 (50条)
-- ==========================================
DECLARE @i INT = 1;
DECLARE @provinces TABLE (idx INT, name NVARCHAR(50));
DECLARE @cities TABLE (idx INT, name NVARCHAR(50));
DECLARE @districts TABLE (idx INT, name NVARCHAR(50));

INSERT INTO @provinces VALUES (1, N'浙江省'), (2, N'江苏省'), (3, N'广东省'), (4, N'上海市'), (5, N'北京市');
INSERT INTO @cities VALUES (1, N'杭州市'), (2, N'南京市'), (3, N'广州市'), (4, N'上海市'), (5, N'北京市');
INSERT INTO @districts VALUES (1, N'西湖区'), (2, N'玄武区'), (3, N'天河区'), (4, N'浦东新区'), (5, N'朝阳区');

WHILE @i <= 50
BEGIN
    DECLARE @idx INT = ((@i - 1) % 5) + 1;
    DECLARE @province NVARCHAR(50) = (SELECT name FROM @provinces WHERE idx = @idx);
    DECLARE @city NVARCHAR(50) = (SELECT name FROM @cities WHERE idx = @idx);
    DECLARE @district NVARCHAR(50) = (SELECT name FROM @districts WHERE idx = @idx);

    INSERT INTO dim_store (store_id, store_name, store_type, province, city, district, [address], manager_name, open_date, area_sqm, [status])
    VALUES (
        'STR' + RIGHT('00000' + CAST(@i AS VARCHAR), 5),
        @city + @district + N'店' + CAST(@i AS NVARCHAR),
        CASE WHEN @i <= 30 THEN N'直营' ELSE N'加盟' END,
        @province,
        @city,
        @district,
        @district + N'某路' + CAST(@i AS NVARCHAR) + N'号',
        N'店长' + CAST(@i AS NVARCHAR),
        DATEADD(DAY, ABS(CHECKSUM(NEWID())) % 1000, '2020-01-01'),
        200 + ABS(CHECKSUM(NEWID())) % 800,
        'ACTIVE'
    );
    SET @i = @i + 1;
END
GO

PRINT 'Store dimension generated.';
GO

-- ==========================================
-- 5. 生成商品维度数据 (500条)
-- ==========================================
DECLARE @j INT = 1;
DECLARE @brands TABLE (idx INT, name NVARCHAR(50));
INSERT INTO @brands VALUES (1, N'Apple'), (2, N'华为'), (3, N'小米'), (4, N'三星'), (5, N'OPPO'),
                           (6, N'vivo'), (7, N'联想'), (8, N'戴尔'), (9, N'惠普'), (10, N'Nike'),
                           (11, N'Adidas'), (12, N'优衣库'), (13, N'海尔'), (14, N'美的'), (15, N'格力');

WHILE @j <= 500
BEGIN
    DECLARE @brand_idx INT = ((@j - 1) % 15) + 1;
    DECLARE @brand NVARCHAR(50) = (SELECT name FROM @brands WHERE idx = @brand_idx);
    DECLARE @cat_id VARCHAR(32);
    DECLARE @cat_name NVARCHAR(100);
    DECLARE @sub_cat_id VARCHAR(32);
    DECLARE @sub_cat_name NVARCHAR(100);
    DECLARE @price DECIMAL(18,2) = 50 + ABS(CHECKSUM(NEWID())) % 9950;

    IF @j <= 100
    BEGIN
        SET @cat_id = 'CAT001'; SET @cat_name = N'数码电器';
        IF @j <= 40 BEGIN SET @sub_cat_id = 'CAT001001'; SET @sub_cat_name = N'手机通讯'; END
        ELSE IF @j <= 70 BEGIN SET @sub_cat_id = 'CAT001002'; SET @sub_cat_name = N'电脑办公'; END
        ELSE BEGIN SET @sub_cat_id = 'CAT001003'; SET @sub_cat_name = N'家用电器'; END
    END
    ELSE IF @j <= 200
    BEGIN
        SET @cat_id = 'CAT002'; SET @cat_name = N'服装配饰';
        IF @j <= 140 BEGIN SET @sub_cat_id = 'CAT002001'; SET @sub_cat_name = N'男装'; END
        ELSE IF @j <= 180 BEGIN SET @sub_cat_id = 'CAT002002'; SET @sub_cat_name = N'女装'; END
        ELSE BEGIN SET @sub_cat_id = 'CAT002003'; SET @sub_cat_name = N'鞋靴'; END
    END
    ELSE IF @j <= 300
    BEGIN SET @cat_id = 'CAT003'; SET @cat_name = N'食品饮料'; SET @sub_cat_id = 'CAT003001'; SET @sub_cat_name = N'休闲零食'; END
    ELSE IF @j <= 400
    BEGIN SET @cat_id = 'CAT004'; SET @cat_name = N'家居日用'; SET @sub_cat_id = 'CAT004001'; SET @sub_cat_name = N'家具'; END
    ELSE
    BEGIN SET @cat_id = 'CAT005'; SET @cat_name = N'美妆个护'; SET @sub_cat_id = 'CAT005001'; SET @sub_cat_name = N'护肤'; END

    INSERT INTO dim_product (product_id, product_name, category_id, category_name, sub_category_id, sub_category_name, brand, unit_price, unit_cost, [status])
    VALUES (
        'PRD' + RIGHT('000000' + CAST(@j AS VARCHAR), 6),
        @brand + N' ' + @sub_cat_name + N'商品' + CAST(@j AS NVARCHAR),
        @cat_id,
        @cat_name,
        @sub_cat_id,
        @sub_cat_name,
        @brand,
        @price,
        @price * (0.4 + (ABS(CHECKSUM(NEWID())) % 30) / 100.0),
        'ACTIVE'
    );
    SET @j = @j + 1;
END
GO

PRINT 'Product dimension generated.';
GO

-- ==========================================
-- 6. 生成客户维度数据 (1000条)
-- ==========================================
DECLARE @k INT = 1;
DECLARE @c_provinces TABLE (idx INT, name NVARCHAR(50));
DECLARE @c_cities TABLE (idx INT, name NVARCHAR(50));
DECLARE @c_districts TABLE (idx INT, name NVARCHAR(50));

INSERT INTO @c_provinces VALUES (1, N'浙江省'), (2, N'江苏省'), (3, N'广东省'), (4, N'上海市'), (5, N'北京市'), (6, N'四川省');
INSERT INTO @c_cities VALUES (1, N'杭州市'), (2, N'南京市'), (3, N'广州市'), (4, N'上海市'), (5, N'北京市'), (6, N'成都市');
INSERT INTO @c_districts VALUES (1, N'西湖区'), (2, N'玄武区'), (3, N'天河区'), (4, N'浦东新区'), (5, N'朝阳区'), (6, N'武侯区');

WHILE @k <= 1000
BEGIN
    DECLARE @c_idx INT = ((@k - 1) % 6) + 1;
    DECLARE @c_province NVARCHAR(50) = (SELECT name FROM @c_provinces WHERE idx = @c_idx);
    DECLARE @c_city NVARCHAR(50) = (SELECT name FROM @c_cities WHERE idx = @c_idx);
    DECLARE @c_district NVARCHAR(50) = (SELECT name FROM @c_districts WHERE idx = @c_idx);
    DECLARE @c_type VARCHAR(20);
    DECLARE @gender NVARCHAR(10);
    DECLARE @age_group VARCHAR(20);
    DECLARE @member_level VARCHAR(20);
    DECLARE @rand1 FLOAT = RAND(CHECKSUM(NEWID()));
    DECLARE @rand2 FLOAT = RAND(CHECKSUM(NEWID()));
    DECLARE @rand3 INT = ABS(CHECKSUM(NEWID())) % 5;
    DECLARE @rand4 INT = ABS(CHECKSUM(NEWID())) % 5;

    IF @k <= 100 SET @c_type = 'VIP';
    ELSE IF @k <= 400 SET @c_type = 'NORMAL';
    ELSE SET @c_type = 'NEW';

    IF @rand1 < 0.48 SET @gender = N'男';
    ELSE IF @rand1 < 0.96 SET @gender = N'女';
    ELSE SET @gender = N'未知';

    SET @age_group = CASE @rand3 WHEN 0 THEN '18-24' WHEN 1 THEN '25-34' WHEN 2 THEN '35-44' WHEN 3 THEN '45-54' ELSE '55+' END;
    SET @member_level = CASE @rand4 WHEN 0 THEN 'BRONZE' WHEN 1 THEN 'SILVER' WHEN 2 THEN 'GOLD' WHEN 3 THEN 'PLATINUM' ELSE 'DIAMOND' END;

    INSERT INTO dim_customer (customer_id, customer_name, customer_type, gender, age_group, province, city, district, register_date, member_level, [status])
    VALUES (
        'CUS' + RIGHT('000000' + CAST(@k AS VARCHAR), 6),
        N'客户' + CAST(@k AS NVARCHAR),
        @c_type,
        @gender,
        @age_group,
        @c_province,
        @c_city,
        @c_district,
        DATEADD(DAY, ABS(CHECKSUM(NEWID())) % 1500, '2020-01-01'),
        @member_level,
        'ACTIVE'
    );
    SET @k = @k + 1;
END
GO

PRINT 'Customer dimension generated.';
GO

-- ==========================================
-- 7. 生成订单事实数据 (2000订单 - SQL Server 简化版)
-- ==========================================
DECLARE @o INT = 1;
DECLARE @rand_date DATE;
DECLARE @date_key INT;
DECLARE @order_id VARCHAR(32);
DECLARE @customer_key INT;
DECLARE @store_key INT;
DECLARE @channel_key INT;
DECLARE @promotion_key INT;
DECLARE @order_status VARCHAR(20);
DECLARE @payment_status VARCHAR(20);
DECLARE @pay_method VARCHAR(50);
DECLARE @order_time DATETIME;
DECLARE @line_count INT;
DECLARE @total_quantity INT;
DECLARE @total_amount DECIMAL(18,2);
DECLARE @discount_amount DECIMAL(18,2);
DECLARE @freight_amount DECIMAL(18,2);
DECLARE @l INT;
DECLARE @product_key INT;
DECLARE @quantity INT;
DECLARE @unit_price DECIMAL(18,2);
DECLARE @unit_cost DECIMAL(18,2);
DECLARE @line_amount DECIMAL(18,2);
DECLARE @status_rand INT;

WHILE @o <= 2000
BEGIN
    SET @rand_date = DATEADD(DAY, ABS(CHECKSUM(NEWID())) % 1095, '2022-01-01');
    SET @date_key = CAST(FORMAT(@rand_date, 'yyyyMMdd') AS INT);
    SET @order_time = DATEADD(SECOND, ABS(CHECKSUM(NEWID())) % 86400, CAST(@rand_date AS DATETIME));

    SET @order_id = 'ORD' + FORMAT(@rand_date, 'yyyyMMdd') + RIGHT('000000' + CAST(@o AS VARCHAR), 6);
    SET @customer_key = 1 + ABS(CHECKSUM(NEWID())) % 1000;
    SET @store_key = 1 + ABS(CHECKSUM(NEWID())) % 50;
    SET @channel_key = 1 + ABS(CHECKSUM(NEWID())) % 10;
    SET @promotion_key = CASE WHEN RAND(CHECKSUM(NEWID())) < 0.3 THEN 1 + ABS(CHECKSUM(NEWID())) % 30 ELSE 1 END;

    SET @status_rand = ABS(CHECKSUM(NEWID())) % 10;
    IF @status_rand = 0 BEGIN SET @order_status = 'PENDING'; SET @payment_status = 'UNPAID'; END
    ELSE IF @status_rand = 1 BEGIN SET @order_status = 'CANCELLED'; SET @payment_status = 'UNPAID'; END
    ELSE IF @status_rand = 2 BEGIN SET @order_status = 'PAID'; SET @payment_status = 'PAID'; END
    ELSE IF @status_rand = 3 BEGIN SET @order_status = 'SHIPPED'; SET @payment_status = 'PAID'; END
    ELSE BEGIN SET @order_status = 'COMPLETED'; SET @payment_status = 'PAID'; END

    SET @pay_method = CASE ABS(CHECKSUM(NEWID())) % 4 WHEN 0 THEN 'ALIPAY' WHEN 1 THEN 'WECHAT' WHEN 2 THEN 'CARD' ELSE 'CASH' END;

    SET @line_count = 1 + ABS(CHECKSUM(NEWID())) % 5;
    SET @total_quantity = 0;
    SET @total_amount = 0;

    SET @l = 1;
    WHILE @l <= @line_count
    BEGIN
        SET @product_key = 1 + ABS(CHECKSUM(NEWID())) % 500;
        SET @quantity = 1 + ABS(CHECKSUM(NEWID())) % 3;

        SELECT @unit_price = unit_price, @unit_cost = ISNULL(unit_cost, unit_price * 0.6)
        FROM dim_product WHERE product_key = @product_key;

        IF @unit_price IS NULL
        BEGIN
            SET @unit_price = 100 + ABS(CHECKSUM(NEWID())) % 900;
            SET @unit_cost = @unit_price * 0.6;
        END

        SET @line_amount = @quantity * @unit_price;
        SET @total_quantity = @total_quantity + @quantity;
        SET @total_amount = @total_amount + @line_amount;

        INSERT INTO fact_sales (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key, quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, order_status, payment_method)
        VALUES (@order_id, @l, @date_key, @product_key, @customer_key, @store_key, @channel_key, @promotion_key, @quantity, @unit_price, @unit_cost, 0, @line_amount, @quantity * @unit_cost, @line_amount - @quantity * @unit_cost, @order_status, @pay_method);

        SET @l = @l + 1;
    END

    SET @discount_amount = CASE WHEN @promotion_key > 1 THEN @total_amount * (RAND(CHECKSUM(NEWID())) * 0.2) ELSE 0 END;
    SET @freight_amount = CASE WHEN @total_amount > 99 THEN 0 ELSE 10 END;

    INSERT INTO fact_order (order_id, date_key, customer_key, store_key, channel_key, promotion_key, total_quantity, total_amount, discount_amount, freight_amount, pay_amount, order_status, payment_status, order_time)
    VALUES (@order_id, @date_key, @customer_key, @store_key, @channel_key, @promotion_key, @total_quantity, @total_amount, @discount_amount, @freight_amount, @total_amount - @discount_amount + @freight_amount, @order_status, @payment_status, @order_time);

    IF @o % 500 = 0
        PRINT 'Generated ' + CAST(@o AS VARCHAR) + ' orders...';

    SET @o = @o + 1;
END
GO

PRINT 'Order facts generated.';
GO

-- ==========================================
-- 8. 生成支付事实数据
-- ==========================================
INSERT INTO fact_payment (payment_id, order_id, date_key, customer_key, pay_amount, pay_method, pay_channel, pay_status, pay_time)
SELECT
    'PAY' + RIGHT('00000000' + CAST(ROW_NUMBER() OVER (ORDER BY order_key) AS VARCHAR), 8),
    order_id,
    date_key,
    customer_key,
    pay_amount,
    CASE ABS(CHECKSUM(NEWID())) % 4 WHEN 0 THEN 'ALIPAY' WHEN 1 THEN 'WECHAT' WHEN 2 THEN 'CARD' ELSE 'CASH' END,
    CASE ABS(CHECKSUM(NEWID())) % 3 WHEN 0 THEN 'APP' WHEN 1 THEN 'WEB' ELSE 'POS' END,
    CASE payment_status WHEN 'PAID' THEN 'SUCCESS' WHEN 'REFUNDED' THEN 'REFUNDED' ELSE 'FAILED' END,
    DATEADD(SECOND, ABS(CHECKSUM(NEWID())) % 3600, order_time)
FROM fact_order
WHERE payment_status IN ('PAID', 'REFUNDED');
GO

PRINT 'Payment facts generated.';
GO

-- ==========================================
-- 9. 生成退货事实数据 (约5%退货率)
-- ==========================================
INSERT INTO fact_return (return_id, order_id, order_line_no, date_key, product_key, customer_key, store_key, return_quantity, return_amount, return_reason, return_type, return_status, return_time)
SELECT TOP 500
    'RTN' + RIGHT('00000000' + CAST(ROW_NUMBER() OVER (ORDER BY NEWID()) AS VARCHAR), 8),
    order_id,
    order_line_no,
    date_key + ABS(CHECKSUM(NEWID())) % 7,
    product_key,
    customer_key,
    store_key,
    CASE WHEN quantity > 1 THEN 1 + ABS(CHECKSUM(NEWID())) % quantity ELSE 1 END,
    sales_amount * (RAND(CHECKSUM(NEWID()))),
    CASE ABS(CHECKSUM(NEWID())) % 5 WHEN 0 THEN N'质量问题' WHEN 1 THEN N'尺码不合适' WHEN 2 THEN N'不喜欢' WHEN 3 THEN N'发错货' ELSE N'其他原因' END,
    CASE WHEN RAND(CHECKSUM(NEWID())) < 0.3 THEN 'REFUND_ONLY' ELSE 'RETURN_REFUND' END,
    CASE ABS(CHECKSUM(NEWID())) % 4 WHEN 0 THEN 'PENDING' WHEN 1 THEN 'APPROVED' WHEN 2 THEN 'COMPLETED' ELSE 'REJECTED' END,
    DATEADD(DAY, 1 + ABS(CHECKSUM(NEWID())) % 7, CAST(CAST(date_key AS VARCHAR) AS DATE))
FROM fact_sales
WHERE order_status = 'COMPLETED'
ORDER BY NEWID();
GO

PRINT 'Return facts generated.';
GO

-- ==========================================
-- 10. 生成库存快照数据 (简化版)
-- ==========================================
INSERT INTO fact_inventory_snapshot (date_key, product_key, store_key, quantity_on_hand, quantity_reserved, quantity_available, unit_cost, inventory_value)
SELECT TOP 2000
    20240101,
    p.product_key,
    s.store_key,
    50 + ABS(CHECKSUM(NEWID())) % 200 AS qty_on_hand,
    ABS(CHECKSUM(NEWID())) % 20 AS qty_reserved,
    50 + ABS(CHECKSUM(NEWID())) % 200 - ABS(CHECKSUM(NEWID())) % 20 AS qty_available,
    p.unit_cost,
    (50 + ABS(CHECKSUM(NEWID())) % 200) * p.unit_cost AS inv_value
FROM dim_product p
CROSS JOIN dim_store s
WHERE p.[status] = 'ACTIVE' AND s.[status] = 'ACTIVE'
ORDER BY NEWID();
GO

PRINT 'Inventory snapshot generated.';
GO

-- 完成统计
PRINT '===== Test Data Generation Completed =====';

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
GO

-- ============================================
-- 父子维度测试数据 (Parent-Child Dimension)
-- ============================================

-- 11. 团队维度数据（层级结构）
INSERT INTO dim_team (team_id, team_name, parent_id, team_level, manager_name, [status]) VALUES
('T001', N'总公司', NULL, 1, N'张总', 'ACTIVE'),
('T002', N'技术部', 'T001', 2, N'李经理', 'ACTIVE'),
('T003', N'研发组', 'T002', 3, N'王组长', 'ACTIVE'),
('T004', N'测试组', 'T002', 3, N'赵组长', 'ACTIVE'),
('T005', N'销售部', 'T001', 2, N'钱经理', 'ACTIVE'),
('T006', N'前端小组', 'T003', 4, N'孙组长', 'ACTIVE'),
('T007', N'后端小组', 'T003', 4, N'周组长', 'ACTIVE'),
('T008', N'华东区', 'T005', 3, N'吴经理', 'ACTIVE'),
('T009', N'华北区', 'T005', 3, N'郑经理', 'ACTIVE');
GO

-- 12. 团队闭包表数据
INSERT INTO team_closure (parent_id, team_id, distance) VALUES
('T001', 'T001', 0), ('T001', 'T002', 1), ('T001', 'T003', 2), ('T001', 'T004', 2),
('T001', 'T005', 1), ('T001', 'T006', 3), ('T001', 'T007', 3), ('T001', 'T008', 2), ('T001', 'T009', 2),
('T002', 'T002', 0), ('T002', 'T003', 1), ('T002', 'T004', 1), ('T002', 'T006', 2), ('T002', 'T007', 2),
('T003', 'T003', 0), ('T003', 'T006', 1), ('T003', 'T007', 1),
('T004', 'T004', 0),
('T005', 'T005', 0), ('T005', 'T008', 1), ('T005', 'T009', 1),
('T006', 'T006', 0), ('T007', 'T007', 0), ('T008', 'T008', 0), ('T009', 'T009', 0);
GO

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
GO

PRINT 'Parent-Child dimension data generated.';
GO

-- ============================================
-- 嵌套维度测试数据 (Nested Dimension / Snowflake Schema)
-- ============================================

-- 14. 品类组维度数据（三级维度）
INSERT INTO dim_category_group (group_id, group_name, group_type, [status]) VALUES
('GRP001', N'电子产品组', N'高价值', 'ACTIVE'),
('GRP002', N'日用品组', N'快消品', 'ACTIVE'),
('GRP003', N'服装配饰组', N'时尚', 'ACTIVE');
GO

-- 15. 品类维度数据（二级维度）
INSERT INTO dim_category_nested (category_id, category_name, category_level, group_key, [status]) VALUES
('CAT001', N'数码电器', 1, 1, 'ACTIVE'),
('CAT002', N'手机通讯', 2, 1, 'ACTIVE'),
('CAT003', N'电脑办公', 2, 1, 'ACTIVE'),
('CAT004', N'家居用品', 1, 2, 'ACTIVE'),
('CAT005', N'厨房用品', 2, 2, 'ACTIVE'),
('CAT006', N'男装', 1, 3, 'ACTIVE'),
('CAT007', N'女装', 1, 3, 'ACTIVE');
GO

-- 16. 嵌套产品维度数据
INSERT INTO dim_product_nested (product_id, product_name, brand, category_key, unit_price, [status]) VALUES
('NP001', N'iPhone 15 Pro', 'Apple', 2, 8999.00, 'ACTIVE'),
('NP002', N'华为 Mate 60', N'华为', 2, 6999.00, 'ACTIVE'),
('NP003', N'MacBook Pro 14', 'Apple', 3, 14999.00, 'ACTIVE'),
('NP004', N'联想 ThinkPad', N'联想', 3, 7999.00, 'ACTIVE'),
('NP005', N'不锈钢炒锅', N'苏泊尔', 5, 299.00, 'ACTIVE'),
('NP006', N'电饭煲', N'美的', 5, 499.00, 'ACTIVE'),
('NP007', N'休闲T恤', N'优衣库', 6, 99.00, 'ACTIVE'),
('NP008', N'牛仔裤', 'Levis', 6, 599.00, 'ACTIVE'),
('NP009', N'连衣裙', 'ZARA', 7, 399.00, 'ACTIVE'),
('NP010', N'风衣外套', 'MAX&Co.', 7, 1299.00, 'ACTIVE');
GO

-- 17. 区域维度数据
INSERT INTO dim_region_nested (region_id, region_name, province, city, [status]) VALUES
('REG001', N'华东区-杭州', N'浙江省', N'杭州市', 'ACTIVE'),
('REG002', N'华东区-上海', N'上海市', N'上海市', 'ACTIVE'),
('REG003', N'华北区-北京', N'北京市', N'北京市', 'ACTIVE'),
('REG004', N'华南区-广州', N'广东省', N'广州市', 'ACTIVE'),
('REG005', N'华南区-深圳', N'广东省', N'深圳市', 'ACTIVE');
GO

-- 18. 嵌套门店维度数据
INSERT INTO dim_store_nested (store_id, store_name, store_type, region_key, [status]) VALUES
('NS001', N'杭州西湖旗舰店', N'旗舰店', 1, 'ACTIVE'),
('NS002', N'杭州滨江店', N'标准店', 1, 'ACTIVE'),
('NS003', N'上海南京路店', N'旗舰店', 2, 'ACTIVE'),
('NS004', N'北京王府井店', N'旗舰店', 3, 'ACTIVE'),
('NS005', N'广州天河店', N'标准店', 4, 'ACTIVE'),
('NS006', N'深圳华强北店', N'标准店', 5, 'ACTIVE');
GO

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
GO

PRINT 'Nested dimension data generated.';
GO

-- 最终统计
PRINT '===== Parent-Child & Nested Dimension Data =====';

SELECT 'dim_team' AS table_name, COUNT(*) AS row_count FROM dim_team
UNION ALL SELECT 'team_closure', COUNT(*) FROM team_closure
UNION ALL SELECT 'fact_team_sales', COUNT(*) FROM fact_team_sales
UNION ALL SELECT 'dim_category_group', COUNT(*) FROM dim_category_group
UNION ALL SELECT 'dim_category_nested', COUNT(*) FROM dim_category_nested
UNION ALL SELECT 'dim_product_nested', COUNT(*) FROM dim_product_nested
UNION ALL SELECT 'dim_region_nested', COUNT(*) FROM dim_region_nested
UNION ALL SELECT 'dim_store_nested', COUNT(*) FROM dim_store_nested
UNION ALL SELECT 'fact_sales_nested', COUNT(*) FROM fact_sales_nested;
GO
