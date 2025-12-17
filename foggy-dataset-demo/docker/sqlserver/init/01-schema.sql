-- ============================================
-- Foggy Dataset Model SQL Server 测试数据库表结构
-- 文件: 01-schema.sql
-- 说明: 创建维度表、事实表、字典表
-- 注意: SQL Server 容器不支持自动执行，需手动执行
--   docker exec -it foggy-sqlserver-test /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -i /scripts/01-schema.sql
-- ============================================

USE master;
GO

-- 如果数据库存在则删除
IF EXISTS (SELECT name FROM sys.databases WHERE name = 'foggy_test')
BEGIN
    ALTER DATABASE foggy_test SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE foggy_test;
END
GO

-- 创建数据库
CREATE DATABASE foggy_test COLLATE Chinese_PRC_CI_AS;
GO

USE foggy_test;
GO

-- ==========================================
-- 1. 日期维度表
-- ==========================================
CREATE TABLE dim_date (
    date_key        INT NOT NULL,
    full_date       DATE NOT NULL,
    [year]          SMALLINT NOT NULL,
    [quarter]       SMALLINT NOT NULL,
    [month]         SMALLINT NOT NULL,
    month_name      NVARCHAR(20) NOT NULL,
    week_of_year    SMALLINT NOT NULL,
    day_of_month    SMALLINT NOT NULL,
    day_of_week     SMALLINT NOT NULL,
    day_name        NVARCHAR(20) NOT NULL,
    is_weekend      SMALLINT NOT NULL DEFAULT 0,
    is_holiday      SMALLINT NOT NULL DEFAULT 0,
    fiscal_year     SMALLINT,
    fiscal_quarter  SMALLINT,
    CONSTRAINT pk_dim_date PRIMARY KEY (date_key)
);
GO
CREATE INDEX idx_dim_date_full_date ON dim_date (full_date);
CREATE INDEX idx_dim_date_year_month ON dim_date ([year], [month]);
GO

-- ==========================================
-- 2. 商品维度表
-- ==========================================
CREATE TABLE dim_product (
    product_key       INT IDENTITY(1,1),
    product_id        VARCHAR(32) NOT NULL,
    product_name      NVARCHAR(200) NOT NULL,
    category_id       VARCHAR(32),
    category_name     NVARCHAR(100),
    sub_category_id   VARCHAR(32),
    sub_category_name NVARCHAR(100),
    brand             NVARCHAR(100),
    supplier_id       VARCHAR(32),
    unit_price        DECIMAL(18,2) NOT NULL,
    unit_cost         DECIMAL(18,2),
    [status]          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at        DATETIME DEFAULT GETDATE(),
    updated_at        DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_product PRIMARY KEY (product_key),
    CONSTRAINT uk_product_id UNIQUE (product_id)
);
GO
CREATE INDEX idx_dim_product_category ON dim_product (category_id);
CREATE INDEX idx_dim_product_brand ON dim_product (brand);
CREATE INDEX idx_dim_product_status ON dim_product ([status]);
GO

-- ==========================================
-- 3. 客户维度表
-- ==========================================
CREATE TABLE dim_customer (
    customer_key    INT IDENTITY(1,1),
    customer_id     VARCHAR(32) NOT NULL,
    customer_name   NVARCHAR(100) NOT NULL,
    customer_type   VARCHAR(20),
    gender          NVARCHAR(10),
    age_group       VARCHAR(20),
    province        NVARCHAR(50),
    city            NVARCHAR(50),
    district        NVARCHAR(50),
    register_date   DATE,
    member_level    VARCHAR(20),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_customer PRIMARY KEY (customer_key),
    CONSTRAINT uk_customer_id UNIQUE (customer_id)
);
GO
CREATE INDEX idx_dim_customer_province_city ON dim_customer (province, city);
CREATE INDEX idx_dim_customer_member_level ON dim_customer (member_level);
CREATE INDEX idx_dim_customer_customer_type ON dim_customer (customer_type);
GO

-- ==========================================
-- 4. 门店维度表
-- ==========================================
CREATE TABLE dim_store (
    store_key       INT IDENTITY(1,1),
    store_id        VARCHAR(32) NOT NULL,
    store_name      NVARCHAR(100) NOT NULL,
    store_type      NVARCHAR(20),
    province        NVARCHAR(50),
    city            NVARCHAR(50),
    district        NVARCHAR(50),
    [address]       NVARCHAR(500),
    manager_name    NVARCHAR(50),
    open_date       DATE,
    area_sqm        DECIMAL(10,2),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_store PRIMARY KEY (store_key),
    CONSTRAINT uk_store_id UNIQUE (store_id)
);
GO
CREATE INDEX idx_dim_store_province_city ON dim_store (province, city);
CREATE INDEX idx_dim_store_store_type ON dim_store (store_type);
GO

-- ==========================================
-- 5. 销售渠道维度表
-- ==========================================
CREATE TABLE dim_channel (
    channel_key     INT IDENTITY(1,1),
    channel_id      VARCHAR(32) NOT NULL,
    channel_name    NVARCHAR(100) NOT NULL,
    channel_type    VARCHAR(50),
    platform        NVARCHAR(50),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_channel PRIMARY KEY (channel_key),
    CONSTRAINT uk_channel_id UNIQUE (channel_id)
);
GO

-- ==========================================
-- 6. 促销活动维度表
-- ==========================================
CREATE TABLE dim_promotion (
    promotion_key   INT IDENTITY(1,1),
    promotion_id    VARCHAR(32) NOT NULL,
    promotion_name  NVARCHAR(200) NOT NULL,
    promotion_type  NVARCHAR(50),
    discount_rate   DECIMAL(5,2),
    start_date      DATE,
    end_date        DATE,
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_promotion PRIMARY KEY (promotion_key),
    CONSTRAINT uk_promotion_id UNIQUE (promotion_id)
);
GO
CREATE INDEX idx_dim_promotion_date_range ON dim_promotion (start_date, end_date);
GO

-- ==========================================
-- 7. 订单事实表（订单头）
-- ==========================================
CREATE TABLE fact_order (
    order_key       BIGINT IDENTITY(1,1),
    order_id        VARCHAR(32) NOT NULL,
    date_key        INT NOT NULL,
    customer_key    INT,
    store_key       INT,
    channel_key     INT,
    promotion_key   INT,
    total_quantity  INT NOT NULL,
    total_amount    DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) DEFAULT 0,
    freight_amount  DECIMAL(18,2) DEFAULT 0,
    pay_amount      DECIMAL(18,2) NOT NULL,
    order_status    VARCHAR(20) NOT NULL,
    payment_status  VARCHAR(20),
    order_time      DATETIME NOT NULL,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_order PRIMARY KEY (order_key),
    CONSTRAINT uk_order_id UNIQUE (order_id)
);
GO
CREATE INDEX idx_fact_order_date_key ON fact_order (date_key);
CREATE INDEX idx_fact_order_customer_key ON fact_order (customer_key);
CREATE INDEX idx_fact_order_store_key ON fact_order (store_key);
CREATE INDEX idx_fact_order_channel_key ON fact_order (channel_key);
CREATE INDEX idx_fact_order_order_status ON fact_order (order_status);
CREATE INDEX idx_fact_order_order_time ON fact_order (order_time);
GO

-- ==========================================
-- 8. 销售事实表（订单明细）
-- ==========================================
CREATE TABLE fact_sales (
    sales_key       BIGINT IDENTITY(1,1),
    order_id        VARCHAR(32) NOT NULL,
    order_line_no   INT NOT NULL,
    date_key        INT NOT NULL,
    product_key     INT NOT NULL,
    customer_key    INT,
    store_key       INT,
    channel_key     INT,
    promotion_key   INT,
    quantity        INT NOT NULL,
    unit_price      DECIMAL(18,2) NOT NULL,
    unit_cost       DECIMAL(18,2),
    discount_amount DECIMAL(18,2) DEFAULT 0,
    sales_amount    DECIMAL(18,2) NOT NULL,
    cost_amount     DECIMAL(18,2),
    profit_amount   DECIMAL(18,2),
    tax_amount      DECIMAL(18,2) DEFAULT 0,
    order_status    VARCHAR(20),
    payment_method  VARCHAR(50),
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_sales PRIMARY KEY (sales_key),
    CONSTRAINT uk_order_line UNIQUE (order_id, order_line_no)
);
GO
CREATE INDEX idx_fact_sales_date_key ON fact_sales (date_key);
CREATE INDEX idx_fact_sales_product_key ON fact_sales (product_key);
CREATE INDEX idx_fact_sales_customer_key ON fact_sales (customer_key);
CREATE INDEX idx_fact_sales_store_key ON fact_sales (store_key);
CREATE INDEX idx_fact_sales_channel_key ON fact_sales (channel_key);
CREATE INDEX idx_fact_sales_created_at ON fact_sales (created_at);
GO

-- ==========================================
-- 9. 支付事实表
-- ==========================================
CREATE TABLE fact_payment (
    payment_key     BIGINT IDENTITY(1,1),
    payment_id      VARCHAR(32) NOT NULL,
    order_id        VARCHAR(32) NOT NULL,
    date_key        INT NOT NULL,
    customer_key    INT,
    pay_amount      DECIMAL(18,2) NOT NULL,
    pay_method      VARCHAR(50) NOT NULL,
    pay_channel     VARCHAR(50),
    pay_status      VARCHAR(20) NOT NULL,
    pay_time        DATETIME NOT NULL,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_payment PRIMARY KEY (payment_key),
    CONSTRAINT uk_payment_id UNIQUE (payment_id)
);
GO
CREATE INDEX idx_fact_payment_order_id ON fact_payment (order_id);
CREATE INDEX idx_fact_payment_date_key ON fact_payment (date_key);
CREATE INDEX idx_fact_payment_customer_key ON fact_payment (customer_key);
CREATE INDEX idx_fact_payment_pay_status ON fact_payment (pay_status);
CREATE INDEX idx_fact_payment_pay_time ON fact_payment (pay_time);
GO

-- ==========================================
-- 10. 退货事实表
-- ==========================================
CREATE TABLE fact_return (
    return_key      BIGINT IDENTITY(1,1),
    return_id       VARCHAR(32) NOT NULL,
    order_id        VARCHAR(32) NOT NULL,
    order_line_no   INT,
    date_key        INT NOT NULL,
    product_key     INT,
    customer_key    INT,
    store_key       INT,
    return_quantity INT NOT NULL,
    return_amount   DECIMAL(18,2) NOT NULL,
    return_reason   NVARCHAR(200),
    return_type     VARCHAR(20),
    return_status   VARCHAR(20) NOT NULL,
    return_time     DATETIME NOT NULL,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_return PRIMARY KEY (return_key),
    CONSTRAINT uk_return_id UNIQUE (return_id)
);
GO
CREATE INDEX idx_fact_return_order_id ON fact_return (order_id);
CREATE INDEX idx_fact_return_date_key ON fact_return (date_key);
CREATE INDEX idx_fact_return_product_key ON fact_return (product_key);
CREATE INDEX idx_fact_return_customer_key ON fact_return (customer_key);
CREATE INDEX idx_fact_return_return_status ON fact_return (return_status);
CREATE INDEX idx_fact_return_return_time ON fact_return (return_time);
GO

-- ==========================================
-- 11. 库存快照事实表
-- ==========================================
CREATE TABLE fact_inventory_snapshot (
    snapshot_key        BIGINT IDENTITY(1,1),
    date_key            INT NOT NULL,
    product_key         INT NOT NULL,
    store_key           INT NOT NULL,
    quantity_on_hand    INT NOT NULL,
    quantity_reserved   INT DEFAULT 0,
    quantity_available  INT NOT NULL,
    unit_cost           DECIMAL(18,2),
    inventory_value     DECIMAL(18,2),
    created_at          DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_inventory_snapshot PRIMARY KEY (snapshot_key),
    CONSTRAINT uk_snapshot UNIQUE (date_key, product_key, store_key)
);
GO
CREATE INDEX idx_fact_inventory_date_key ON fact_inventory_snapshot (date_key);
CREATE INDEX idx_fact_inventory_product_key ON fact_inventory_snapshot (product_key);
CREATE INDEX idx_fact_inventory_store_key ON fact_inventory_snapshot (store_key);
GO

-- ==========================================
-- 12. 地区字典表（层级结构）
-- ==========================================
CREATE TABLE dict_region (
    region_id       VARCHAR(32) NOT NULL,
    region_name     NVARCHAR(100) NOT NULL,
    parent_id       VARCHAR(32),
    region_level    SMALLINT NOT NULL,
    region_code     VARCHAR(20),
    sort_order      INT DEFAULT 0,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dict_region PRIMARY KEY (region_id)
);
GO
CREATE INDEX idx_dict_region_parent_id ON dict_region (parent_id);
CREATE INDEX idx_dict_region_region_level ON dict_region (region_level);
GO

-- ==========================================
-- 13. 商品品类字典表（层级结构）
-- ==========================================
CREATE TABLE dict_category (
    category_id     VARCHAR(32) NOT NULL,
    category_name   NVARCHAR(100) NOT NULL,
    parent_id       VARCHAR(32),
    category_level  SMALLINT NOT NULL,
    sort_order      INT DEFAULT 0,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dict_category PRIMARY KEY (category_id)
);
GO
CREATE INDEX idx_dict_category_parent_id ON dict_category (parent_id);
CREATE INDEX idx_dict_category_category_level ON dict_category (category_level);
GO

-- ==========================================
-- 14. 通用状态字典表
-- ==========================================
CREATE TABLE dict_status (
    status_type     VARCHAR(50) NOT NULL,
    status_code     VARCHAR(50) NOT NULL,
    status_name     NVARCHAR(100) NOT NULL,
    sort_order      INT DEFAULT 0,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dict_status PRIMARY KEY (status_type, status_code)
);
GO

-- ==========================================
-- 父子维度测试表 (Parent-Child Dimension)
-- ==========================================

-- 15. 团队维度表（支持父子层级结构）
CREATE TABLE dim_team (
    team_id         VARCHAR(32) NOT NULL,
    team_name       NVARCHAR(100) NOT NULL,
    parent_id       VARCHAR(32),
    team_level      INT NOT NULL DEFAULT 1,
    manager_name    NVARCHAR(50),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_team PRIMARY KEY (team_id)
);
GO
CREATE INDEX idx_dim_team_parent_id ON dim_team (parent_id);
GO

-- 16. 团队闭包表（存储层级关系）
CREATE TABLE team_closure (
    parent_id       VARCHAR(32) NOT NULL,
    team_id         VARCHAR(32) NOT NULL,
    distance        INT DEFAULT 0,
    CONSTRAINT pk_team_closure PRIMARY KEY (parent_id, team_id)
);
GO
CREATE INDEX idx_team_closure_parent_id ON team_closure (parent_id);
CREATE INDEX idx_team_closure_team_id ON team_closure (team_id);
GO

-- 17. 团队销售事实表
CREATE TABLE fact_team_sales (
    sales_id        BIGINT IDENTITY(1,1),
    team_id         VARCHAR(32) NOT NULL,
    date_key        INT NOT NULL,
    sales_amount    DECIMAL(18,2) NOT NULL,
    sales_count     INT NOT NULL DEFAULT 1,
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_team_sales PRIMARY KEY (sales_id)
);
GO
CREATE INDEX idx_fact_team_sales_team_id ON fact_team_sales (team_id);
CREATE INDEX idx_fact_team_sales_date_key ON fact_team_sales (date_key);
GO

-- ==========================================
-- 嵌套维度测试表 (Nested Dimension / Snowflake Schema)
-- ==========================================

-- 18. 品类组维度表（三级维度）
CREATE TABLE dim_category_group (
    group_key       INT IDENTITY(1,1),
    group_id        VARCHAR(32) NOT NULL,
    group_name      NVARCHAR(100) NOT NULL,
    group_type      NVARCHAR(50),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_category_group PRIMARY KEY (group_key),
    CONSTRAINT uk_group_id UNIQUE (group_id)
);
GO

-- 19. 品类维度表（二级维度，关联品类组）
CREATE TABLE dim_category_nested (
    category_key    INT IDENTITY(1,1),
    category_id     VARCHAR(32) NOT NULL,
    category_name   NVARCHAR(100) NOT NULL,
    category_level  INT DEFAULT 1,
    group_key       INT,
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_category_nested PRIMARY KEY (category_key),
    CONSTRAINT uk_category_nested_id UNIQUE (category_id)
);
GO
CREATE INDEX idx_dim_category_nested_group_key ON dim_category_nested (group_key);
GO

-- 20. 嵌套产品维度表（一级维度，关联品类）
CREATE TABLE dim_product_nested (
    product_key     INT IDENTITY(1,1),
    product_id      VARCHAR(32) NOT NULL,
    product_name    NVARCHAR(200) NOT NULL,
    brand           NVARCHAR(100),
    category_key    INT,
    unit_price      DECIMAL(18,2) NOT NULL,
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_product_nested PRIMARY KEY (product_key),
    CONSTRAINT uk_product_nested_id UNIQUE (product_id)
);
GO
CREATE INDEX idx_dim_product_nested_category_key ON dim_product_nested (category_key);
GO

-- 21. 区域维度表（二级维度）
CREATE TABLE dim_region_nested (
    region_key      INT IDENTITY(1,1),
    region_id       VARCHAR(32) NOT NULL,
    region_name     NVARCHAR(100) NOT NULL,
    province        NVARCHAR(50),
    city            NVARCHAR(50),
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_region_nested PRIMARY KEY (region_key),
    CONSTRAINT uk_region_nested_id UNIQUE (region_id)
);
GO

-- 22. 嵌套门店维度表（一级维度，关联区域）
CREATE TABLE dim_store_nested (
    store_key       INT IDENTITY(1,1),
    store_id        VARCHAR(32) NOT NULL,
    store_name      NVARCHAR(100) NOT NULL,
    store_type      NVARCHAR(20),
    region_key      INT,
    [status]        VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_dim_store_nested PRIMARY KEY (store_key),
    CONSTRAINT uk_store_nested_id UNIQUE (store_id)
);
GO
CREATE INDEX idx_dim_store_nested_region_key ON dim_store_nested (region_key);
GO

-- 23. 嵌套维度销售事实表
CREATE TABLE fact_sales_nested (
    sales_key       BIGINT IDENTITY(1,1),
    date_key        INT NOT NULL,
    product_key     INT NOT NULL,
    store_key       INT,
    quantity        INT NOT NULL,
    sales_amount    DECIMAL(18,2) NOT NULL,
    cost_amount     DECIMAL(18,2),
    created_at      DATETIME DEFAULT GETDATE(),
    CONSTRAINT pk_fact_sales_nested PRIMARY KEY (sales_key)
);
GO
CREATE INDEX idx_fact_sales_nested_date_key ON fact_sales_nested (date_key);
CREATE INDEX idx_fact_sales_nested_product_key ON fact_sales_nested (product_key);
CREATE INDEX idx_fact_sales_nested_store_key ON fact_sales_nested (store_key);
GO

PRINT 'Schema created successfully!';
GO
