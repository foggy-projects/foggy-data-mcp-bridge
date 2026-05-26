-- ============================================
-- Pre-aggregation Test Data for SQLite
-- File: 05-preagg-data.sql
-- ============================================
-- This file contains CORRECTLY aggregated data from fact_sales
-- Used for validating pre-aggregation correctness

-- 1. Populate preagg_daily_product_sales
-- Aggregated from fact_sales by (date_key, product_key)
INSERT INTO preagg_daily_product_sales
    (date_key, product_key,
     full_date, year, quarter, month, month_name,
     product_id, product_name, category_id, category_name, brand,
     quantity_sum, sales_amount_sum, sales_amount_formula_yuan_sum,
     cost_amount_sum, profit_amount_sum, _preagg_row_count)
SELECT
    fs.date_key,
    fs.product_key,
    d.full_date,
    d.year,
    d.quarter,
    d.month,
    d.month_name,
    p.product_id,
    p.product_name,
    p.category_id,
    p.category_name,
    p.brand,
    SUM(fs.quantity) as quantity_sum,
    SUM(fs.sales_amount) as sales_amount_sum,
    SUM((fs.sales_amount + 0) / 100.0) as sales_amount_formula_yuan_sum,
    SUM(COALESCE(fs.cost_amount, 0)) as cost_amount_sum,
    SUM(COALESCE(fs.profit_amount, 0)) as profit_amount_sum,
    COUNT(*) as _preagg_row_count
FROM fact_sales fs
LEFT JOIN dim_date d ON fs.date_key = d.date_key
LEFT JOIN dim_product p ON fs.product_key = p.product_key
GROUP BY fs.date_key, fs.product_key,
         d.full_date, d.year, d.quarter, d.month, d.month_name,
         p.product_id, p.product_name, p.category_id, p.category_name, p.brand;

-- 2. Populate preagg_monthly_category_sales
-- Aggregated from fact_sales by (year-month, category_id)
INSERT INTO preagg_monthly_category_sales
    (year_month, category_id, category_name, quantity_sum, sales_amount_sum, _preagg_row_count)
SELECT
    substr(d.full_date, 1, 7) as year_month,
    p.category_id,
    p.category_name,
    SUM(fs.quantity) as quantity_sum,
    SUM(fs.sales_amount) as sales_amount_sum,
    COUNT(*) as _preagg_row_count
FROM fact_sales fs
LEFT JOIN dim_product p ON fs.product_key = p.product_key
LEFT JOIN dim_date d ON fs.date_key = d.date_key
GROUP BY substr(d.full_date, 1, 7), p.category_id, p.category_name;

-- 3. Populate preagg_daily_customer_channel_sales
-- Aggregated from fact_sales by (date_key, customer_key, channel_key)
INSERT INTO preagg_daily_customer_channel_sales
    (date_key, customer_key, channel_key,
     full_date,
     customer_name, customer_type, province, city,
     channel_name, channel_type,
     quantity_sum, sales_amount_sum, _preagg_row_count)
SELECT
    fs.date_key,
    fs.customer_key,
    fs.channel_key,
    d.full_date,
    c.customer_name,
    c.customer_type,
    c.province,
    c.city,
    ch.channel_name,
    ch.channel_type,
    SUM(fs.quantity) as quantity_sum,
    SUM(fs.sales_amount) as sales_amount_sum,
    COUNT(*) as _preagg_row_count
FROM fact_sales fs
LEFT JOIN dim_date d ON fs.date_key = d.date_key
LEFT JOIN dim_customer c ON fs.customer_key = c.customer_key
LEFT JOIN dim_channel ch ON fs.channel_key = ch.channel_key
WHERE fs.customer_key IS NOT NULL AND fs.channel_key IS NOT NULL
GROUP BY fs.date_key, fs.customer_key, fs.channel_key,
         d.full_date,
         c.customer_name, c.customer_type, c.province, c.city,
         ch.channel_name, ch.channel_type;

-- 4. Populate preagg_daily_return
-- Aggregated from fact_return by (date_key, product_key)
INSERT INTO preagg_daily_return
    (date_key, product_key, full_date, product_name, category_name,
     return_quantity_sum, return_amount_sum, _preagg_row_count)
SELECT
    fr.date_key,
    fr.product_key,
    d.full_date,
    p.product_name,
    p.category_name,
    SUM(fr.return_quantity) as return_quantity_sum,
    SUM(fr.return_amount) as return_amount_sum,
    COUNT(*) as _preagg_row_count
FROM fact_return fr
LEFT JOIN dim_date d ON fr.date_key = d.date_key
LEFT JOIN dim_product p ON fr.product_key = p.product_key
WHERE fr.product_key IS NOT NULL
GROUP BY fr.date_key, fr.product_key, d.full_date, p.product_name, p.category_name;
