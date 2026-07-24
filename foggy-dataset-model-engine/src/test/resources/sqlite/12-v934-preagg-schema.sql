DROP TABLE IF EXISTS v934_preagg_daily_product_sales;
DROP TABLE IF EXISTS v934_preagg_fact_sales;
DROP TABLE IF EXISTS v934_preagg_dim_product;
DROP TABLE IF EXISTS v934_preagg_dim_date;

CREATE TABLE v934_preagg_dim_date (
    date_key INTEGER NOT NULL PRIMARY KEY,
    full_date TEXT NOT NULL
);

CREATE TABLE v934_preagg_dim_product (
    product_key INTEGER NOT NULL PRIMARY KEY,
    product_name TEXT NOT NULL,
    category_name TEXT NOT NULL
);

CREATE TABLE v934_preagg_fact_sales (
    sales_key INTEGER NOT NULL PRIMARY KEY,
    date_key INTEGER NOT NULL,
    product_key INTEGER NOT NULL,
    sales_amount REAL NOT NULL
);

CREATE TABLE v934_preagg_daily_product_sales (
    date_key INTEGER NOT NULL,
    product_key INTEGER NOT NULL,
    category_name TEXT NOT NULL,
    sales_amount_sum REAL NOT NULL,
    PRIMARY KEY (date_key, product_key)
);
