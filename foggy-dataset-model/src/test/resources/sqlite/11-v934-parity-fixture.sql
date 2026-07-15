-- Deterministic rows owned by the 9.3.4 five-database QueryFacade parity lane.
INSERT INTO fact_sales (
    order_id, order_line_no, date_key, product_key, customer_key,
    store_key, channel_key, promotion_key, quantity, unit_price,
    unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
    tax_amount, order_status, payment_method
) VALUES
    ('V934_PARITY_SENTINEL', 1, 20240101, 1, 1, 1, 1, 1, 1, 10.00,
     6.00, 0.00, 10.00, 6.00, 4.00, 0.00, 'COMPLETED', 'V934'),
    ('V934_PARITY_SENTINEL', 2, 20240101, 1, 1, 1, 1, 1, 2, 20.00,
     6.00, 0.00, 40.00, 12.00, 28.00, 0.00, 'COMPLETED', 'V934')
ON CONFLICT (order_id, order_line_no) DO UPDATE SET
    date_key = excluded.date_key,
    product_key = excluded.product_key,
    customer_key = excluded.customer_key,
    store_key = excluded.store_key,
    channel_key = excluded.channel_key,
    promotion_key = excluded.promotion_key,
    quantity = excluded.quantity,
    unit_price = excluded.unit_price,
    unit_cost = excluded.unit_cost,
    discount_amount = excluded.discount_amount,
    sales_amount = excluded.sales_amount,
    cost_amount = excluded.cost_amount,
    profit_amount = excluded.profit_amount,
    tax_amount = excluded.tax_amount,
    order_status = excluded.order_status,
    payment_method = excluded.payment_method;
