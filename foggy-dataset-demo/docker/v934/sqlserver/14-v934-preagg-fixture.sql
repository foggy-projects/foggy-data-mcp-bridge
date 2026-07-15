USE foggy_test;
GO

IF OBJECT_ID(N'dbo.v934_preagg_daily_product_sales', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_daily_product_sales;
IF OBJECT_ID(N'dbo.v934_preagg_fact_sales', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_fact_sales;
IF OBJECT_ID(N'dbo.v934_preagg_dim_product', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_dim_product;
IF OBJECT_ID(N'dbo.v934_preagg_dim_date', N'U') IS NOT NULL DROP TABLE dbo.v934_preagg_dim_date;
GO

CREATE TABLE dbo.v934_preagg_dim_date (
    date_key int NOT NULL PRIMARY KEY,
    full_date date NOT NULL
);

CREATE TABLE dbo.v934_preagg_dim_product (
    product_key int NOT NULL PRIMARY KEY,
    product_name varchar(100) NOT NULL,
    category_name varchar(100) NOT NULL
);

CREATE TABLE dbo.v934_preagg_fact_sales (
    sales_key bigint NOT NULL PRIMARY KEY,
    date_key int NOT NULL,
    product_key int NOT NULL,
    sales_amount decimal(20,4) NOT NULL
);

CREATE TABLE dbo.v934_preagg_daily_product_sales (
    date_key int NOT NULL,
    product_key int NOT NULL,
    category_name varchar(100) NOT NULL,
    sales_amount_sum decimal(20,4) NOT NULL,
    CONSTRAINT PK_v934_preagg_daily_product_sales PRIMARY KEY (date_key, product_key)
);
GO

INSERT INTO dbo.v934_preagg_dim_date (date_key, full_date) VALUES
    (20930101, '2093-01-01'),
    (20930102, '2093-01-02');

INSERT INTO dbo.v934_preagg_dim_product (product_key, product_name, category_name) VALUES
    (934001, 'V934 Product A1', 'V934_ALPHA'),
    (934002, 'V934 Product B1', 'V934_BETA'),
    (934003, 'V934 Product G1', 'V934_GAMMA');

INSERT INTO dbo.v934_preagg_fact_sales (sales_key, date_key, product_key, sales_amount) VALUES
    (93400101, 20930101, 934001, 30.0000),
    (93400102, 20930102, 934001, 20.0000),
    (93400201, 20930101, 934002, 40.0000),
    (93400301, 20930101, 934003, 10.0000);

INSERT INTO dbo.v934_preagg_daily_product_sales
    (date_key, product_key, category_name, sales_amount_sum) VALUES
    (20930101, 934001, 'V934_ALPHA', 30.0000),
    (20930102, 934001, 'V934_ALPHA', 20.0000),
    (20930101, 934002, 'V934_BETA', 40.0000),
    (20930101, 934003, 'V934_GAMMA', 10.0000);
GO
