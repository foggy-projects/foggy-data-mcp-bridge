DROP TABLE IF EXISTS analytics_sales;

CREATE TABLE analytics_sales (
    sale_id INTEGER PRIMARY KEY,
    region TEXT NOT NULL,
    amount DECIMAL(18, 2) NOT NULL
);
