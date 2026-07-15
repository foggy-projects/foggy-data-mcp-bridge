-- Deterministic rows owned by the 9.3.4 five-database QueryFacade parity lane.
USE foggy_test;
GO

MERGE dbo.fact_sales AS target
USING (VALUES
    ('V934_PARITY_SENTINEL', 1, 20240101, 1, 1, 1, 1, 1, 1, 10.00,
     6.00, 0.00, 10.00, 6.00, 4.00, 0.00, 'COMPLETED', 'V934'),
    ('V934_PARITY_SENTINEL', 2, 20240101, 1, 1, 1, 1, 1, 2, 20.00,
     6.00, 0.00, 40.00, 12.00, 28.00, 0.00, 'COMPLETED', 'V934')
) AS source (
    order_id, order_line_no, date_key, product_key, customer_key,
    store_key, channel_key, promotion_key, quantity, unit_price,
    unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
    tax_amount, order_status, payment_method
)
ON target.order_id = source.order_id AND target.order_line_no = source.order_line_no
WHEN MATCHED THEN UPDATE SET
    date_key = source.date_key,
    product_key = source.product_key,
    customer_key = source.customer_key,
    store_key = source.store_key,
    channel_key = source.channel_key,
    promotion_key = source.promotion_key,
    quantity = source.quantity,
    unit_price = source.unit_price,
    unit_cost = source.unit_cost,
    discount_amount = source.discount_amount,
    sales_amount = source.sales_amount,
    cost_amount = source.cost_amount,
    profit_amount = source.profit_amount,
    tax_amount = source.tax_amount,
    order_status = source.order_status,
    payment_method = source.payment_method
WHEN NOT MATCHED THEN INSERT (
    order_id, order_line_no, date_key, product_key, customer_key,
    store_key, channel_key, promotion_key, quantity, unit_price,
    unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
    tax_amount, order_status, payment_method
) VALUES (
    source.order_id, source.order_line_no, source.date_key, source.product_key,
    source.customer_key, source.store_key, source.channel_key, source.promotion_key,
    source.quantity, source.unit_price, source.unit_cost, source.discount_amount,
    source.sales_amount, source.cost_amount, source.profit_amount, source.tax_amount,
    source.order_status, source.payment_method
);
GO
