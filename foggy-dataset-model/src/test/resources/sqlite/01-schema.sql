-- ============================================
-- Foggy Dataset Model SQLite 测试数据库表结构
-- 文件: 01-schema.sql
-- ============================================

-- 1. 日期维度表
DROP TABLE IF EXISTS dim_date;
CREATE TABLE dim_date (
    date_key        INTEGER NOT NULL PRIMARY KEY,
    full_date       TEXT NOT NULL,
    year            INTEGER NOT NULL,
    quarter         INTEGER NOT NULL,
    month           INTEGER NOT NULL,
    month_name      TEXT NOT NULL,
    week_of_year    INTEGER NOT NULL,
    day_of_month    INTEGER NOT NULL,
    day_of_week     INTEGER NOT NULL,
    day_name        TEXT NOT NULL,
    is_weekend      INTEGER NOT NULL DEFAULT 0,
    is_holiday      INTEGER NOT NULL DEFAULT 0,
    fiscal_year     INTEGER,
    fiscal_quarter  INTEGER
);
CREATE INDEX idx_dim_date_full_date ON dim_date (full_date);
CREATE INDEX idx_dim_date_year_month ON dim_date (year, month);

-- 2. 商品维度表
DROP TABLE IF EXISTS dim_product;
CREATE TABLE dim_product (
    product_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id        TEXT NOT NULL UNIQUE,
    product_name      TEXT NOT NULL,
    category_id       TEXT,
    category_name     TEXT,
    sub_category_id   TEXT,
    sub_category_name TEXT,
    brand             TEXT,
    supplier_id       TEXT,
    unit_price        REAL NOT NULL,
    unit_cost         REAL,
    status            TEXT DEFAULT 'ACTIVE',
    created_at        TEXT DEFAULT (datetime('now','localtime')),
    updated_at        TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_product_category ON dim_product (category_id);
CREATE INDEX idx_dim_product_brand ON dim_product (brand);
CREATE INDEX idx_dim_product_status ON dim_product (status);

-- 3. 客户维度表
DROP TABLE IF EXISTS dim_customer;
CREATE TABLE dim_customer (
    customer_key    INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id     TEXT NOT NULL UNIQUE,
    customer_name   TEXT NOT NULL,
    customer_type   TEXT,
    gender          TEXT,
    age_group       TEXT,
    province        TEXT,
    city            TEXT,
    district        TEXT,
    register_date   TEXT,
    member_level    TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_customer_province_city ON dim_customer (province, city);
CREATE INDEX idx_dim_customer_member_level ON dim_customer (member_level);

-- 4. 门店维度表
DROP TABLE IF EXISTS dim_store;
CREATE TABLE dim_store (
    store_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    store_id        TEXT NOT NULL UNIQUE,
    store_name      TEXT NOT NULL,
    store_type      TEXT,
    province        TEXT,
    city            TEXT,
    district        TEXT,
    address         TEXT,
    manager_name    TEXT,
    open_date       TEXT,
    area_sqm        REAL,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_store_province_city ON dim_store (province, city);

-- 5. 销售渠道维度表
DROP TABLE IF EXISTS dim_channel;
CREATE TABLE dim_channel (
    channel_key     INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id      TEXT NOT NULL UNIQUE,
    channel_name    TEXT NOT NULL,
    channel_type    TEXT,
    platform        TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);

-- 6. 促销活动维度表
DROP TABLE IF EXISTS dim_promotion;
CREATE TABLE dim_promotion (
    promotion_key   INTEGER PRIMARY KEY AUTOINCREMENT,
    promotion_id    TEXT NOT NULL UNIQUE,
    promotion_name  TEXT NOT NULL,
    promotion_type  TEXT,
    discount_rate   REAL,
    start_date      TEXT,
    end_date        TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_promotion_date_range ON dim_promotion (start_date, end_date);

-- 7. 订单事实表
DROP TABLE IF EXISTS fact_order;
CREATE TABLE fact_order (
    order_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id        TEXT NOT NULL UNIQUE,
    date_key        INTEGER NOT NULL,
    customer_key    INTEGER,
    store_key       INTEGER,
    channel_key     INTEGER,
    promotion_key   INTEGER,
    total_quantity  INTEGER NOT NULL,
    total_amount    REAL NOT NULL,
    discount_amount REAL DEFAULT 0,
    freight_amount  REAL DEFAULT 0,
    pay_amount      REAL NOT NULL,
    order_status    TEXT NOT NULL,
    payment_status  TEXT,
    order_time      TEXT NOT NULL,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_fact_order_date_key ON fact_order (date_key);
CREATE INDEX idx_fact_order_customer_key ON fact_order (customer_key);
CREATE INDEX idx_fact_order_order_status ON fact_order (order_status);

-- 8. 销售事实表
DROP TABLE IF EXISTS fact_sales;
CREATE TABLE fact_sales (
    sales_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id        TEXT NOT NULL,
    order_line_no   INTEGER NOT NULL,
    date_key        INTEGER NOT NULL,
    product_key     INTEGER NOT NULL,
    customer_key    INTEGER,
    store_key       INTEGER,
    channel_key     INTEGER,
    promotion_key   INTEGER,
    quantity        INTEGER NOT NULL,
    unit_price      REAL NOT NULL,
    unit_cost       REAL,
    discount_amount REAL DEFAULT 0,
    sales_amount    REAL NOT NULL,
    cost_amount     REAL,
    profit_amount   REAL,
    tax_amount      REAL DEFAULT 0,
    order_status    TEXT,
    payment_method  TEXT,
    created_at      TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (order_id, order_line_no)
);
CREATE INDEX idx_fact_sales_date_key ON fact_sales (date_key);
CREATE INDEX idx_fact_sales_product_key ON fact_sales (product_key);

-- 9. 支付事实表
DROP TABLE IF EXISTS fact_payment;
CREATE TABLE fact_payment (
    payment_key     INTEGER PRIMARY KEY AUTOINCREMENT,
    payment_id      TEXT NOT NULL UNIQUE,
    order_id        TEXT NOT NULL,
    date_key        INTEGER NOT NULL,
    customer_key    INTEGER,
    pay_amount      REAL NOT NULL,
    pay_method      TEXT NOT NULL,
    pay_channel     TEXT,
    pay_status      TEXT NOT NULL,
    pay_time        TEXT NOT NULL,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_fact_payment_order_id ON fact_payment (order_id);

-- 10. 退货事实表
DROP TABLE IF EXISTS fact_return;
CREATE TABLE fact_return (
    return_key      INTEGER PRIMARY KEY AUTOINCREMENT,
    return_id       TEXT NOT NULL UNIQUE,
    order_id        TEXT NOT NULL,
    order_line_no   INTEGER,
    date_key        INTEGER NOT NULL,
    product_key     INTEGER,
    customer_key    INTEGER,
    store_key       INTEGER,
    return_quantity INTEGER NOT NULL,
    return_amount   REAL NOT NULL,
    return_reason   TEXT,
    return_type     TEXT,
    return_status   TEXT NOT NULL,
    return_time     TEXT NOT NULL,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_fact_return_order_id ON fact_return (order_id);

-- 11. 库存快照事实表
DROP TABLE IF EXISTS fact_inventory_snapshot;
CREATE TABLE fact_inventory_snapshot (
    snapshot_key        INTEGER PRIMARY KEY AUTOINCREMENT,
    date_key            INTEGER NOT NULL,
    product_key         INTEGER NOT NULL,
    store_key           INTEGER NOT NULL,
    quantity_on_hand    INTEGER NOT NULL,
    quantity_reserved   INTEGER DEFAULT 0,
    quantity_available  INTEGER NOT NULL,
    unit_cost           REAL,
    inventory_value     REAL,
    created_at          TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (date_key, product_key, store_key)
);

-- 12. 地区字典表
DROP TABLE IF EXISTS dict_region;
CREATE TABLE dict_region (
    region_id       TEXT NOT NULL PRIMARY KEY,
    region_name     TEXT NOT NULL,
    parent_id       TEXT,
    region_level    INTEGER NOT NULL,
    region_code     TEXT,
    sort_order      INTEGER DEFAULT 0,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dict_region_parent_id ON dict_region (parent_id);

-- 13. 商品品类字典表
DROP TABLE IF EXISTS dict_category;
CREATE TABLE dict_category (
    category_id     TEXT NOT NULL PRIMARY KEY,
    category_name   TEXT NOT NULL,
    parent_id       TEXT,
    category_level  INTEGER NOT NULL,
    sort_order      INTEGER DEFAULT 0,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dict_category_parent_id ON dict_category (parent_id);

-- 14. 通用状态字典表
DROP TABLE IF EXISTS dict_status;
CREATE TABLE dict_status (
    status_type     TEXT NOT NULL,
    status_code     TEXT NOT NULL,
    status_name     TEXT NOT NULL,
    sort_order      INTEGER DEFAULT 0,
    created_at      TEXT DEFAULT (datetime('now','localtime')),
    PRIMARY KEY (status_type, status_code)
);

-- ============================================
-- 父子维度测试表 (Parent-Child Dimension)
-- ============================================

-- 15. 团队维度表（支持父子层级结构）
DROP TABLE IF EXISTS dim_team;
CREATE TABLE dim_team (
    team_id         TEXT NOT NULL PRIMARY KEY,
    team_name       TEXT NOT NULL,
    parent_id       TEXT,
    team_level      INTEGER NOT NULL DEFAULT 1,
    manager_name    TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_team_parent_id ON dim_team (parent_id);

-- 16. 团队闭包表（存储层级关系）
DROP TABLE IF EXISTS team_closure;
CREATE TABLE team_closure (
    parent_id       TEXT NOT NULL,
    team_id         TEXT NOT NULL,
    distance        INTEGER DEFAULT 0,
    PRIMARY KEY (parent_id, team_id)
);
CREATE INDEX idx_team_closure_parent_id ON team_closure (parent_id);
CREATE INDEX idx_team_closure_team_id ON team_closure (team_id);

-- 17. 团队销售事实表
DROP TABLE IF EXISTS fact_team_sales;
CREATE TABLE fact_team_sales (
    sales_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    team_id         TEXT NOT NULL,
    date_key        INTEGER NOT NULL,
    sales_amount    REAL NOT NULL,
    sales_count     INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_fact_team_sales_team_id ON fact_team_sales (team_id);
CREATE INDEX idx_fact_team_sales_date_key ON fact_team_sales (date_key);

-- ============================================
-- 嵌套维度测试表 (Nested Dimension / Snowflake Schema)
-- ============================================

-- 18. 品类组维度表（三级维度）
DROP TABLE IF EXISTS dim_category_group;
CREATE TABLE dim_category_group (
    group_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id        TEXT NOT NULL UNIQUE,
    group_name      TEXT NOT NULL,
    group_type      TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);

-- 19. 品类维度表（二级维度，关联品类组）
DROP TABLE IF EXISTS dim_category_nested;
CREATE TABLE dim_category_nested (
    category_key    INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id     TEXT NOT NULL UNIQUE,
    category_name   TEXT NOT NULL,
    category_level  INTEGER DEFAULT 1,
    group_key       INTEGER,  -- 外键关联 dim_category_group
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_category_nested_group_key ON dim_category_nested (group_key);

-- 20. 嵌套产品维度表（一级维度，关联品类）
DROP TABLE IF EXISTS dim_product_nested;
CREATE TABLE dim_product_nested (
    product_key     INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id      TEXT NOT NULL UNIQUE,
    product_name    TEXT NOT NULL,
    brand           TEXT,
    category_key    INTEGER,  -- 外键关联 dim_category
    unit_price      REAL NOT NULL,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_product_nested_category_key ON dim_product_nested (category_key);

-- 21. 区域维度表（二级维度）
DROP TABLE IF EXISTS dim_region_nested;
CREATE TABLE dim_region_nested (
    region_key      INTEGER PRIMARY KEY AUTOINCREMENT,
    region_id       TEXT NOT NULL UNIQUE,
    region_name     TEXT NOT NULL,
    province        TEXT,
    city            TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);

-- 22. 嵌套门店维度表（一级维度，关联区域）
DROP TABLE IF EXISTS dim_store_nested;
CREATE TABLE dim_store_nested (
    store_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    store_id        TEXT NOT NULL UNIQUE,
    store_name      TEXT NOT NULL,
    store_type      TEXT,
    region_key      INTEGER,  -- 外键关联 dim_region
    status          TEXT DEFAULT 'ACTIVE',
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_dim_store_nested_region_key ON dim_store_nested (region_key);

-- 23. 嵌套维度销售事实表
DROP TABLE IF EXISTS fact_sales_nested;
CREATE TABLE fact_sales_nested (
    sales_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    date_key        INTEGER NOT NULL,
    product_key     INTEGER NOT NULL,  -- 关联 dim_product_nested
    store_key       INTEGER,           -- 关联 dim_store_nested
    quantity        INTEGER NOT NULL,
    sales_amount    REAL NOT NULL,
    cost_amount     REAL,
    created_at      TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX idx_fact_sales_nested_date_key ON fact_sales_nested (date_key);
CREATE INDEX idx_fact_sales_nested_product_key ON fact_sales_nested (product_key);
CREATE INDEX idx_fact_sales_nested_store_key ON fact_sales_nested (store_key);
