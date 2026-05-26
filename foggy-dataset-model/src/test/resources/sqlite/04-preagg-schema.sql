-- ============================================
-- Pre-aggregation Tables for SQLite Testing
-- File: 04-preagg-schema.sql
-- ============================================

-- 1. Daily Product Sales Pre-aggregation Table
-- Matches: FactSalesPreAggModel.daily_product_sales
DROP TABLE IF EXISTS preagg_daily_product_sales;
CREATE TABLE preagg_daily_product_sales (
    -- Dimension columns
    date_key            INTEGER NOT NULL,
    product_key         INTEGER NOT NULL,

    -- Date dimension properties (denormalized)
    full_date           TEXT,
    year                INTEGER,
    quarter             INTEGER,
    month               INTEGER,
    month_name          TEXT,

    -- Product dimension properties (denormalized)
    product_id          TEXT,
    product_name        TEXT,
    category_id         TEXT,
    category_name       TEXT,
    brand               TEXT,

    -- Measure columns (pre-aggregated)
    quantity_sum        INTEGER NOT NULL DEFAULT 0,
    sales_amount_sum    REAL NOT NULL DEFAULT 0,
    sales_amount_formula_yuan_sum REAL NOT NULL DEFAULT 0,
    cost_amount_sum     REAL DEFAULT 0,
    profit_amount_sum   REAL DEFAULT 0,

    -- Metadata
    _preagg_row_count   INTEGER DEFAULT 1,
    _preagg_updated_at  TEXT DEFAULT (datetime('now','localtime')),

    PRIMARY KEY (date_key, product_key)
);
CREATE INDEX idx_preagg_daily_product_sales_date ON preagg_daily_product_sales(date_key);
CREATE INDEX idx_preagg_daily_product_sales_product ON preagg_daily_product_sales(product_key);

-- 2. Monthly Category Sales Pre-aggregation Table
-- Matches: FactSalesPreAggModel.monthly_category_sales
DROP TABLE IF EXISTS preagg_monthly_category_sales;
CREATE TABLE preagg_monthly_category_sales (
    -- Dimension columns (month level)
    year_month          TEXT NOT NULL,  -- Format: YYYY-MM
    category_id         TEXT NOT NULL,

    -- Dimension properties (denormalized)
    category_name       TEXT,

    -- Measure columns (pre-aggregated)
    quantity_sum        INTEGER NOT NULL DEFAULT 0,
    sales_amount_sum    REAL NOT NULL DEFAULT 0,

    -- Metadata
    _preagg_row_count   INTEGER DEFAULT 1,
    _preagg_updated_at  TEXT DEFAULT (datetime('now','localtime')),

    PRIMARY KEY (year_month, category_id)
);

-- 3. Daily Customer Channel Sales Pre-aggregation Table
-- Matches: FactSalesPreAggModel.daily_customer_channel_sales
DROP TABLE IF EXISTS preagg_daily_customer_channel_sales;
CREATE TABLE preagg_daily_customer_channel_sales (
    -- Dimension columns
    date_key            INTEGER NOT NULL,
    customer_key        INTEGER NOT NULL,
    channel_key         INTEGER NOT NULL,

    -- Date dimension properties
    full_date           TEXT,

    -- Customer dimension properties (denormalized)
    customer_name       TEXT,
    customer_type       TEXT,
    province            TEXT,
    city                TEXT,

    -- Channel dimension properties
    channel_name        TEXT,
    channel_type        TEXT,

    -- Measure columns (pre-aggregated)
    quantity_sum        INTEGER NOT NULL DEFAULT 0,
    sales_amount_sum    REAL NOT NULL DEFAULT 0,

    -- Metadata
    _preagg_row_count   INTEGER DEFAULT 1,
    _preagg_updated_at  TEXT DEFAULT (datetime('now','localtime')),

    PRIMARY KEY (date_key, customer_key, channel_key)
);

-- 4. Daily Return Pre-aggregation Table
-- Matches: FactReturnPreAggModel.daily_return
DROP TABLE IF EXISTS preagg_daily_return;
CREATE TABLE preagg_daily_return (
    -- Dimension columns
    date_key            INTEGER NOT NULL,
    product_key         INTEGER NOT NULL,

    -- Date dimension properties
    full_date           TEXT,

    -- Product dimension properties (denormalized)
    product_name        TEXT,
    category_name       TEXT,

    -- Measure columns (pre-aggregated)
    return_quantity_sum INTEGER NOT NULL DEFAULT 0,
    return_amount_sum   REAL NOT NULL DEFAULT 0,

    -- Metadata
    _preagg_row_count   INTEGER DEFAULT 1,
    _preagg_updated_at  TEXT DEFAULT (datetime('now','localtime')),

    PRIMARY KEY (date_key, product_key)
);
