-- Deterministic rows owned by the 9.3.4 five-database QueryFacade parity lane.
INSERT INTO public.fact_sales (
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
    date_key = EXCLUDED.date_key,
    product_key = EXCLUDED.product_key,
    customer_key = EXCLUDED.customer_key,
    store_key = EXCLUDED.store_key,
    channel_key = EXCLUDED.channel_key,
    promotion_key = EXCLUDED.promotion_key,
    quantity = EXCLUDED.quantity,
    unit_price = EXCLUDED.unit_price,
    unit_cost = EXCLUDED.unit_cost,
    discount_amount = EXCLUDED.discount_amount,
    sales_amount = EXCLUDED.sales_amount,
    cost_amount = EXCLUDED.cost_amount,
    profit_amount = EXCLUDED.profit_amount,
    tax_amount = EXCLUDED.tax_amount,
    order_status = EXCLUDED.order_status,
    payment_method = EXCLUDED.payment_method;
