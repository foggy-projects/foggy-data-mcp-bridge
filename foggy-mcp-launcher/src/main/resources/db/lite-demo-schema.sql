DROP TABLE IF EXISTS fact_order;
DROP TABLE IF EXISTS customer_order_lifecycle;
DROP TABLE IF EXISTS crm_lead;
DROP TABLE IF EXISTS dim_promotion;
DROP TABLE IF EXISTS dim_channel;
DROP TABLE IF EXISTS dim_store;
DROP TABLE IF EXISTS dim_customer;
DROP TABLE IF EXISTS dim_date;

CREATE TABLE dim_date
(
    date_key       INTEGER NOT NULL PRIMARY KEY,
    full_date      TEXT    NOT NULL,
    year           INTEGER NOT NULL,
    quarter        INTEGER NOT NULL,
    month          INTEGER NOT NULL,
    month_name     TEXT    NOT NULL,
    week_of_year   INTEGER NOT NULL,
    day_of_month   INTEGER NOT NULL,
    day_of_week    INTEGER NOT NULL,
    day_name       TEXT    NOT NULL,
    is_weekend     INTEGER NOT NULL DEFAULT 0,
    is_holiday     INTEGER NOT NULL DEFAULT 0,
    fiscal_year    INTEGER,
    fiscal_quarter INTEGER
);

CREATE TABLE dim_customer
(
    customer_key  INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id   TEXT NOT NULL UNIQUE,
    customer_name TEXT NOT NULL,
    customer_type TEXT,
    gender        TEXT,
    age_group     TEXT,
    province      TEXT,
    city          TEXT,
    id_card       TEXT,
    phone         TEXT,
    district      TEXT,
    register_date TEXT,
    member_level  TEXT,
    status        TEXT DEFAULT 'ACTIVE',
    created_at    TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE dim_store
(
    store_key    INTEGER PRIMARY KEY AUTOINCREMENT,
    store_id     TEXT NOT NULL UNIQUE,
    store_name   TEXT NOT NULL,
    store_type   TEXT,
    province     TEXT,
    city         TEXT,
    district     TEXT,
    address      TEXT,
    manager_name TEXT,
    open_date    TEXT,
    area_sqm     REAL,
    status       TEXT DEFAULT 'ACTIVE',
    created_at   TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE dim_channel
(
    channel_key  INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id   TEXT NOT NULL UNIQUE,
    channel_name TEXT NOT NULL,
    channel_type TEXT,
    platform     TEXT,
    status       TEXT DEFAULT 'ACTIVE',
    created_at   TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE dim_promotion
(
    promotion_key  INTEGER PRIMARY KEY AUTOINCREMENT,
    promotion_id   TEXT NOT NULL UNIQUE,
    promotion_name TEXT NOT NULL,
    promotion_type TEXT,
    discount_rate  REAL,
    start_date     TEXT,
    end_date       TEXT,
    status         TEXT DEFAULT 'ACTIVE',
    created_at     TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE fact_order
(
    order_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id        TEXT    NOT NULL UNIQUE,
    date_key        INTEGER NOT NULL,
    customer_key    INTEGER,
    store_key       INTEGER,
    channel_key     INTEGER,
    promotion_key   INTEGER,
    total_quantity  INTEGER NOT NULL,
    total_amount    REAL    NOT NULL,
    discount_amount REAL DEFAULT 0,
    freight_amount  REAL DEFAULT 0,
    pay_amount      REAL    NOT NULL,
    order_status    TEXT    NOT NULL,
    payment_status  TEXT,
    order_time      TEXT    NOT NULL,
    created_at      TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX idx_lite_fact_order_date_key ON fact_order (date_key);
CREATE INDEX idx_lite_fact_order_customer_key ON fact_order (customer_key);
CREATE INDEX idx_lite_fact_order_order_status ON fact_order (order_status);
CREATE INDEX idx_lite_fact_order_order_time ON fact_order (order_time);

CREATE TABLE crm_lead
(
    lead_id                  TEXT NOT NULL PRIMARY KEY,
    created_at               TEXT NOT NULL,
    lead_source              TEXT NOT NULL,
    converted_opportunity_id TEXT,
    converted_order_id       TEXT
);

CREATE INDEX idx_lite_crm_lead_created_at ON crm_lead (created_at);
CREATE INDEX idx_lite_crm_lead_source ON crm_lead (lead_source);
CREATE INDEX idx_lite_crm_lead_converted_order ON crm_lead (converted_order_id);

CREATE TABLE customer_order_lifecycle
(
    customer_id           TEXT NOT NULL PRIMARY KEY,
    customer_name         TEXT NOT NULL,
    first_order_date      TEXT NOT NULL,
    first_order_month     TEXT NOT NULL,
    first_order_channel   TEXT,
    order_count           INTEGER NOT NULL,
    lifetime_amount       REAL NOT NULL,
    repurchase_30d_flag   INTEGER NOT NULL DEFAULT 0,
    repurchase_60d_flag   INTEGER NOT NULL DEFAULT 0,
    repurchase_90d_flag   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_lite_customer_lifecycle_first_order_date ON customer_order_lifecycle (first_order_date);
CREATE INDEX idx_lite_customer_lifecycle_first_order_month ON customer_order_lifecycle (first_order_month);
